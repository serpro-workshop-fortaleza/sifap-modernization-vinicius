package br.gov.sifap.audit;

import br.gov.sifap.support.AbstractIntegrationTest;

/**
 * Base dos testes de integracao da trilha.
 *
 * <p>Mantida como ponto de extensao proprio do contexto; o container PostgreSQL e
 * compartilhado com as demais fatias em {@link AbstractIntegrationTest}, para que uma
 * execucao completa suba um unico banco.
 */
abstract class AbstractAuditIT extends AbstractIntegrationTest {
}
