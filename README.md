# JUnit Manager

Maven プロジェクトの JUnit を、VS Code のテストタブのように一覧・実行する Swing アプリです。見た目は FlatLaf です。

## 必要環境

- JDK 17 以上
- Maven 3.6 以上（プロジェクトに Maven Wrapper がある場合はそちらを使います）

## 起動

リポジトリのルートは集約用の `pom` です。本体は `app`、サンプルは `examples/calculator` です。

```bash
mvn -pl app exec:java
```

引数にプロジェクトのフォルダを渡すと、起動直後に開きます。

```bash
mvn -pl app exec:java -Dexec.args="examples/calculator"
```

配布用の JAR を作る場合:

```bash
mvn -q package
java -jar app/target/junit-manager-1.0.0.jar
```

## 使い方

1. **プロジェクトを開く**で、`pom.xml` があるフォルダを選びます。フォルダをウィンドウへドロップしても開けます。
2. `src/test/java` にある JUnit 4 / 5 のテストが、モジュール・パッケージ・クラス・メソッドのツリーに並びます。
3. **すべて実行**、**選択を実行**、**失敗を再実行**は `mvn test` を呼び出します。結果は Surefire の XML から読み、失敗のメッセージとスタックトレースを右ペインに出します。
4. スタックトレースの行をクリックすると、プロジェクト内のソースを開きます。
5. **変更を監視**をオンにすると、ソース保存のあと直前のテストを再実行します。
6. **デバッグ**はポート 5005 でデバッガの接続を待ちます（`suspend=y`）。IDE のリモートデバッグから接続してください。

| 操作 | キー |
| --- | --- |
| プロジェクトを開く | Ctrl+O |
| すべて実行 | F5 |
| 選択を実行 | F6 |
| 失敗を再実行 | F7 |
| デバッグ | Ctrl+F5 |
| 停止 | Shift+F5 |
| 再検出 | Ctrl+Shift+R |
| フィルター | Ctrl+F |

サンプルは `examples/calculator` です。引き算のテストは失敗するようにしてあるので、結果表示の確認に使えます。

## 検出と実行

- 検出対象はソース上の `@Test` `@ParameterizedTest` `@RepeatedTest` `@TestFactory` `@TestTemplate` `@Theory` です。`@DisplayName` `@Disabled` `@Ignore` `@Nested` も反映します。
- TestNG の `@Test` は import が分かれば除外します。
- 実行は Maven Surefire です。単一モジュールのテストはそのモジュールで、複数モジュールにまたがる場合はルートから `-pl` で実行します。
- マルチモジュールで依存が未インストールのときは、先にルートで「すべて実行」すると解決できます。
