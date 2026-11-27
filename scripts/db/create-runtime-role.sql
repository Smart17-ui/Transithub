-- ============================================================================
-- TransitHub: one-time database setup (ADR-0003, tenant isolation)
--
-- Run ONCE per PostgreSQL server, as a superuser (for example "postgres"),
-- in pgAdmin's Query Tool or with:
--   psql -U postgres -h localhost -f scripts/db/create-runtime-role.sql
--
-- Creates the role the application uses at runtime. It is NOT the owner of
-- any table, so PostgreSQL row-level security always applies to it and it
-- cannot switch security off. Table ownership and migrations stay with
-- transithub_app (Flyway). Table permissions are granted by the Flyway
-- migrations, not here.
--
-- BEFORE RUNNING: replace CHANGE_ME with a strong password, then store the
-- same password in the DB_RUNTIME_PASSWORD user environment variable:
--   [Environment]::SetEnvironmentVariable('DB_RUNTIME_PASSWORD','<password>','User')
-- Never commit the real password.
-- ============================================================================

CREATE ROLE transithub_runtime
    LOGIN
    PASSWORD 'CHANGE_ME'
    NOSUPERUSER
    NOBYPASSRLS
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT;
