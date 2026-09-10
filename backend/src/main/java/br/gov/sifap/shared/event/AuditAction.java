package br.gov.sifap.shared.event;

/**
 * Acoes registraveis na trilha de auditoria.
 *
 * <p>Atende {@code REQ-AUD-008}. No legado, {@code CO} significa consulta no dicionario,
 * e gravado como consulta pelo {@code CONSBENF} desde 2016 e responde por 193,8 milhoes
 * de registros de conciliacao entre 2014 e 2018 — tres significados no mesmo campo.
 * Aqui cada acao tem codigo proprio.
 */
public enum AuditAction {

    INCLUSAO(false),
    ALTERACAO(false),
    EXCLUSAO(false),
    CONCILIACAO(false),
    PROCESSAMENTO(false),

    /** Unica acao roteada para a trilha de acesso, com retencao propria. */
    CONSULTA(true);

    private final boolean accessToPersonalData;

    AuditAction(boolean accessToPersonalData) {
        this.accessToPersonalData = accessToPersonalData;
    }

    public boolean isAccessToPersonalData() {
        return accessToPersonalData;
    }
}
