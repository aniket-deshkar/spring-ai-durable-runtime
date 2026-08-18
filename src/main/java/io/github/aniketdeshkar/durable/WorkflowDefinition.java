package io.github.aniketdeshkar.durable;

import java.util.HashSet;
import java.util.List;

public record WorkflowDefinition(String name, List<StepDefinition> steps) {
  public WorkflowDefinition {
    if (name == null || name.isBlank() || steps == null || steps.isEmpty())
      throw new IllegalArgumentException("workflow name and steps are required");
    steps = List.copyOf(steps);
    var ids = new HashSet<String>();
    if (steps.stream().anyMatch(step -> !ids.add(step.id())))
      throw new IllegalArgumentException("step ids must be unique");
  }
}
