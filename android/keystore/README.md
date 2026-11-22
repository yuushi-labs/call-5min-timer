# 配布用署名鍵（call5min-timer.jks）

アプリ内アップデートで自分自身を上書きインストールするため、Release APK は毎回この鍵で署名する。
鍵が変わると Android は更新を拒否する（同じ applicationId でも署名が違うとインストールできない）。

## 中身

| ファイル | 内容 |
| --- | --- |
| `call5min-timer.jks` | 固定の配布用キーストア（JKS / RSA 2048bit / 有効期限 10000 日） |
| `signing.properties` | 上のキーストアを開くためのパスワード・エイリアス |

`signing.properties` の値は `storePassword=call5min-timer` / `keyAlias=call5min` /
`keyPassword=call5min-timer`。これは配布 ID を再現するための公開情報で、秘密情報ではない
（このアプリは個人配布で、ストア公開もしていない）。

## 差し替え・再発行したくなったとき

この鍵を変えると、既存インストールへの上書き更新ができなくなる。変える場合は

1. `versionCode` を上げて別 applicationId にする、または
2. 一度アンインストールしてから入れ直す

のどちらかが必要になる。CI から署名する場合も、この鍵（または同じ鍵を Base64 にした
Secrets）を使うこと。
