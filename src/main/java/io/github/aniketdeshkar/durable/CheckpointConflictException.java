package io.github.aniketdeshkar.durable;

public final class CheckpointConflictException extends RuntimeException {
  public CheckpointConflictException(String message) {
    super(message);
  }
}
