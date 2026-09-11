package br.gov.sifap.payment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Indice de correcao de um mes.
 *
 * <p>Atende {@code REQ-PAY-021}. O legado guarda a serie em
 * {@code CALC-INDEX-ACCUM(1:120)} ({@code CALCCORR.NSP:78}), array de dez anos gravado no
 * codigo-fonte: alem da janela, o indice fica {@code 1.000000} e a correcao resulta em
 * zero, indistinguivel de nao haver correcao devida.
 */
@Entity
@Table(name = "correction_index")
class CorrectionIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "correctionIndexSeq")
    @SequenceGenerator(
            name = "correctionIndexSeq",
            sequenceName = "correction_index_id_seq",
            allocationSize = 50)
    private Long id;

    @Column(name = "period", nullable = false, length = 6)
    private String period;

    @Column(name = "rate", nullable = false, precision = 11, scale = 6)
    private BigDecimal rate;

    @Column(name = "source", nullable = false, length = 120)
    private String source;

    protected CorrectionIndex() {
        // exigido pelo JPA
    }

    CorrectionIndex(String period, BigDecimal rate, String source) {
        this.period = period;
        this.rate = rate;
        this.source = source;
    }

    String period() {
        return period;
    }

    BigDecimal rate() {
        return rate;
    }

    String source() {
        return source;
    }
}
