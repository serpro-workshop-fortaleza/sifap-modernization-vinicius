package br.gov.sifap.shared.event;

import java.util.Objects;

/**
 * Autor de uma operacao auditavel.
 *
 * <p>Atende {@code REQ-AUD-005}. O perfil e obrigatorio: no legado o campo existe no
 * dicionario desde 2005 ({@code AUDIT.ddm:74-76}) e nenhum programa o preenche, o que
 * torna a {@code RN-009} — alteracao de CPF exige autorizacao de supervisor —
 * inauditavel.
 *
 * @param id identificador do operador ou do processo de origem
 * @param profile perfil sob o qual a operacao foi executada
 * @param type natureza do autor
 */
public record Actor(String id, String profile, ActorType type) {

    private static final String SYSTEM_PROFILE = "SISTEMA";

    public Actor {
        requireText(id, "id");
        requireText(profile, "profile");
        Objects.requireNonNull(type, "type");
    }

    public static Actor human(String id, String profile) {
        return new Actor(id, profile, ActorType.HUMANO);
    }

    /** Atende AC-005.2: operacao sem usuario interativo identifica o processo de origem. */
    public static Actor process(String processId) {
        return new Actor(processId, SYSTEM_PROFILE, ActorType.PROCESSO);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Actor." + field + " e obrigatorio");
        }
    }
}
