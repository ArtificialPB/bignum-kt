---
name: create-release
description: Release workflow for bignum-kt. Use when we need to bump VERSION_NAME, create a release commit, tag a v* release, start the next SNAPSHOT version, push the release refs, or inspect GitHub Actions publishing for this repository. Require the user to provide an explicit release version before making release changes.
---

# Create Release

## Overview

Use this workflow to publish a new bignum-kt version through GitHub Actions. The release is driven by `gradle.properties`, a lightweight `v*` tag, and the CI workflow in `.github/workflows/publish-library.yml`.

## CI Facts

- Publishing runs from `.github/workflows/publish-library.yml`.
- The workflow triggers on pushes to `master`, pushes to tags matching `v*`, and manual dispatch.
- The deploy job runs `./gradlew clean build`, `./gradlew publish`, then `./gradlew jreleaserDeploy`.
- The tag workflow should publish the fixed release version.
- The branch workflow should normally publish the next `-SNAPSHOT` version.
- Previous releases used lightweight tags, for example `v1.0.0`.

## Release Workflow

1. Require an explicit release version from the user before changing files, creating commits, creating tags, or pushing refs. Do not infer the release version from `VERSION_NAME`, tags, branch history, or a requested bump type.

   Acceptable examples: `1.0.1`, `v1.0.1`.

   If the user has not provided a concrete release version, ask for it and stop.

2. Inspect state before changing anything:
   ```bash
   git status --short --branch
   git log --oneline --decorate -8
   git tag --list --sort=-version:refname
   sed -n '1,80p' gradle.properties
   sed -n '1,220p' .github/workflows/publish-library.yml
   ```

3. Normalize the explicit release version by removing a leading `v` for `VERSION_NAME`, while keeping `v` for the tag. For example, user input `v1.0.1` becomes `VERSION_NAME=1.0.1` and tag `v1.0.1`.

4. Confirm the next snapshot version, usually the next patch such as `1.0.2-SNAPSHOT`. The next snapshot can be inferred from the explicit release version unless the user gives a different next snapshot.

5. Set the release version in `gradle.properties` and update stable dependency examples in `README.md` to the same version:
   ```properties
   VERSION_NAME=1.0.1
   ```

   ```kotlin
   implementation("io.github.artificialpb:bignum:1.0.1")
   ```

6. Commit only `gradle.properties` and `README.md`:
   ```bash
   git add gradle.properties README.md
   git commit -m "chore: release 1.0.1"
   ```

7. Create a lightweight tag on the release commit:
   ```bash
   git tag v1.0.1
   ```

8. Set the next snapshot version in `gradle.properties` and update the snapshot dependency example in `README.md`:
   ```properties
   VERSION_NAME=1.0.2-SNAPSHOT
   ```

   ```kotlin
   implementation("io.github.artificialpb:bignum:1.0.2-SNAPSHOT")
   ```

9. Commit only `gradle.properties` and `README.md` again:
   ```bash
   git add gradle.properties README.md
   git commit -m "chore: start 1.0.2 snapshot"
   ```

10. Verify local history before pushing:
   ```bash
   git log --oneline --decorate -5
   git status --short --branch
   git show --quiet --format='%h %D %s' v1.0.1
   ```

11. Push the branch and release tag together:
   ```bash
   git push origin master v1.0.1
   ```

   This ordering matters: `master` should point at the next snapshot commit by the time GitHub sees the branch push, while the `v1.0.1` tag points at the release commit.

12. Verify the remote tag and GitHub Actions:
    ```bash
    git ls-remote --tags origin v1.0.1
    gh run list --repo ArtificialPB/bignum-kt --limit 8
    ```

    Expect one deploy run for `v1.0.1`, one deploy run for `master` at the next snapshot commit, and pull request checks for `master`.

## Guardrails

- Do not include unrelated worktree files in the release commits.
- Keep stable and snapshot dependency examples in `README.md` aligned with the release and next snapshot versions.
- Do not push the release commit to `master` by itself unless the user explicitly wants the branch workflow to publish a non-snapshot version.
- Never infer the release version. The user must provide it explicitly.
- If `git tag`, `git push`, `git ls-remote`, or `gh run list` needs elevated/network permissions, request approval with the narrow command prefix.
- If CI should be monitored, use `gh run list` first, then inspect failing runs/jobs only when needed.
