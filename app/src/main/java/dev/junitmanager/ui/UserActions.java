package dev.junitmanager.ui;

import dev.junitmanager.model.TestNode;

public interface UserActions {
    void openProject();

    void runAll();

    void runSelected();

    void runFailed();

    void debugSelected();

    void stop();

    void refresh();

    void clearResults();

    void watch(boolean enabled);

    void runNode(TestNode node, boolean debug);

    void openSource(TestNode node);

    void openStackLine(String line);

    void copySelector(TestNode node);

    void copyName(TestNode node);

    void copyTestCommand(TestNode node);

    void reveal(TestNode node);

    void selectNode(TestNode node);
}
