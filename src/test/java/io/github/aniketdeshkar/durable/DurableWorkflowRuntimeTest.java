package io.github.aniketdeshkar.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DurableWorkflowRuntimeTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void restartAndResumeDoesNotRepeatCommittedStep() {
    var repository = new InMemoryCheckpointRepository();
    var modelCalls = new AtomicInteger();
    WorkflowDefinition workflow =
        new WorkflowDefinition(
            "approval",
            List.of(
                new StepDefinition(
                    "model",
                    context -> {
                      modelCalls.incrementAndGet();
                      return StepOutcome.completed(Map.of("answer", "42"));
                    }),
                new StepDefinition(
                    "approval",
                    context ->
                        context.signals().containsKey("approved")
                            ? StepOutcome.completed(Map.of())
                            : StepOutcome.waiting("human approval")),
                new StepDefinition(
                    "publish", context -> StepOutcome.completed(Map.of("published", true)))));
    var firstProcess = new DurableWorkflowRuntime(repository, CLOCK);
    firstProcess.start("execution-1", workflow);

    ExecutionCheckpoint waiting = firstProcess.run("execution-1", workflow);
    var restartedProcess = new DurableWorkflowRuntime(repository, CLOCK);
    ExecutionCheckpoint completed =
        restartedProcess.resume("execution-1", workflow, Map.of("approved", true));

    assertEquals(ExecutionStatus.WAITING, waiting.status());
    assertEquals(ExecutionStatus.COMPLETED, completed.status());
    assertEquals(1, modelCalls.get());
    assertEquals(StepStatus.COMPLETED, completed.steps().get("model").status());
  }

  @Test
  void suppliesStableIdempotencyKeyToSideEffect() {
    var repository = new InMemoryCheckpointRepository();
    var seen = new java.util.ArrayList<String>();
    var workflow =
        workflow(
            "send",
            context -> {
              seen.add(context.idempotencyKey());
              return StepOutcome.completed(Map.of());
            });
    var runtime = new DurableWorkflowRuntime(repository, CLOCK);
    runtime.start("order-7", workflow);

    runtime.run("order-7", workflow);

    assertEquals(List.of("order-7:send"), seen);
  }

  @Test
  void recordsFailureTypeAndAttemptMetadata() {
    var repository = new InMemoryCheckpointRepository();
    var workflow =
        workflow(
            "model",
            context -> {
              throw new IllegalStateException("provider details");
            });
    var runtime = new DurableWorkflowRuntime(repository, CLOCK);
    runtime.start("failed", workflow);

    assertThrows(IllegalStateException.class, () -> runtime.run("failed", workflow));
    ExecutionCheckpoint state = repository.find("failed").orElseThrow();

    assertEquals(ExecutionStatus.FAILED, state.status());
    assertEquals(1, state.steps().get("model").attempts());
    assertEquals("IllegalStateException", state.steps().get("model").message());
  }

  @Test
  void cancelsActiveExecutionAndWillNotRunIt() {
    var calls = new AtomicInteger();
    var repository = new InMemoryCheckpointRepository();
    var workflow =
        workflow(
            "tool",
            context -> {
              calls.incrementAndGet();
              return StepOutcome.completed(Map.of());
            });
    var runtime = new DurableWorkflowRuntime(repository, CLOCK);
    runtime.start("cancelled", workflow);

    runtime.cancel("cancelled", workflow);
    ExecutionCheckpoint result = runtime.run("cancelled", workflow);

    assertEquals(ExecutionStatus.CANCELLED, result.status());
    assertEquals(0, calls.get());
  }

  @Test
  void rejectsDuplicateExecutionsAndStaleWrites() {
    var repository = new InMemoryCheckpointRepository();
    var workflow = workflow("one", context -> StepOutcome.completed(Map.of()));
    var runtime = new DurableWorkflowRuntime(repository, CLOCK);
    ExecutionCheckpoint initial = runtime.start("same", workflow);

    assertThrows(CheckpointConflictException.class, () -> runtime.start("same", workflow));
    ExecutionCheckpoint saved = repository.save(initial, 0);
    assertEquals(1, saved.version());
    assertThrows(CheckpointConflictException.class, () -> repository.save(initial, 0));
  }

  @Test
  void validatesDefinitionsAndResumeState() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StepDefinition("", context -> StepOutcome.completed(Map.of())));
    assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition("empty", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WorkflowDefinition(
                "duplicate",
                List.of(
                    new StepDefinition("a", context -> StepOutcome.completed(Map.of())),
                    new StepDefinition("a", context -> StepOutcome.completed(Map.of())))));
    var repository = new InMemoryCheckpointRepository();
    var workflow = workflow("one", context -> StepOutcome.completed(Map.of()));
    var runtime = new DurableWorkflowRuntime(repository, CLOCK);
    runtime.start("active", workflow);
    assertThrows(IllegalStateException.class, () -> runtime.resume("active", workflow, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> runtime.run("missing", workflow));
  }

  @Test
  void completedExecutionIsStableAcrossRepeatedRunCalls() {
    var repository = new InMemoryCheckpointRepository();
    var workflow = workflow("one", context -> StepOutcome.completed(Map.of()));
    var runtime = new DurableWorkflowRuntime(repository, CLOCK);
    runtime.start("done", workflow);
    ExecutionCheckpoint first = runtime.run("done", workflow);

    ExecutionCheckpoint second = runtime.run("done", workflow);

    assertTrue(first.terminal());
    assertEquals(first.version(), second.version());
  }

  private static WorkflowDefinition workflow(String id, WorkflowStep step) {
    return new WorkflowDefinition("workflow", List.of(new StepDefinition(id, step)));
  }
}
