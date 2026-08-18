package io.github.aniketdeshkar.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class JdbcCheckpointRepositoryTest {
  @Test
  void persistsAndOptimisticallyUpdatesCheckpointUsingPostgresSqlMode() {
    var source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:checkpoint;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    var repository = new JdbcCheckpointRepository(source, new ObjectMapper());
    repository.initialize();
    var initial =
        new ExecutionCheckpoint(
            "jdbc-1",
            "workflow",
            ExecutionStatus.STARTED,
            0,
            Map.of("one", StepCheckpoint.pending()),
            Map.of(),
            0,
            Instant.parse("2026-01-01T00:00:00Z"));

    repository.create(initial);
    var running =
        new ExecutionCheckpoint(
            "jdbc-1",
            "workflow",
            ExecutionStatus.RUNNING,
            0,
            initial.steps(),
            Map.of("approved", true),
            0,
            initial.updatedAt());
    ExecutionCheckpoint saved = repository.save(running, 0);

    assertEquals(1, saved.version());
    assertEquals(ExecutionStatus.RUNNING, repository.find("jdbc-1").orElseThrow().status());
    assertEquals(true, repository.find("jdbc-1").orElseThrow().signals().get("approved"));
    assertThrows(CheckpointConflictException.class, () -> repository.save(running, 0));
    assertThrows(CheckpointConflictException.class, () -> repository.create(initial));
  }
}
