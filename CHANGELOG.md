# Changelog

## 0.1.0 - 2026-08-18

- Added explicit checkpointed workflow execution, pause, resume, cancellation, and failure state.
- Added in-memory and JDBC/PostgreSQL checkpoint repositories with optimistic versions.
- Added stable step idempotency keys and deterministic crash/restart tests.
