package io.github.aniketdeshkar.durable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcCheckpointRepository implements CheckpointRepository {
  private final DataSource dataSource;
  private final ObjectMapper mapper;

  public JdbcCheckpointRepository(DataSource dataSource, ObjectMapper mapper) {
    this.dataSource = dataSource;
    this.mapper = mapper.findAndRegisterModules();
  }

  public void initialize() {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE IF NOT EXISTS ai_workflow_checkpoint (execution_id VARCHAR(128) PRIMARY KEY, version BIGINT NOT NULL, payload TEXT NOT NULL)");
    } catch (SQLException error) {
      throw persistence(error);
    }
  }

  public Optional<ExecutionCheckpoint> find(String id) {
    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement(
                "SELECT payload FROM ai_workflow_checkpoint WHERE execution_id=?")) {
      statement.setString(1, id);
      try (var result = statement.executeQuery()) {
        return result.next()
            ? Optional.of(mapper.readValue(result.getString(1), ExecutionCheckpoint.class))
            : Optional.empty();
      }
    } catch (Exception error) {
      throw persistence(error);
    }
  }

  public ExecutionCheckpoint create(ExecutionCheckpoint checkpoint) {
    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement(
                "INSERT INTO ai_workflow_checkpoint(execution_id, version, payload) VALUES (?, ?, ?)")) {
      statement.setString(1, checkpoint.executionId());
      statement.setLong(2, checkpoint.version());
      statement.setString(3, mapper.writeValueAsString(checkpoint));
      statement.executeUpdate();
      return checkpoint;
    } catch (Exception error) {
      throw new CheckpointConflictException("execution already exists or could not be created");
    }
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
    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement(
                "UPDATE ai_workflow_checkpoint SET version=?, payload=? WHERE execution_id=? AND version=?")) {
      statement.setLong(1, next.version());
      statement.setString(2, mapper.writeValueAsString(next));
      statement.setString(3, next.executionId());
      statement.setLong(4, expectedVersion);
      if (statement.executeUpdate() != 1)
        throw new CheckpointConflictException("checkpoint version changed");
      return next;
    } catch (CheckpointConflictException error) {
      throw error;
    } catch (Exception error) {
      throw persistence(error);
    }
  }

  private static IllegalStateException persistence(Exception error) {
    return new IllegalStateException("checkpoint persistence failed", error);
  }
}
