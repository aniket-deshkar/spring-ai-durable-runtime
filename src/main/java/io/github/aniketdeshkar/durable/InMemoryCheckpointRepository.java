package io.github.aniketdeshkar.durable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCheckpointRepository implements CheckpointRepository {
  private final ConcurrentHashMap<String, ExecutionCheckpoint> checkpoints =
      new ConcurrentHashMap<>();

  public Optional<ExecutionCheckpoint> find(String id) {
    return Optional.ofNullable(checkpoints.get(id));
  }

  public ExecutionCheckpoint create(ExecutionCheckpoint checkpoint) {
    if (checkpoints.putIfAbsent(checkpoint.executionId(), checkpoint) != null)
      throw new CheckpointConflictException("execution already exists");
    return checkpoint;
  }

  public ExecutionCheckpoint save(ExecutionCheckpoint checkpoint, long expectedVersion) {
    var next =
        new ExecutionCheckpoint(
            checkpoint.executionId(),
            checkpoint.workflowName(),
            checkpoint.status(),
            checkpoint.currentStep(),
            checkpoint.steps(),
            checkpoint.signals(),
            expectedVersion + 1,
            checkpoint.updatedAt());
    boolean saved =
        checkpoints.computeIfPresent(
                checkpoint.executionId(),
                (id, current) -> current.version() == expectedVersion ? next : current)
            == next;
    if (!saved) throw new CheckpointConflictException("checkpoint version changed");
    return next;
  }
}
