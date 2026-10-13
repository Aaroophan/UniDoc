-- UniDoc Issue 20 / MySQL-only, read-only schema smoke.
-- Run with the unidoc_app account against local MySQL `unidoc`.
-- No CREATE, ALTER, DELETE, INSERT, UPDATE, TRUNCATE, DROP or stored procedures.
-- Results show schema metadata ONLY (no user identities, passwords, JWTs or file paths).

SELECT DATABASE() AS connected_schema,
       @@version AS mysql_version,
       @@character_set_database AS schema_character_set,
       @@collation_database AS schema_collation;

SELECT table_name, table_type, engine
FROM information_schema.tables
WHERE table_schema = 'unidoc'
  AND table_name IN (
    'users', 'student_profiles', 'document_types', 'document_requests',
    'request_attachments', 'issued_documents', 'request_status_history', 'notifications'
  )
ORDER BY table_name;

SELECT COUNT(*) AS approved_table_count
FROM information_schema.tables
WHERE table_schema = 'unidoc'
  AND table_name IN (
    'users', 'student_profiles', 'document_types', 'document_requests',
    'request_attachments', 'issued_documents', 'request_status_history', 'notifications'
  );

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = 'unidoc'
  AND constraint_type IN ('UNIQUE','FOREIGN KEY')
ORDER BY table_name, constraint_type, constraint_name;

SELECT table_name, index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_in_index
FROM information_schema.statistics
WHERE table_schema = 'unidoc'
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

SELECT table_name, column_name, data_type, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'unidoc'
  AND (
      (table_name = 'document_requests' AND column_name IN ('id', 'version', 'status', 'delivery_method', 'created_at'))
      OR (table_name = 'users' AND column_name IN ('email', 'role', 'status', 'auth_version'))
      OR (table_name = 'issued_documents' AND column_name IN ('request_id', 'download_count'))
  )
ORDER BY table_name, column_name;
