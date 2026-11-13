#!/usr/bin/env bash
# Verifies that no object in the BignumKt XCFramework requires a newer OS than the minimum
# platforms declared in Package.swift, so Swift and KMP consumers don't link objects built for
# a newer deployment target than the package promises.
#
#   scripts/check-xcframework-min-os.sh [path/to/BignumKt.xcframework]
#
# Set OTOOL / LIPO to use different tools (e.g. llvm-otool / llvm-lipo).
set -euo pipefail
shopt -s nullglob

cd "$(dirname "$0")/.."

xcframework=${1:-bignum/build/XCFrameworks/release/BignumKt.xcframework}
otool=${OTOOL:-otool}
lipo=${LIPO:-lipo}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# package_minimum <iOS|macOS> -> e.g. 15.0, read from `.iOS(.v15)` / `.macOS(.v12)` in Package.swift
package_minimum() {
    local version
    version=$(perl -ne 'print "$1\n" if /\.\Q'"$1"'\E\(\.v([0-9_]+)\)/' Package.swift | head -n 1)
    if [[ -z $version ]]; then
        echo "error: no .$1 platform in Package.swift" >&2
        exit 1
    fi
    version=${version//_/.}
    [[ $version == *.* ]] || version="$version.0"
    echo "$version"
}

# Prints "<minimum OS> <arch or slice> <member>" for every object in a thin static archive or binary.
thin_object_minimums() {
    "$otool" -l "$1" | awk -v arch="$2" '
        /^[^ \t].*:$/ { member = $0; sub(/:$/, "", member); sub(/^.*\//, "", member) }
        $1 == "cmd" { cmd = $2 }
        $1 == "minos" && cmd == "LC_BUILD_VERSION" { print $2, arch, member }
        $1 == "version" && cmd ~ /^LC_VERSION_MIN_/ { print $2, arch, member }
    '
}

# Same for a possibly fat file; each architecture is extracted and inspected on its own.
object_minimums() {
    local archs arch
    # Fat (universal) files start with the 0xcafebabe magic; everything else is inspected as-is.
    if [[ $(od -An -tx1 -N4 "$1" | tr -d ' \n') != cafebabe ]]; then
        thin_object_minimums "$1" "$(basename "$(dirname "$(dirname "$1")")")"
        return
    fi
    read -r -a archs <<<"$("$lipo" -archs "$1")"
    for arch in "${archs[@]}"; do
        mkdir -p "$work/$arch"
        "$lipo" "$1" -thin "$arch" -output "$work/$arch/$(basename "$1")"
        thin_object_minimums "$work/$arch/$(basename "$1")" "$arch"
    done
}

version_greater() {
    [[ $1 != "$2" && $(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1) == "$1" ]]
}

ios_minimum=$(package_minimum iOS)
macos_minimum=$(package_minimum macOS)
failed=0
slices=0

for framework in "$xcframework"/*/BignumKt.framework; do
    slice=$(basename "$(dirname "$framework")")
    case "$slice" in
        ios-*) platform=iOS expected=$ios_minimum ;;
        macos-*) platform=macOS expected=$macos_minimum ;;
        *) echo "error: unexpected XCFramework slice $slice" >&2; exit 1 ;;
    esac

    minimums=$(object_minimums "$framework/BignumKt")
    if [[ -z $minimums ]]; then
        echo "error: $slice: no minimum OS load commands found" >&2
        failed=1
        continue
    fi

    newest=$(cut -d' ' -f1 <<<"$minimums" | sort -V | tail -n 1)
    offenders=$(while read -r version arch member; do
        if version_greater "$version" "$expected"; then echo "  $member ($arch) requires $platform $version"; fi
    done <<<"$minimums")

    if [[ -n $offenders ]]; then
        echo "error: $slice: objects require a newer $platform than Package.swift's $expected:" >&2
        sort -u <<<"$offenders" | head -n 20 >&2
        failed=1
    else
        echo "$slice: $(wc -l <<<"$minimums" | tr -d ' ') objects, newest minimum $platform $newest (package: $expected)"
    fi
    slices=$((slices + 1))
done

if [[ $slices -eq 0 ]]; then
    echo "error: no BignumKt.framework slices found in $xcframework" >&2
    exit 1
fi
exit "$failed"
