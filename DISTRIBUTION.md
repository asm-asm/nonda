# APKをGitHubで配布する手順

この手順では、GitHub ReleasesにAPKを置き、GitHub Pagesの初心者向けページからダウンロードできるようにします。

> [!IMPORTANT]
> `app-debug.apk` は動作確認用です。一般配布には、必ず同じ署名鍵で署名したRelease APKを使用してください。署名鍵を失うと、利用者がアプリを上書き更新できなくなります。

## 1. 配布用APKを作る

Android Studioで次の順に操作します。

1. 上部メニューの `Build` → `Generate Signed App Bundle or APK` を開く
2. `APK` を選択して `Next`
3. 初回だけ `Create new...` を押す
4. Key store pathを、このプロジェクトフォルダーの外に指定する
5. パスワード、Key alias、Key passwordを入力する
6. Validityは25年以上にする
7. 証明書欄を入力して `OK`
8. 作成した署名鍵を選択し、`Next`
9. `release` を選択し、署名バージョンは両方にチェックして `Create`

完成したAPKは通常、次の場所にあります。

```text
app/build/outputs/apk/release/app-release.apk
```

署名鍵とパスワードは、パスワード管理アプリなど安全な場所にバックアップしてください。GitHubには絶対にアップロードしないでください。

## 2. GitHubへプロジェクトを公開する

初心者にはGitHub Desktopが簡単です。

1. [GitHub Desktop](https://desktop.github.com/)をインストールしてログインする
2. `File` → `Add Local Repository` を開く
3. このプロジェクトのフォルダーを選ぶ
4. リポジトリではないと表示されたら、作成するリンクを押す
5. Repository nameを `nonda` にする
6. `Create repository` を押す
7. 左下のSummaryに「初回公開」と入力して `Commit to main`
8. 上部の `Publish repository` を押す

個人情報を含めたくない場合は、公開前にGitHub Desktopの変更一覧を確認してください。このプロジェクトでは署名鍵、端末固有設定、ビルド結果がアップロードされないよう `.gitignore` を用意しています。

## 3. APKをGitHub Releasesへ載せる

1. ブラウザーでGitHubのリポジトリを開く
2. 右側の `Releases` → `Create a new release`
3. `Choose a tag` に `v1.0.0` と入力して新規作成
4. Release titleに「飲んだ？ v1.0.0」と入力
5. Release APKを `nonda-v1.0.0.apk` に名前変更して添付
6. 変更内容と注意事項を書く
7. `Publish release` を押す

## 4. 初心者向けページを公開する

1. GitHubリポジトリの `Settings` を開く
2. 左側の `Pages` を開く
3. Sourceで `Deploy from a branch` を選ぶ
4. Branchを `main`、フォルダーを `/docs` にする
5. `Save` を押す
6. 数分後に表示されるURLを開く

ページのURLは通常、次の形式です。

```text
https://asm-asm.github.io/nonda/
```

ページのダウンロードボタンは、そのリポジトリの最新Releaseへ自動的に移動します。

## 5. アプリを更新するとき

1. `app/build.gradle.kts` の `versionCode` を必ず1増やす
2. `versionName` を `1.0.1` などに変更する
3. 初回と同じ署名鍵でRelease APKを作る
4. GitHubで `v1.0.1` などの新しいReleaseを作る
5. 新しいAPKを添付して公開する

利用者は新しいAPKをダウンロードして開くと、保存済みの記録を残したまま上書き更新できます。同じ署名鍵を使用していることが必要です。

## 公開前チェック

- 朝・夜の記録とウィジェットが連動する
- 画面消灯中でも通知される
- 「飲んだ」「音を止める」が動く
- 通知権限を拒否した場合もクラッシュしない
- 署名鍵がGitHubの変更一覧にない
- APKを一度アンインストールした端末へ新規インストールできる
- 旧版を入れた端末へ上書き更新できる
