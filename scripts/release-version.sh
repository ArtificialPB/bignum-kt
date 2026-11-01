#!/usr/bin/env bash
# Rewrites the version references that change during a release.
#
#   scripts/release-version.sh release <version> <xcframework-checksum>
#       Sets VERSION_NAME, the stable README dependency examples, and the Package.swift binary release.
#   scripts/release-version.sh snapshot <version>-SNAPSHOT
#       Sets VERSION_NAME and the snapshot README dependency example.
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
    echo "usage: $0 release <version> <checksum> | snapshot <version>-SNAPSHOT" >&2
    exit 64
}

# replace <file> <perl substitution> <text expected afterwards>
replace() {
    local file=$1 expression=$2 expected=$3
    perl -pi -e "$expression" "$file"
    if ! grep -qF -- "$expected" "$file"; then
        echo "error: expected '$expected' in $file after update" >&2
        exit 1
    fi
}

mode=${1:-}
case "$mode" in
    release)
        [[ $# -eq 3 ]] || usage
        version=$2 checksum=$3
        [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "error: invalid release version '$version'" >&2; exit 1; }
        [[ $checksum =~ ^[0-9a-f]{64}$ ]] || { echo "error: invalid checksum '$checksum'" >&2; exit 1; }
        export VERSION=$version CHECKSUM=$checksum

        replace gradle.properties 's/^VERSION_NAME=.*/VERSION_NAME=$ENV{VERSION}/' "VERSION_NAME=$version"
        replace README.md 's/(io\.github\.artificialpb:bignum:)\d+\.\d+\.\d+(?=")/$1$ENV{VERSION}/g' \
            "io.github.artificialpb:bignum:$version\""
        replace README.md 's/(\.package\(url: "https:\/\/github\.com\/ArtificialPB\/bignum-kt\.git", from: ")[^"]+/$1$ENV{VERSION}/' \
            "from: \"$version\""
        replace Package.swift 's/^let releaseVersion = ".*"/let releaseVersion = "$ENV{VERSION}"/' \
            "let releaseVersion = \"$version\""
        replace Package.swift 's/^let releaseChecksum = ".*"/let releaseChecksum = "$ENV{CHECKSUM}"/' \
            "let releaseChecksum = \"$checksum\""
        ;;
    snapshot)
        [[ $# -eq 2 ]] || usage
        version=$2
        [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]] || { echo "error: invalid snapshot version '$version'" >&2; exit 1; }
        export VERSION=$version

        replace gradle.properties 's/^VERSION_NAME=.*/VERSION_NAME=$ENV{VERSION}/' "VERSION_NAME=$version"
        replace README.md 's/(io\.github\.artificialpb:bignum:)\d+\.\d+\.\d+-SNAPSHOT/$1$ENV{VERSION}/g' \
            "io.github.artificialpb:bignum:$version\""
        ;;
    *)
        usage
        ;;
esac
