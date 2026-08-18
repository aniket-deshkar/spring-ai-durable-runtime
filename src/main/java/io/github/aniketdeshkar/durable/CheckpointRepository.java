package io.github.aniketdeshkar.durable;

import java.util.Optional;

public interface CheckpointRepository {
  Optional<ExecutionCheckpoint> find(String executionId);

  ExecutionCheckpoint create(ExecutionCheckpoint checkpoint);

  ExecutionCheckpoint save(ExecutionCheckpoint checkpoint, long expectedVersion);
}
