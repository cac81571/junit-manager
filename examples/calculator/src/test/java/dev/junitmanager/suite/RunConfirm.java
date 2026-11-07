package dev.junitmanager.suite;

import javax.swing.JOptionPane;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * 実行前確認。デフォルトは非対話（確認なし）。
 * {@code -Dconfirm=console} または {@code -Dconfirm=dialog} で有効化。
 */
public final class RunConfirm {
    private RunConfirm() {
    }

    public static void ask(String preconditions) {
        String mode = System.getProperty("confirm", "").trim();
        if (mode.isEmpty()) {
            return;
        }
        switch (mode.toLowerCase()) {
            case "console" -> console(preconditions);
            case "dialog" -> dialog(preconditions);
            default -> throw new IllegalArgumentException("confirm は console か dialog です: " + mode);
        }
    }

    private static void console(String preconditions) {
        String prompt = preconditions + "\n実行しますか？ [y/N]: ";
        Console console = System.console();
        String answer;
        if (console != null) {
            console.printf("%s", prompt);
            answer = console.readLine();
        } else {
            System.out.println(prompt);
            System.out.flush();
            try {
                answer = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset())).readLine();
            } catch (IOException ex) {
                answer = null;
            }
        }
        proceed(answer != null && answer.trim().equalsIgnoreCase("y"));
    }

    private static void dialog(String preconditions) {
        int result = JOptionPane.showConfirmDialog(
                null,
                preconditions + "\n\n実行しますか？",
                "実行確認",
                JOptionPane.OK_CANCEL_OPTION
        );
        proceed(result == JOptionPane.OK_OPTION);
    }

    private static void proceed(boolean accepted) {
        if (accepted) {
            return;
        }
        System.out.println("ユーザが中断しました");
        System.exit(1);
    }
}
