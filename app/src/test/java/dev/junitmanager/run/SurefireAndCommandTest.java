package dev.junitmanager.run;

import dev.junitmanager.model.TestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurefireAndCommandTest {
    @TempDir
    Path temp;

    @Test
    void parsesOnlyUpdatedReports() throws Exception {
        Path module = temp.resolve("mod");
        Path reports = module.resolve("target/surefire-reports");
        Files.createDirectories(reports);
        Path xml = reports.resolve("TEST-com.example.CalcTest.xml");
        Files.writeString(xml, report(
                "adds(int, int)[2]",
                "failure",
                "expected 0 but was 1",
                "trace-line\n\tat com.example.CalcTest.subtracts(CalcTest.java:24)\n"));
        Map<Path, Long> before = SurefireReports.snapshot(List.of(module));
        assertTrue(SurefireReports.parseUpdated(List.of(module), before).isEmpty());

        Files.writeString(xml, report(
                "adds(int, int)[2]",
                "failure",
                "expected 0 but was 1",
                "trace-line\n\tat com.example.CalcTest.subtracts(CalcTest.java:24)\n")
                + extraSkipped());
        Files.setLastModifiedTime(xml, FileTime.from(Instant.now().plusSeconds(2)));
        List<ReportedTest> tests = SurefireReports.parseUpdated(List.of(module), before);
        assertEquals(2, tests.size());
        ReportedTest failed = tests.get(0);
        assertEquals(TestStatus.FAILED, failed.status());
        assertEquals("adds(int, int)[2]", failed.name());
        assertTrue(failed.trace().contains("CalcTest.java:24"));
        assertEquals(TestStatus.SKIPPED, tests.get(1).status());
        assertTrue(failed.durationMillis() >= 40);
    }

    @Test
    void windowsCommandKeepsSelectorCharacters() {
        List<String> args = List.of("-B", "test", "-Dtest=com.example.Foo$Bar#baz+other");
        MavenCommands.Launch launch = MavenCommands.launch(temp, temp, args);
        String rendered = String.join("\n", launch.command());
        assertTrue(rendered.contains("-Dtest=com.example.Foo$Bar#baz+other"), rendered);
        assertTrue(launch.display().contains("-Dtest=com.example.Foo$Bar#baz+other"));
        if (MavenCommands.isWindows()) {
            assertEquals("cmd.exe", launch.command().get(0));
        }
    }

    @Test
    void clipboardCommandIncludesCdAndTestSelector() {
        Path dir = temp.resolve("my module").toAbsolutePath().normalize();
        List<String> args = List.of(
                "-B",
                "-Dstyle.color=always",
                "-DtrimStackTrace=false",
                "-Dsurefire.failIfNoSpecifiedTests=false",
                "-Dtest=com.example.Calc$Div#divides",
                "test");
        String cmd = MavenCommands.clipboardCommand(dir, args, MavenCommands.ClipboardShell.CMD);
        String ps = MavenCommands.clipboardCommand(dir, args, MavenCommands.ClipboardShell.POWERSHELL);
        String extras = " -Dconfirm=console -DforkCount=0";
        String mvnCmd = "mvn test -Dtest=com.example.Calc$Div#divides" + extras;
        String mvnPs = "mvn test '-Dtest=com.example.Calc$Div#divides'" + extras;
        if (MavenCommands.isWindows()) {
            assertEquals("cd /d \"" + dir + "\" && " + mvnCmd, cmd);
        } else {
            assertEquals("cd '" + dir + "' && " + mvnCmd, cmd);
        }
        assertEquals("Set-Location -LiteralPath '" + dir + "'; " + mvnPs, ps);
        assertEquals("mvn test" + extras, MavenCommands.clipboardCommand(null, List.of("-B", "test"), MavenCommands.ClipboardShell.CMD));
    }

    private static String report(String name, String tag, String message, String trace) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.CalcTest" tests="1" failures="1" errors="0" skipped="0" time="0.05">
                  <testcase name="%s" classname="com.example.CalcTest" time="0.042">
                    <%s message="%s" type="org.opentest4j.AssertionFailedError"><![CDATA[%s]]></%s>
                  </testcase>
                """.formatted(name, tag, message, trace, tag);
    }

    private static String extraSkipped() {
        return """
                  <testcase name="disabledOne" classname="com.example.CalcTest" time="0">
                    <skipped message="保留"/>
                  </testcase>
                </testsuite>
                """;
    }
}
