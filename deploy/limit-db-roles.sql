\set ON_ERROR_STOP on
\getenv app_password BREAKGROUND_APP_DB_PASSWORD
\getenv flyway_password BREAKGROUND_FLYWAY_DB_PASSWORD

BEGIN;
SELECT 'CREATE ROLE breakground_app'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'breakground_app') \gexec
SELECT 'CREATE ROLE breakground_flyway'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'breakground_flyway') \gexec

ALTER ROLE breakground_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';
ALTER ROLE breakground_flyway LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'flyway_password';

GRANT USAGE ON SCHEMA public TO breakground_app;
GRANT USAGE, CREATE ON SCHEMA public TO breakground_flyway;
ALTER TYPE public.user_status OWNER TO breakground_flyway;
ALTER TABLE public.flyway_schema_history OWNER TO breakground_flyway;
ALTER TABLE public.room OWNER TO breakground_flyway;
ALTER TABLE public.anonymous_user OWNER TO breakground_flyway;
ALTER TABLE public.message OWNER TO breakground_flyway;
ALTER TABLE public.report OWNER TO breakground_flyway;

GRANT SELECT ON public.room TO breakground_app;
GRANT SELECT, INSERT, UPDATE ON public.anonymous_user TO breakground_app;
GRANT SELECT, INSERT, DELETE ON public.message, public.report TO breakground_app;
GRANT USAGE ON SEQUENCE public.anonymous_user_id_seq, public.report_id_seq TO breakground_app;
COMMIT;
