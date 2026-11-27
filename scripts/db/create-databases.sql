-- ============================================================================
-- TransitHub: one-time local database setup, step 1 of 2
--
-- Run ONCE per PostgreSQL server as a superuser (for example "postgres"):
--   psql -U postgres -h localhost -f scripts/db/create-databases.sql
-- Then run scripts/db/create-runtime-role.sql (step 2).
--
-- Creates the schema-owner role used by Flyway migrations and the two local
-- databases. Tables, permissions and row-level security are created by the
-- Flyway migrations when the application or the tests first start.
--
-- BEFORE RUNNING: replace CHANGE_ME with a strong password and store it in
-- the DB_PASSWORD user environment variable:
--   [Environment]::SetEnvironmentVariable('DB_PASSWORD','<password>','User')
-- Never commit the real password.
-- ============================================================================

CREATE ROLE transithub_app
    LOGIN
    PASSWORD 'CHANGE_ME'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE;

CREATE DATABASE transithub_dev  OWNER transithub_app;
CREATE DATABASE transithub_test OWNER transithub_app;
