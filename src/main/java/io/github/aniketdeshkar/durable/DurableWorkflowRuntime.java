package io.github.aniketdeshkar.durable;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DurableWorkflowRuntime {
  private final CheckpointRepository repository;
  private final Clock clock;

  public DurableWorkflowRuntime(CheckpointRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public ExecutionCheckpoint start(String id, WorkflowDefinition workflow) {
    Map<String, StepCheckpoint> steps = new LinkedHashMap<>();
    workflow.steps().forEach(step -> steps.put(step.id(), StepCheckpoint.pending()));
    return repository.create(
        new ExecutionCheckpoint(
            id, workflow.name(), ExecutionStatus.STARTED, 0, steps, Map.of(), 0, clock.instant()));
  }

  public ExecutionCheckpoint run(String id, WorkflowDefinition workflow) {
    ExecutionCheckpoint state = require(id, workflow);
    if (state.terminal() || state.status() == ExecutionStatus.WAITING) return state;
    while (state.currentStep() < workflow.steps().size()) {
      StepDefinition step = workflow.steps().get(state.currentStep());
      StepCheckpoint previous = state.steps().get(step.id());
      if (previous.status() == StepStatus.COMPLETED) {
        state =
            update(
                state,
                ExecutionStatus.RUNNING,
                state.currentStep() + 1,
                state.steps(),
                state.signals());
        continue;
      }
      Map<String, StepCheckpoint> runningSteps = new HashMap<>(state.steps());
      runningSteps.put(
          step.id(), new StepCheckpoint(StepStatus.RUNNING, Map.of(), previous.attempts() + 1, ""));
      state =
          update(
              state, ExecutionStatus.RUNNING, state.currentStep(), runningSteps, state.signals());
      try {
        StepOutcome outcome =
            step.action().execute(new StepContext(id, step.id(), state.steps(), state.signals()));
        Map<String, StepCheckpoint> nextSteps = new HashMap<>(state.steps());
        if (outcome instanceof StepOutcome.Waiting waiting) {
          nextSteps.put(
              step.id(),
              new StepCheckpoint(
                  StepStatus.WAITING, Map.of(), previous.attempts() + 1, waiting.reason()));
          return update(
              state, ExecutionStatus.WAITING, state.currentStep(), nextSteps, state.signals());
        }
        var completed = (StepOutcome.Completed) outcome;
        nextSteps.put(
            step.id(),
            new StepCheckpoint(
                StepStatus.COMPLETED, completed.output(), previous.attempts() + 1, ""));
        state =
            update(
                state,
                ExecutionStatus.RUNNING,
                state.currentStep() + 1,
                nextSteps,
                state.signals());
      } catch (RuntimeException error) {
        Map<String, StepCheckpoint> failed = new HashMap<>(state.steps());
        failed.put(
            step.id(),
            new StepCheckpoint(
                StepStatus.FAILED,
                Map.of(),
                previous.attempts() + 1,
                error.getClass().getSimpleName()));
        update(state, ExecutionStatus.FAILED, state.currentStep(), failed, state.signals());
        throw error;
      }
    }
    return update(
        state, ExecutionStatus.COMPLETED, state.currentStep(), state.steps(), state.signals());
  }

  public ExecutionCheckpoint resume(
      String id, WorkflowDefinition workflow, Map<String, Object> signals) {
    ExecutionCheckpoint state = require(id, workflow);
    if (state.status() != ExecutionStatus.WAITING)
      throw new IllegalStateException("execution is not waiting");
    Map<String, Object> merged = new HashMap<>(state.signals());
    merged.putAll(signals);
    Map<String, StepCheckpoint> steps = new HashMap<>(state.steps());
    String stepId = workflow.steps().get(state.currentStep()).id();
    StepCheckpoint old = steps.get(stepId);
    steps.put(stepId, new StepCheckpoint(StepStatus.PENDING, Map.of(), old.attempts(), ""));
    update(state, ExecutionStatus.RUNNING, state.currentStep(), steps, merged);
    return run(id, workflow);
  }

  public ExecutionCheckpoint cancel(String id, WorkflowDefinition workflow) {
    ExecutionCheckpoint state = require(id, workflow);
    if (state.terminal()) return state;
    return update(
        state, ExecutionStatus.CANCELLED, state.currentStep(), state.steps(), state.signals());
  }

  private ExecutionCheckpoint require(String id, WorkflowDefinition workflow) {
    ExecutionCheckpoint state =
        repository.find(id).orElseThrow(() -> new IllegalArgumentException("unknown execution"));
    if (!state.workflowName().equals(workflow.name()))
      throw new IllegalArgumentException("workflow does not match checkpoint");
    return state;
  }

  private ExecutionCheckpoint update(
      ExecutionCheckpoint state,
      ExecutionStatus status,
      int index,
      Map<String, StepCheckpoint> steps,
      Map<String, Object> signals) {
    return repository.save(
        new ExecutionCheckpoint(
            state.executionId(),
            state.workflowName(),
            status,
            index,
            steps,
            signals,
            state.version(),
            clock.instant()),
        state.version());
  }
}
