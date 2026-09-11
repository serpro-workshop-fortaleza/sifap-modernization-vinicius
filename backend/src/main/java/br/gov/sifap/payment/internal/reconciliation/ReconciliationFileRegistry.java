package br.gov.sifap.payment.internal.reconciliation;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro do arquivo de retorno em transacao propria.
 *
 * <p>{@code REQUIRES_NEW} e o que torna o {@code REQ-REC-015} possivel. Se a abertura do
 * arquivo compartilhasse a transacao do processamento, uma falha no meio do ciclo
 * desfaria tambem o registro, e a retomada nunca teria de onde partir.
 */
@Service
class ReconciliationFileRegistry {

    private final ReconciliationFileRepository files;
    private final Clock clock;

    ReconciliationFileRegistry(ReconciliationFileRepository files, Clock clock) {
        this.files = files;
        this.clock = clock;
    }

    /**
     * Abre o arquivo, recusando o que ja foi concluido.
     *
     * <p>{@code REQ-REC-003}: um arquivo interrompido pode ser retomado; um concluido, nao.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ReconciliationFile open(String sha256, String declaredName, String referencePeriod, Actor actor) {
        Optional<ReconciliationFile> existing = files.findBySha256(sha256);

        if (existing.filter(ReconciliationFile::isCompleted).isPresent()) {
            ReconciliationFile completed = existing.orElseThrow();
            throw new DomainRuleException(
                    "REQ-REC-003",
                    "arquivo de retorno ja processado em " + completed.processedAt()
                            + "; conteudo identico ao de " + completed.declaredName());
        }

        return existing.orElseGet(() -> files.save(
                ReconciliationFile.start(sha256, declaredName, referencePeriod, actor.id(), clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void complete(Long fileId, int recordsRead) {
        files.findById(fileId).ifPresent(file -> {
            file.complete(recordsRead);
            files.save(file);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void interrupt(Long fileId, int recordsRead) {
        files.findById(fileId).ifPresent(file -> {
            file.interrupt(recordsRead);
            files.save(file);
        });
    }
}
