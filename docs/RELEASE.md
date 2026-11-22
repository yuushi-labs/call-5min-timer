# リリース手順（v0.1.0 以降）

1. `android/app/build.gradle.kts` の `versionCode` / `versionName` を上げる
2. テスト・Lint・署名付き APK を作る

   ```bash
   cd android
   ./gradlew testDebugUnitTest lintDebug assembleRelease
   /path/to/build-tools/34.0.0/apksigner verify --print-certs \
     app/build/outputs/apk/release/app-release.apk
   ```

3. APK を `call-5min-timer-v<version>.apk` にリネームして GitHub Release に添付

   ```bash
   gh api -X POST /repos/yuushi-labs/call-5min-timer/releases \
     -f tag_name=v0.1.0 \
     -f name="v0.1.0" \
     -f body="$(cat release-notes.md)" \
     -F draft=false -F prerelease=false
   gh release upload v0.1.0 call-5min-timer-v0.1.0.apk
   ```

4. リリース本文に `versionCode: <n>` を書く。アプリ内アップデートはタグと本文の
   `versionCode` を見て「今より新しいか」を判断する（同じタグでも versionCode が
   上がっていれば更新とみなす）。

## 注意

- 署名鍵（`android/keystore/call5min-timer.jks`）は変えない。変えると上書き更新できない。
- `gh pr merge --delete-branch` や `gh release create` が 403 になるときは、
  `git push origin --delete <branch>` や `gh api -X POST .../releases` を使う。
