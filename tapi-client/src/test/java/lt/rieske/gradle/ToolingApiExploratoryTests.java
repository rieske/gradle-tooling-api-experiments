package lt.rieske.gradle;

import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ResultHandler;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    void launchesExistingTaskSynchronously() {
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
    void launchesExistingTaskAsynchronously() {
        var connector = connectorForCurrentProject();
        OutputStream standardOutput = new ByteArrayOutputStream();
        OutputStream standardError = new ByteArrayOutputStream();
        List<ProgressEvent> progressEvents = new ArrayList<>();
        OutcomeCapturingResultHandler<Void> resultHandler = new OutcomeCapturingResultHandler<>();

        try (var connection = connector.connect()) {
            var buildLauncher = connection.newBuild().forTasks(":projects");
            buildLauncher.setStandardOutput(standardOutput);
            buildLauncher.setStandardError(standardError);
            buildLauncher.addProgressListener(progressEvents::add, Set.of(OperationType.TASK));

            buildLauncher.run(resultHandler);
        } // when connection is closed, it blocks until the in-flight operations ar complete

        assertThat(resultHandler.onCompleteCalled()).isTrue();
        assertThat(resultHandler.hasFailure()).isFalse();
        assertThat(progressEvents).hasSize(2);
        assertThat(progressEvents.get(0).getDisplayName()).isEqualTo("Task :projects started");
        assertThat(progressEvents.get(1).getDisplayName()).isEqualTo("Task :projects SUCCESS");
        assertThat(standardOutput.toString()).contains("Root project 'gradle-tooling-api-experiments'");
        assertThat(standardError.toString()).isEmpty();
    }

    @Test
    void throwsWhenLaunchingNonExistentTaskSynchronously() {
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

    @Test
    void notifiesOfFailureWhenLaunchingNonExistentTaskAsynchronously() {
        var connector = connectorForCurrentProject();
        List<ProgressEvent> progressEvents = new ArrayList<>();
        OutputStream standardOutput = new ByteArrayOutputStream();
        OutputStream standardError = new ByteArrayOutputStream();
        OutcomeCapturingResultHandler<Void> resultHandler = new OutcomeCapturingResultHandler<>();

        try (var connection = connector.connect()) {
            var buildLauncher = connection.newBuild().forTasks(":foobar");
            buildLauncher.setStandardOutput(standardOutput);
            buildLauncher.setStandardError(standardError);
            buildLauncher.addProgressListener(progressEvents::add, Set.of(OperationType.TASK));

            buildLauncher.run(resultHandler);
        }

        assertThat(progressEvents.isEmpty());
        assertThat(standardError.toString()).contains("Task 'foobar' not found in root project 'gradle-tooling-api-experiments'.");
        assertThat(resultHandler.onCompleteCalled()).isFalse();
        assertThat(resultHandler.hasFailure()).isTrue();
    }

    private static GradleConnector connectorForCurrentProject() {
        return GradleConnector.newConnector().forProjectDirectory(new File("."));
    }

    private static class OutcomeCapturingResultHandler<T> implements ResultHandler<T> {

        private boolean onCompleteCalled = false;
        private T result = null;
        private GradleConnectionException failure = null;

        @Override
        public void onComplete(T result) {
            this.onCompleteCalled = true;
            this.result = result;
        }

        @Override
        public void onFailure(GradleConnectionException failure) {
            this.failure = failure;
        }

        boolean onCompleteCalled() {
            return onCompleteCalled;
        }

        boolean hasFailure() {
            return failure != null;
        }
    }
}
