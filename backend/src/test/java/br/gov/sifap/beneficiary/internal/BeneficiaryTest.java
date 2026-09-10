package br.gov.sifap.beneficiary.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.beneficiary.AddDependentCommand;
import br.gov.sifap.beneficiary.AddressData;
import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.beneficiary.RegisterBeneficiaryCommand;
import br.gov.sifap.beneficiary.Relationship;
import br.gov.sifap.beneficiary.Sex;
import br.gov.sifap.beneficiary.UpdateBeneficiaryCommand;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.Change;
import br.gov.sifap.shared.exception.DomainRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** T-202 a T-208 — cobre {@code REQ-BEN-002} a {@code REQ-BEN-014} e {@code REQ-BEN-018}. */
class BeneficiaryTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
    private static final Actor OPERATOR = Actor.human("op.silva", "SUPERVISOR");

    private static final String VALID_CPF = "11144477735";
    private static final LocalDate ADULT_BIRTH = LocalDate.of(1990, 5, 20);
    private static final LocalDate ELDERLY_BIRTH = LocalDate.of(1940, 5, 20);

    @Nested
    @DisplayName("criacao")
    class Creation {

        @Test
        @DisplayName("deve criar o beneficiario ativo quando os dados sao validos")
        void deve_criar_o_beneficiario_ativo() {
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);

            assertThat(beneficiary.cpf()).isEqualTo(VALID_CPF);
            assertThat(beneficiary.status()).contains(BeneficiaryStatus.ATIVO);
            assertThat(beneficiary.ageAt(LocalDate.now(CLOCK))).hasValue(36);
        }

        @ParameterizedTest(name = "CPF \"{0}\" impede a construcao")
        @ValueSource(strings = {"11111111111", "00000000000", "1234567890A", "12345678908"})
        @DisplayName("deve impedir a construcao quando o CPF e invalido")
        void deve_impedir_a_construcao_quando_o_cpf_e_invalido(String cpf) {
            // REQ-BEN-003. Um Beneficiary que existe e valido; nao ha o que revalidar depois.
            assertThatThrownBy(() -> register(cpf, ADULT_BIRTH))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-BEN-003");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("deve exigir o nome quando o beneficiario e incluido")
        void deve_exigir_o_nome(String name) {
            // REQ-BEN-002 / AC-002.1
            assertThatThrownBy(() -> Beneficiary.register(
                            command(VALID_CPF, name, ADULT_BIRTH, Sex.F, "SP"), OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-BEN-002");
        }

        @Test
        @DisplayName("deve exigir nome e sobrenome quando o nome tem uma palavra so")
        void deve_exigir_nome_e_sobrenome() {
            // VALBENEF.NSN:316-331 aproxima a regra por ao menos um espaco interno.
            assertThatThrownBy(() -> Beneficiary.register(
                            command(VALID_CPF, "Maria", ADULT_BIRTH, Sex.F, "SP"), OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("sobrenome");
        }

        @Test
        @DisplayName("deve exigir a data de nascimento quando ela nao e informada")
        void deve_exigir_a_data_de_nascimento() {
            assertThatThrownBy(() -> Beneficiary.register(
                            command(VALID_CPF, "Maria Silva", null, Sex.F, "SP"), OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("data de nascimento");
        }

        @ParameterizedTest(name = "ano {0} e recusado")
        @CsvSource({"1899", "2027"})
        @DisplayName("deve recusar a data de nascimento quando o ano esta fora da faixa")
        void deve_recusar_ano_fora_da_faixa(int year) {
            // Faixa de VALBENEF.NSN:302, que compara apenas o ano.
            LocalDate birthDate = LocalDate.of(year, 1, 15);
            assertThatThrownBy(() -> register(VALID_CPF, birthDate))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("faixa");
        }

        @Test
        @DisplayName("deve recusar sexo indefinido quando o beneficiario e incluido")
        void deve_recusar_sexo_indefinido() {
            // REQ-BEN-002 / AC-002.2. O dicionario admite I; CADBENEF.NSP:182-186 nao.
            assertThatThrownBy(() -> Beneficiary.register(
                            command(VALID_CPF, "Maria Silva", ADULT_BIRTH, Sex.I, "SP"),
                            OPERATOR,
                            CLOCK))
                    .isInstanceOf(DomainRuleException.class);
        }

        @Test
        @DisplayName("deve recusar unidade federativa fora da tabela")
        void deve_recusar_uf_fora_da_tabela() {
            assertThatThrownBy(() -> Beneficiary.register(
                            command(VALID_CPF, "Maria Silva", ADULT_BIRTH, Sex.F, "XX"),
                            OPERATOR,
                            CLOCK))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("federativa");
        }

        @Test
        @DisplayName("deve registrar autor e instante quando o beneficiario e criado")
        void deve_registrar_autor_e_instante() {
            // REQ-BEN-019 / AC-019.1. BENEFIC.ddm:112-118 declara os campos desde 1997
            // e nenhum programa os preenche.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);

            assertThat(beneficiary.createdBy()).isEqualTo("op.silva");
            assertThat(beneficiary.createdAt()).isEqualTo(CLOCK.instant());
            assertThat(beneficiary.updatedBy()).isEqualTo("op.silva");
        }

        @Test
        @DisplayName("deve preservar o endereco integralmente quando ele e longo")
        void deve_preservar_o_endereco_integralmente() {
            // REQ-BEN-018 / AC-018.1. CADBENEF.NSP:277-279 grava A80 em campo A60.
            String longStreet = "Avenida ".repeat(14).trim();
            AddressData address = new AddressData(
                    longStreet, "1000", "Bloco B", "Centro", "Sao Paulo", "SP", "01310100", "35");

            Beneficiary beneficiary = Beneficiary.register(
                    new RegisterBeneficiaryCommand(
                            VALID_CPF, null, "Maria Silva", ADULT_BIRTH, Sex.F,
                            "0001", BigDecimal.TEN, address, null, null),
                    OPERATOR,
                    CLOCK);

            assertThat(beneficiary.address().street()).isEqualTo(longStreet).hasSizeGreaterThan(60);
            assertThat(beneficiary.address().complement()).isEqualTo("Bloco B");
        }
    }

    @Nested
    @DisplayName("situacao cadastral")
    class Status {

        @Test
        @DisplayName("deve preservar a situacao quando dados cadastrais sao alterados")
        void deve_preservar_a_situacao_na_alteracao() {
            // REQ-BEN-005 / AC-005.1. O comando nem possui campo de situacao.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);
            beneficiary.changeStatus(BeneficiaryStatus.SUSPENSO, "revisao cadastral", OPERATOR, CLOCK);

            beneficiary.update(
                    new UpdateBeneficiaryCommand(
                            "Maria Silva Souza", "0001", BigDecimal.ONE, null, "11999990000", null),
                    OPERATOR,
                    CLOCK);

            assertThat(beneficiary.status()).contains(BeneficiaryStatus.SUSPENSO);
        }

        @Test
        @DisplayName("deve devolver os campos alterados quando a alteracao e aplicada")
        void deve_devolver_os_campos_alterados() {
            // REQ-AUD-006: a estrutura existe em AUDIT.ddm:61-69 desde 2005, nunca preenchida.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);

            Map<String, Change> changes = beneficiary.update(
                    new UpdateBeneficiaryCommand(
                            "Maria Silva Souza", "0002", new BigDecimal("1450.00"), null, null, null),
                    OPERATOR,
                    CLOCK);

            assertThat(changes)
                    .containsEntry("fullName", new Change("Maria Silva", "Maria Silva Souza"))
                    .containsKey("programCode")
                    .containsKey("familyIncome");
        }

        @Test
        @DisplayName("deve nao registrar alteracao quando nada muda")
        void deve_nao_registrar_alteracao_quando_nada_muda() {
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);

            Map<String, Change> changes = beneficiary.update(
                    new UpdateBeneficiaryCommand(
                            "Maria Silva", "0001", BigDecimal.TEN, sameAddress(), null, null),
                    OPERATOR,
                    CLOCK);

            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("deve exigir motivo quando a situacao muda")
        void deve_exigir_motivo_quando_a_situacao_muda() {
            // REQ-BEN-006. E o motivo que permitira derivar dos dados quais transicoes ocorrem.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);

            assertThatThrownBy(() ->
                            beneficiary.changeStatus(BeneficiaryStatus.CANCELADO, "  ", OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-BEN-006");
        }

        @Test
        @DisplayName("deve suspender automaticamente quando o beneficiario ativo passa de 75 anos")
        void deve_suspender_automaticamente_acima_de_75_anos() {
            // REQ-BEN-006, nivel PS. CADBENEF.NSP:250-251.
            Beneficiary beneficiary = register(VALID_CPF, ELDERLY_BIRTH);

            assertThat(beneficiary.applyAgeBasedSuspension(OPERATOR, CLOCK))
                    .contains(BeneficiaryStatus.ATIVO);
            assertThat(beneficiary.status()).contains(BeneficiaryStatus.SUSPENSO);
            assertThat(beneficiary.statusReason()).get().asString().contains("75");
        }

        @Test
        @DisplayName("deve nao suspender quando o beneficiario tem ate 75 anos")
        void deve_nao_suspender_ate_75_anos() {
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);

            assertThat(beneficiary.applyAgeBasedSuspension(OPERATOR, CLOCK)).isEmpty();
            assertThat(beneficiary.status()).contains(BeneficiaryStatus.ATIVO);
        }

        @Test
        @DisplayName("deve nao promover cancelado a suspenso quando a idade passa de 75 anos")
        void deve_nao_promover_cancelado_a_suspenso() {
            // CADBENEF.NSP:251 atribui S qualquer que seja a situacao anterior, de modo que
            // alterar o cadastro de um cancelado idoso o promove a suspenso.
            Beneficiary beneficiary = register(VALID_CPF, ELDERLY_BIRTH);
            beneficiary.changeStatus(BeneficiaryStatus.CANCELADO, "decisao judicial", OPERATOR, CLOCK);

            assertThat(beneficiary.applyAgeBasedSuspension(OPERATOR, CLOCK)).isEmpty();
            assertThat(beneficiary.status()).contains(BeneficiaryStatus.CANCELADO);
        }

        @Test
        @DisplayName("deve calcular a idade pela diferenca de anos civis")
        void deve_calcular_a_idade_por_ano_civil() {
            // REQ-BEN-007 / AC-007.1, nivel PS. CADBENEF.NSP:242 ignora mes e dia:
            // a idade muda em 1o de janeiro, nao no aniversario.
            Beneficiary december = register(VALID_CPF, LocalDate.of(1990, 12, 31));

            assertThat(december.ageAt(LocalDate.of(2026, 1, 1))).hasValue(36);
        }
    }

    @Nested
    @DisplayName("dependentes")
    class Dependents {

        @Test
        @DisplayName("deve aceitar o sexto dependente e recusar o setimo")
        void deve_aceitar_o_sexto_e_recusar_o_setimo() {
            // REQ-BEN-008 / AC-008.1 e AC-008.2. CADDEPEN.NSP:117 testa > 5.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);
            for (int i = 0; i < 6; i++) {
                beneficiary.addDependent(dependent(null, "Dependente " + i));
            }

            assertThat(beneficiary.activeDependentCount()).isEqualTo(6);
            assertThatThrownBy(() -> beneficiary.addDependent(dependent(null, "Setimo")))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-BEN-008");
        }

        @Test
        @DisplayName("deve derivar a contagem quando um dependente e inativado")
        void deve_derivar_a_contagem_quando_um_dependente_e_inativado() {
            // REQ-BEN-009 / AC-009.1. CADDEPEN.NSP:192 incrementa um contador armazenado
            // sem consultar a situacao de ninguem.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);
            Dependent first = beneficiary.addDependent(dependent(null, "Ana Souza"));
            beneficiary.addDependent(dependent(null, "Bruno Souza"));

            first.deactivate();

            assertThat(beneficiary.activeDependentCount()).isEqualTo(1);
            assertThat(beneficiary.dependents()).hasSize(2);
        }

        @Test
        @DisplayName("deve recusar dependente com CPF ja vinculado ao titular")
        void deve_recusar_cpf_ja_vinculado() {
            // REQ-BEN-012 / AC-012.1
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);
            beneficiary.addDependent(dependent("52998224725", "Ana Souza"));

            assertThatThrownBy(() -> beneficiary.addDependent(dependent("52998224725", "Outro Nome")))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-BEN-012");
        }

        @Test
        @DisplayName("deve recusar dependente com CPF invalido")
        void deve_recusar_cpf_de_dependente_invalido() {
            // REQ-BEN-013 / AC-013.1. CADDEPEN.NSP:170 monta a mensagem e a descarta.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);

            assertThatThrownBy(() -> beneficiary.addDependent(dependent("11111111111", "Ana Souza")))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-BEN-013");
        }

        @Test
        @DisplayName("deve aceitar varios dependentes sem CPF no mesmo titular")
        void deve_aceitar_varios_dependentes_sem_cpf() {
            // REQ-BEN-011 / AC-011.2. O legado usa onze zeros e exclui esse valor da
            // verificacao de duplicidade; aqui a ausencia e ausencia.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);
            beneficiary.addDependent(dependent(null, "Ana Souza"));
            beneficiary.addDependent(dependent("00000000000", "Bruno Souza"));

            assertThat(beneficiary.activeDependentCount()).isEqualTo(2);
            assertThat(beneficiary.dependents()).allSatisfy(d -> assertThat(d.cpf()).isEmpty());
        }

        @Test
        @DisplayName("deve recusar dependente quando o titular esta cancelado ou desligado")
        void deve_recusar_dependente_em_titular_cancelado() {
            // REQ-BEN-014 / AC-014.1. CADDEPEN.NSP:110
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);
            beneficiary.changeStatus(BeneficiaryStatus.CANCELADO, "decisao judicial", OPERATOR, CLOCK);

            assertThatThrownBy(() -> beneficiary.addDependent(dependent(null, "Ana Souza")))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-BEN-014");
        }

        @Test
        @DisplayName("deve aceitar dependente quando o titular esta suspenso")
        void deve_aceitar_dependente_em_titular_suspenso() {
            // REQ-BEN-014 / AC-014.2. O teste legado cobre apenas C e D.
            Beneficiary beneficiary = register(VALID_CPF, ADULT_BIRTH);
            beneficiary.changeStatus(BeneficiaryStatus.SUSPENSO, "revisao", OPERATOR, CLOCK);

            beneficiary.addDependent(dependent(null, "Ana Souza"));

            assertThat(beneficiary.activeDependentCount()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------- auxiliares

    private static Beneficiary register(String cpf, LocalDate birthDate) {
        return Beneficiary.register(
                command(cpf, "Maria Silva", birthDate, Sex.F, "SP"), OPERATOR, CLOCK);
    }

    private static RegisterBeneficiaryCommand command(
            String cpf, String name, LocalDate birthDate, Sex sex, String uf) {
        return new RegisterBeneficiaryCommand(
                cpf,
                null,
                name,
                birthDate,
                sex,
                "0001",
                BigDecimal.TEN,
                new AddressData("Rua A", "10", null, "Centro", "Sao Paulo", uf, "01310100", "35"),
                null,
                null);
    }

    private static AddDependentCommand dependent(String cpf, String name) {
        return new AddDependentCommand(cpf, name, LocalDate.of(2015, 3, 1), Relationship.FI, false);
    }

    private static AddressData sameAddress() {
        return new AddressData(
                "Rua A", "10", null, "Centro", "Sao Paulo", "SP", "01310100", "35");
    }
}
