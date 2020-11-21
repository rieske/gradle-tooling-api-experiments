package lt.rieske.gradle;

import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.model.GradleProject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class ToolingApiExploratoryTests {

    @Test
    void getsProjectModel() {
        var connector = connectorForCurrentProject();
        try (var connection = connector.connect()) {
            var modelBuilder = connection.model(GradleProject.class);

            var project = modelBuilder.get();

            assertThat(project.getName()).isEqualTo("gradle-tooling-api-experiments");
            assertThat(project.getParent()).isNull();
            assertThat(project.getPath()).isEqualTo(":");
            assertThat(project.getChildren()).isNotEmpty();
            assertThat(project.getTasks()).isNotEmpty();
        }
    }

    @Test
    void launchesExistingTask() {
        var connector = connectorForCurrentProject();
        OutputStream standardOutput = new ByteArrayOutputStream();
        OutputStream standardError = new ByteArrayOutputStream();
        List<ProgressEvent> progressEvents = new ArrayList<>();

        try (var connection = connector.connect()) {
            var buildLauncher = connection.newBuild().forTasks(":projects");
            buildLauncher.setStandardOutput(standardOutput);
            buildLauncher.setStandardError(standardError);
            buildLauncher.addProgressListener(progressEvents::add, Set.of(OperationType.TASK));

            buildLauncher.run();
        }

        assertThat(progressEvents).hasSize(2);
        assertThat(progressEvents.get(0).getDisplayName()).isEqualTo("Task :projects started");
        assertThat(progressEvents.get(1).getDisplayName()).isEqualTo("Task :projects SUCCESS");
        assertThat(standardOutput.toString()).contains("Root project 'gradle-tooling-api-experiments'");
        assertThat(standardError.toString()).isEmpty();
    }

    @Test
    void throwsWhenLaunchingNonExistentTask() {
        var connector = connectorForCurrentProject();
        List<ProgressEvent> progressEvents = new ArrayList<>();
        OutputStream standardOutput = new ByteArrayOutputStream();
        OutputStream standardError = new ByteArrayOutputStream();
        try (var connection = connector.connect()) {
            var buildLauncher = connection.newBuild().forTasks(":foobar");
            buildLauncher.setStandardOutput(standardOutput);
            buildLauncher.setStandardError(standardError);
            buildLauncher.addProgressListener(progressEvents::add, Set.of(OperationType.TASK));

            buildLauncher.run();
            fail("Expected build launcher to throw");
        } catch (BuildException e) {
            assertThat(progressEvents.isEmpty());
            assertThat(standardError.toString()).contains("Task 'foobar' not found in root project 'gradle-tooling-api-experiments'.");
        }
    }

    private static GradleConnector connectorForCurrentProject() {
        return GradleConnector.newConnector().forProjectDirectory(new File("."));
    }
}
