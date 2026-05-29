package com.cde.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkControllerTest {

    @TempDir
    private Path tempDir;

    @Test
    void presetsAndScenarioNamesCoverKnownNetworkProfiles() {
        NetworkController controller = controller();

        assertThat(controller.getPresets().getData()).asList().hasSize(5);
        assertThat(ReflectionTestUtils.invokeMethod(controller, "getScenarioName", 0, 0, 0).toString())
                .isNotBlank();
        assertThat(ReflectionTestUtils.invokeMethod(controller, "getScenarioName", 10, 0, 0).toString())
                .isNotBlank();
        assertThat(ReflectionTestUtils.invokeMethod(controller, "getScenarioName", 100, 5, 10).toString())
                .isNotBlank();
        assertThat(ReflectionTestUtils.invokeMethod(controller, "getScenarioName", 250, 15, 2).toString())
                .isNotBlank();
        assertThat(ReflectionTestUtils.invokeMethod(controller, "getScenarioName", 500, 30, 1).toString())
                .isNotBlank();
        assertThat(ReflectionTestUtils.invokeMethod(controller, "getScenarioName", 12, 3, 4).toString())
                .isNotBlank();
    }

    @Test
    void simulateReturnsFailurePayloadWhenTcCommandCannotRun() {
        NetworkController controller = controller("""
                @echo off
                set "cmd=%~2"
                if "%cmd%"=="ip link show eth0 2>/dev/null" (exit /b 0)
                echo failed
                exit /b 7
                """, """
                #!/bin/sh
                cmd="$2"
                if [ "$cmd" = "ip link show eth0 2>/dev/null" ]; then exit 0; fi
                printf failed
                exit 7
                """);

        assertThat(controller.simulate(1, 1, 1).isSuccess()).isFalse();
    }

    @Test
    void executeCommandReturnsOutputAndReportsExitCodeAndTimeout() {
        NetworkController controller = controller();

        assertThat(ReflectionTestUtils.invokeMethod(controller, "executeCommand", "printf ok", 5).toString())
                .isEqualTo("ok");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(controller, "executeCommand", "printf bad; exit 7", 5))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(controller, "executeCommand", "sleep 2", 1))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void simulateCanResetAndApplyNetworkProfileWhenCommandsSucceed() {
        NetworkController controller = controller();

        assertThat(controller.simulate(0, 0, 0).isSuccess()).isTrue();
        assertThat(controller.simulate(100, 5, 10).isSuccess()).isTrue();
    }

    private NetworkController controller() {
        return controller("""
                @echo off
                set "cmd=%~2"
                if "%cmd%"=="printf ok" (echo ok& exit /b 0)
                if "%cmd%"=="printf bad; exit 7" (echo bad& exit /b 7)
                if "%cmd%"=="sleep 2" (ping -n 3 127.0.0.1 > nul& exit /b 0)
                if "%cmd%"=="ip link show eth0 2>/dev/null" (exit /b 0)
                echo ok
                exit /b 0
                """, """
                #!/bin/sh
                cmd="$2"
                case "$cmd" in
                  "printf ok") printf ok; exit 0 ;;
                  "printf bad; exit 7") printf bad; exit 7 ;;
                  "sleep 2") sleep 2; exit 0 ;;
                  "ip link show eth0 2>/dev/null") exit 0 ;;
                  *) printf ok; exit 0 ;;
                esac
                """);
    }

    private NetworkController controller(String windowsScript, String unixScript) {
        try {
            Path shell = tempDir.resolve(isWindows() ? "sh.bat" : "sh");
            Files.writeString(shell, isWindows() ? windowsScript : unixScript, StandardCharsets.UTF_8);
            shell.toFile().setExecutable(true);
            NetworkController controller = new NetworkController();
            ReflectionTestUtils.setField(controller, "shellExecutable", shell.toAbsolutePath().toString());
            ReflectionTestUtils.setField(controller, "shellOption", "-c");
            return controller;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
