# Walti Plugin

Walti Plugin は Jenkins の 「ビルド後の処理」にセキュリティスキャンサービス [Walti](https://walti.io/) によるスキャンの実行を追加するプラグインです。

[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/walti-plugin/2/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/walti-plugin/2/)

## 使用方法

* [Walti](https://walti.io) のアカウントをまだお持ちでない場合はサインアップしてアカウントを取得してください。
* プラグインをインストールすると、プロジェクト設定画面の「ビルド後の処理」に「Waltiでスキャンを実行」が追加されるのでこちらを選択します。
* スキャンの際に必要となるAPIキーとAPIシークレットを入力します。
* APIキーとシークレットに基づいて組織が特定され、ターゲットの候補が更新されるのでスキャンするターゲットを選択してください。
* 実行したいスキャンの種類をチェックして変更を保存してください。

## 関連リポジトリ

* [walti/walti-api-jar](https://github.com/walti/walti-api-jar)
