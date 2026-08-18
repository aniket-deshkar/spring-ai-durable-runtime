package io.github.aniketdeshkar.durable;

@FunctionalInterface
public interface WorkflowStep {
  StepOutcome execute(StepContext context);
}
