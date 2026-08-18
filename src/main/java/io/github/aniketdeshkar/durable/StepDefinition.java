package io.github.aniketdeshkar.durable;

public record StepDefinition(String id, WorkflowStep action) {
  public StepDefinition {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("step id must not be blank");
    if (action == null) throw new IllegalArgumentException("action is required");
  }
}
