-- Imutabilidade da trilha por permissao, nao por convencao. Cobre REQ-AUD-002.
--
-- AUDIT.ddm:14-18 declara IMMUTABLE RECORD - UPDATE/DELETE NOT ALLOWED e nada no
-- legado impede a operacao. Convencao que depende de disciplina foi o que permitiu
-- cinco rotinas de CPF divergentes conviverem por catorze anos.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sifap_app') THEN
        CREATE ROLE sifap_app NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sifap_audit_purge') THEN
        CREATE ROLE sifap_audit_purge NOLOGIN;
    END IF;
END $$;

GRANT SELECT, INSERT ON audit_change_event, audit_access_event TO sifap_app;
GRANT USAGE ON SEQUENCE audit_change_event_id_seq, audit_access_event_id_seq TO sifap_app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_change_event, audit_access_event FROM sifap_app;

GRANT SELECT ON audit_change_event, audit_access_event TO sifap_audit_purge;

-- O expurgo remove particoes, o que exige ownership. Em vez de tornar a role de
-- expurgo dona das tabelas, ela recebe apenas o direito de invocar esta funcao,
-- que so alcanca particoes da trilha e so as vencidas.
CREATE OR REPLACE FUNCTION audit_drop_partitions_before(p_table TEXT, p_before DATE)
RETURNS INT
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_partition RECORD;
    v_dropped   INT := 0;
BEGIN
    IF p_table NOT IN ('audit_change_event', 'audit_access_event') THEN
        RAISE EXCEPTION 'tabela fora da trilha de auditoria: %', p_table;
    END IF;

    FOR v_partition IN
        SELECT child.relname AS name
        FROM pg_inherits i
        JOIN pg_class child ON child.oid = i.inhrelid
        JOIN pg_class parent ON parent.oid = i.inhparent
        WHERE parent.relname = p_table
          AND child.relname ~ ('^' || p_table || '_[0-9]{6}$')
          AND (to_date(right(child.relname, 6), 'YYYYMM') + INTERVAL '1 month')::DATE <= p_before
    LOOP
        EXECUTE format('DROP TABLE %I', v_partition.name);
        v_dropped := v_dropped + 1;
    END LOOP;

    RETURN v_dropped;
END;
$$ LANGUAGE plpgsql;

REVOKE EXECUTE ON FUNCTION audit_drop_partitions_before(TEXT, DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit_drop_partitions_before(TEXT, DATE) TO sifap_audit_purge;

-- Criar particao futura tambem e manutencao de schema, e nao trabalho da role da
-- aplicacao. Fica com a mesma role restrita.
REVOKE EXECUTE ON FUNCTION audit_create_partition(TEXT, DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit_create_partition(TEXT, DATE) TO sifap_audit_purge;
