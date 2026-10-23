-- Existing acceptance volumes only. Never delete/merge historical attempts.
-- ALTER fails if duplicate pairs exist; resolve ownership/history explicitly before retrying.
SET @attempt_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics
  WHERE table_schema='doj_problem' AND table_name='attempt' AND index_name='uk_attempt_candidate'),
  'SELECT 1', 'ALTER TABLE doj_problem.attempt ADD UNIQUE KEY uk_attempt_candidate (assessment_id,candidate_id)');
PREPARE attempt_migration FROM @attempt_ddl;
EXECUTE attempt_migration;
DEALLOCATE PREPARE attempt_migration;
