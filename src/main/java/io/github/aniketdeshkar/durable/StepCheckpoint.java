package io.github.aniketdeshkar.durable;

import java.util.Map;

public record StepCheckpoint(
    StepStatus status, Map<String, Object> output, int attempts, String message) {
  public StepCheckpoint {
    output = Map.copyOf(output);
  }

  public static StepCheckpoint pending() {
    return new StepCheckpoint(StepStatus.PENDING, Map.of(), 0, "");
  }
}
