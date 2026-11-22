# Android CI（ワークフローの配置手順）

GitHub App に `workflows` 権限が無いため、このリポジトリへは `.github/workflows/` 配下を
push できない。そこでワークフロー定義を `docs/ci/android.yml` に置いてある。

## 配置手順

1. `docs/ci/android.yml` の中身を **そのまま**（YAML のみ。前置きやコードフェンスは無い）
   `.github/workflows/android.yml` としてコミットする。
   - GitHub の Web UI（Add file → Create new file → `.github/workflows/android.yml`）で
     貼り付けるのが一番簡単。
   - 権限のある PC からなら `mkdir -p .github/workflows && cp docs/ci/android.yml .github/workflows/`
     して push。
2. 配置した時点で、`main` への push・`v*` タグの push・PR で CI が動く。

## やること

- `android/` で JVM ユニットテスト・Lint・Release APK ビルド
- `apksigner verify` で署名を確認
- タグ `v*` の push で GitHub Release に APK を添付（固定配布鍵で署名済み）

## 署名について

`android/keystore/call5min-timer.jks` は配布用の固定鍵で、リポジトリに置いてある。
CI でも同じ鍵を使うため、Secrets の追加は不要。鍵の扱いと差し替えの注意は
[android/keystore/README.md](../../android/keystore/README.md) を参照。

リリース手順そのものは [docs/RELEASE.md](../RELEASE.md)。
