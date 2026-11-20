# Updating the existing Windows checkout

This project already has a Git history and a stable tag `v0.5.3.27`. Update that checkout in place. **Do not delete `.git`, reinitialize Git, force-push, or replace the repository with a fresh import.**

1. Close Android Studio or ensure no build is running.
2. In PowerShell, inspect the current checkout:

   ```powershell
   Set-Location D:\FT-10C_SYNC
   git status --short --branch
   git branch --show-current
   git log -1 --oneline --decorate
   git tag --list v0.5.3.27
   ```

3. Confirm the branch is `main` and the working tree is clean. If it is not clean, stop and review/commit or stash the user's own changes before proceeding.
4. Fetch the existing remote history:

   ```powershell
   git fetch origin --tags
   git switch main
   git pull --ff-only origin main
   ```

5. Extract the contents of the supplied update ZIP directly into `D:\FT-10C_SYNC` and allow the release files to overwrite matching project files. The ZIP intentionally does not contain a `.git` directory. Do not extract it into a new nested `JetSport_FT10C_Sync` subfolder.
6. Review the changes before staging:

   ```powershell
   git status --short
   git diff --stat
   git diff -- app/build.gradle.kts README.md docs\ARCHITECTURE.md docs\FT10C_PROTOCOL.md
   ```

7. Ensure `local.properties`, `*.jks`, `*.keystore`, `app\build`, `.gradle`, exported logs, and generated APKs are not staged. The release source package is source-only.
8. Open `D:\FT-10C_SYNC` in Android Studio, select JDK 17 for Gradle, then run:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   .\gradlew.bat :app:testDebugUnitTest
   ```

9. Install and test the debug APK on the phone/bracelet. Only after the build, tests, and smoke test pass, create the release commit and tag:

   ```powershell
   git add -A
   git diff --cached --stat
   git diff --cached --check
   git commit -m "Release v1.0.0"
   git tag -a v1.0.0 -m "JetSport FT-10C Sync v1.0.0"
   git push origin main
   git push origin v1.0.0
   ```

10. Create the GitHub Release from tag `v1.0.0` and paste the contents of `RELEASE_NOTES_1.0.0.md`. Do not delete or move the historical `v0.5.3.27` tag.

If `git pull --ff-only` reports divergence or the branch is not clean, stop and resolve that state before copying files. Do not use `reset --hard`, `push --force`, or delete tags as a shortcut.
