package io.github.aniketdeshkar.durable;

import java.time.Instant;
import java.util.Map;

public record ExecutionCheckpoint(
    String executionId,
    String workflowName,
    ExecutionStatus status,
    int currentStep,
    Map<String, StepCheckpoint> steps,
    Map<String, Object> signals,
    long version,
    Instant updatedAt) {
  public ExecutionCheckpoint {
    steps = Map.copyOf(steps);
    signals = Map.copyOf(signals);
  }

  public boolean terminal() {
    return status == ExecutionStatus.COMPLETED
        || status == ExecutionStatus.FAILED
        || status == ExecutionStatus.CANCELLED;
  }
}
