package br.gov.sifap.audit;

import java.time.Instant;
import java.util.List;

/**
 * Interface publica de leitura da trilha de auditoria.
 *
 * <p>Cobre {@code REQ-AUD-012}. As consultas usam o indice por entidade e data,
 * equivalente ao superdescritor que {@code AUDIT.ddm:102} manda usar em consulta pesada.
 *
 * <p>Nenhum metodo filtra a acao por conta propria. A omissao de exclusoes que
 * {@code RELAUDIT.NSP:128-134} pratica e do relatorio, nao da consulta, e o
 * {@code REQ-AUD-011} a corrige na Fatia 5.
 */
public interface AuditQuery {

    List<AuditEventView> findChangesByEntity(String entityType, String entityId, Instant from, Instant to);

    List<AuditEventView> findChangesBySubject(String subjectCpf, Instant from, Instant to);

    List<AuditEventView> findAccessesBySubject(String subjectCpf, Instant from, Instant to);

    List<AuditEventView> findChangesByBatchRun(String batchRunId);
}
