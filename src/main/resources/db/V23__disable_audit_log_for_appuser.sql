DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'sykmeldinger-kafka-user')
        THEN
            ALTER USER "sykmeldinger-kafka-user" IN DATABASE "sykmeldinger" SET pgaudit.log TO 'none';
        END IF;
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'sykmeldinger-db-instance')
        THEN
            ALTER USER "sykmeldinger-db-instance" IN DATABASE "sykmeldinger" SET pgaudit.log TO 'none';
        END IF;
    END
$$;
