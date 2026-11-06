package dev.junitmanager.model;

public enum TestStatus {
    NOT_RUN("未実行"),
    RUNNING("実行中"),
    PASSED("成功"),
    FAILED("失敗"),
    SKIPPED("スキップ");

    private final String label;

    TestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
