package br.gov.sifap.shared.event;

import java.util.Objects;

/**
 * Valor anterior e posterior de um campo alterado.
 *
 * <p>Atende {@code REQ-AUD-006}. O dicionario legado reserva dois grupos MU de ate
 * 20 ocorrencias ({@code AUDIT.ddm:61-69}) e nenhum programa os preenche. O limite de
 * 20 e restricao do Adabas, nao regra de negocio, e desaparece na serializacao JSONB.
 *
 * @param before valor anterior; nulo quando o campo esta sendo criado (AC-006.2)
 * @param after valor posterior; nulo quando o campo esta sendo removido
 */
public record Change(String before, String after) {

    public Change {
        if (before == null && after == null) {
            throw new IllegalArgumentException("Change exige ao menos um dos valores");
        }
    }

    public static Change of(String before, String after) {
        return new Change(Objects.requireNonNull(before), Objects.requireNonNull(after));
    }

    public static Change created(String after) {
        return new Change(null, Objects.requireNonNull(after));
    }

    public static Change removed(String before) {
        return new Change(Objects.requireNonNull(before), null);
    }

    public boolean hasPreviousValue() {
        return before != null;
    }
}
