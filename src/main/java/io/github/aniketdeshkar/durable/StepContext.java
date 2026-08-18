package io.github.aniketdeshkar.durable;

import java.util.Map;

public record StepContext(
    String executionId,
    String stepId,
    Map<String, StepCheckpoint> completedSteps,
    Map<String, Object> signals) {
  public String idempotencyKey() {
    return executionId + ":" + stepId;
  }
}
