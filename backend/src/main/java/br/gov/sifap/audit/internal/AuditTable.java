package br.gov.sifap.audit.internal;

/** Tabelas particionadas da trilha. */
public enum AuditTable {

    CHANGE("audit_change_event"),
    ACCESS("audit_access_event");

    private final String tableName;

    AuditTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
