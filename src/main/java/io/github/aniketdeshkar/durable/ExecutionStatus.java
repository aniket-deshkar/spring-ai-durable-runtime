package io.github.aniketdeshkar.durable;

public enum ExecutionStatus {
  STARTED,
  RUNNING,
  WAITING,
  COMPLETED,
  FAILED,
  CANCELLED
}
