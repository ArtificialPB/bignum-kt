---
name: create-release
description: Release workflow for bignum-kt. Use when we need to cut a release (Maven Central + Swift package XCFramework), bump VERSION_NAME, tag a v* release, start the next SNAPSHOT version, or inspect GitHub Actions publishing for this repository. Require the user to provide an explicit release version before making release changes.
---

# Create Release

## Overview

Releases are cut entirely in GitHub Actions by `.github/workflows/release.yml` (manual `workflow_dispatch`). Do not create release commits or tags locally: the Swift package requires the XCFramework checksum in `Package.swift` of the tagged commit, and only the workflow computes it.

## CI Facts

- `release.yml` takes `version` (required) and `next-snapshot` (optional, defaults to the next patch `-SNAPSHOT`).
- It runs `./gradlew build :bignum:assembleBignumKtReleaseXCFramework`, zips the XCFramework, runs `swift test` against it, then commits `chore: release X.Y.Z` (via `scripts/release-version.sh release`), tags `vX.Y.Z`, and commits `chore: start X.Y.(Z+1) snapshot`.
- `BignumKt.xcframework.zip` is uploaded to a draft GitHub release before `master` and the tag are pushed atomically; the release is then published and the downloaded asset checksum is verified.
- It then calls `publish-library.yml` twice with `run-build: false` (the release job already built and tested): once for the tag (release to Maven Central), then once for the snapshot commit.
- `publish-library.yml` also runs on every push to `master` (snapshots) and on manual dispatch. It no longer triggers on tag pushes.
- Previous releases used lightweight tags, for example `v1.0.0`.

## Release Workflow

1. Require an explicit release version from the user before dispatching anything. Do not infer the release version from `VERSION_NAME`, tags, branch history, or a requested bump type.

   Acceptable examples: `1.0.1`, `v1.0.1` (the workflow strips a leading `v`).

   If the user has not provided a concrete release version, ask for it and stop.

2. Inspect state before releasing:
   ```bash
   git fetch origin --tags
   git log --oneline --decorate -8 origin/master
   git tag --list --sort=-version:refname | head -5
   git show origin/master:gradle.properties | grep VERSION_NAME
   gh run list --repo ArtificialPB/bignum-kt --branch master --limit 5
   ```

   `origin/master` must be on a `-SNAPSHOT` version, the release tag must not exist yet, and the latest pull request checks on `master` should be green. Any changes meant for the release must already be merged to `master`.

3. Confirm the next snapshot version, usually the next patch such as `1.0.2-SNAPSHOT`. Only pass `next-snapshot` when the user wants something other than the next patch.

4. Dispatch the release:
   ```bash
   gh workflow run release.yml --repo ArtificialPB/bignum-kt --ref master -f version=1.0.1
   # or, with an explicit next snapshot:
   gh workflow run release.yml --repo ArtificialPB/bignum-kt --ref master -f version=1.0.1 -f next-snapshot=1.1.0-SNAPSHOT
   ```

5. Monitor the run:
   ```bash
   gh run list --repo ArtificialPB/bignum-kt --workflow release.yml --limit 3
   gh run watch <run-id> --repo ArtificialPB/bignum-kt
   ```

6. Verify the result:
   ```bash
   git fetch origin --tags
   git show --quiet --format='%h %D %s' v1.0.1
   git show v1.0.1:Package.swift | grep -E 'releaseVersion|releaseChecksum'
   gh release view v1.0.1 --repo ArtificialPB/bignum-kt
   git log --oneline --decorate -3 origin/master
   ```

   Expect the `v1.0.1` tag on `chore: release 1.0.1`, a published GitHub release with `BignumKt.xcframework.zip`, `origin/master` on the next snapshot commit, and successful `Publish release` and `Publish next snapshot` jobs.

## Failure Recovery

- Failure before the push step: nothing was pushed (a draft release, if created, is deleted). Fix the cause on `master` and dispatch again.
- Failure after the push step (publishing the GitHub release, checksum verification, or Maven Central jobs): the tag and commits exist, so do not dispatch again. Inspect the failing job. The exact zip is kept as the `BignumKt.xcframework` workflow artifact; re-attach that file (never a rebuilt one, whose checksum differs) with `gh release upload v1.0.1 BignumKt.xcframework.zip`, or re-run only the failed Maven jobs from the run page, or dispatch `publish-library.yml` with `--ref v1.0.1`.

## Guardrails

- Never infer the release version. The user must provide it explicitly.
- Never create or push `v*` tags by hand; the tag would lack a valid `Package.swift` checksum and would not publish to Maven Central.
- If `gh workflow run`, `gh run`, or `git fetch` needs elevated/network permissions, request approval with the narrow command prefix.
- If CI should be monitored, use `gh run list` first, then inspect failing runs/jobs only when needed.
