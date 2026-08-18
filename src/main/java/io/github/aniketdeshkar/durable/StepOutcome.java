package io.github.aniketdeshkar.durable;

import java.util.Map;

public sealed interface StepOutcome permits StepOutcome.Completed, StepOutcome.Waiting {
  record Completed(Map<String, Object> output) implements StepOutcome {
    public Completed {
      output = Map.copyOf(output);
    }
  }

  record Waiting(String reason) implements StepOutcome {}

  static Completed completed(Map<String, Object> output) {
    return new Completed(output);
  }

  static Waiting waiting(String reason) {
    return new Waiting(reason);
  }
}
