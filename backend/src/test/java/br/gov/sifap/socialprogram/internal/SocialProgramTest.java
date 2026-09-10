package br.gov.sifap.socialprogram.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.Change;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.socialprogram.RegisterSocialProgramCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand.BandData;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand.RegionData;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.UpdateSocialProgramCommand;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** T-303 a T-309 — cobre {@code REQ-PRG-002} a {@code REQ-PRG-011}. */
class SocialProgramTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
    private static final Actor OPERATOR = Actor.human("op.ribeiro", "GESTOR");

    @Nested
    @DisplayName("criacao")
    class Creation {

        @Test
        @DisplayName("deve criar o programa ativo quando os dados sao validos")
        void deve_criar_o_programa_ativo() {
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            assertThat(program.code()).isEqualTo("0001");
            assertThat(program.status()).isEqualTo(SocialProgramStatus.ATIVO);
            assertThat(program.amountBase()).isEqualByComparingTo("600.00");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("deve exigir o nome quando o programa e incluido")
        void deve_exigir_o_nome(String name) {
            // REQ-PRG-002 / AC-002.1. CADPROG.NSP:96-106 grava tudo que a tela devolve.
            assertThatThrownBy(() -> SocialProgram.register(
                            command("0001", name, SocialProgramType.ASSISTENCIA,
                                    new BigDecimal("600.00"), 0, 0),
                            OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-PRG-002");
        }

        @Test
        @DisplayName("deve exigir o tipo quando o programa e incluido")
        void deve_exigir_o_tipo() {
            // REQ-PRG-002 / AC-002.2
            assertThatThrownBy(() -> SocialProgram.register(
                            command("0001", "Bolsa Exemplo", null, new BigDecimal("600.00"), 0, 0),
                            OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class);
        }

        @ParameterizedTest(name = "valor base {0} e recusado")
        @ValueSource(strings = {"0.00", "-1.00"})
        @DisplayName("deve recusar valor base nao positivo")
        void deve_recusar_valor_base_nao_positivo(String amount) {
            // REQ-PRG-002 / AC-002.3
            assertThatThrownBy(() -> register(new BigDecimal(amount), 0, 0))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("maior que zero");
        }

        @Test
        @DisplayName("deve recusar faixa etaria invertida")
        void deve_recusar_faixa_etaria_invertida() {
            // REQ-PRG-003 / AC-003.1. Uma faixa invertida gravada torna todos os
            // candidatos inelegiveis, e VALELEG.NSN:153-169 nao distingue isso de regra.
            assertThatThrownBy(() -> register(new BigDecimal("600.00"), 65, 18))
                    .isInstanceOf(DomainRuleException.class)
                    .extracting(e -> ((DomainRuleException) e).requirementId())
                    .isEqualTo("REQ-PRG-003");
        }

        @ParameterizedTest(name = "faixa {0}-{1} e aceita")
        @CsvSource({"0, 0", "0, 65", "18, 0", "18, 65"})
        @DisplayName("deve aceitar zero como ausencia de limite etario")
        void deve_aceitar_zero_como_ausencia_de_limite(int min, int max) {
            // REQ-PRG-003 / AC-003.2. Zero significa sem limite (SOCPROG.ddm:57-58).
            SocialProgram program = register(new BigDecimal("600.00"), min, max);

            assertThat(program.ageMin()).isEqualTo(min);
            assertThat(program.ageMax()).isEqualTo(max);
        }

        @Test
        @DisplayName("deve registrar autor e instante quando o programa e criado")
        void deve_registrar_autor_e_instante() {
            // REQ-PRG-013. SOCPROG.ddm:95-98 declara os campos desde 1997, nunca preenchidos.
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            assertThat(program.createdBy()).isEqualTo("op.ribeiro");
            assertThat(program.createdAt()).isEqualTo(CLOCK.instant());
        }
    }

    @Nested
    @DisplayName("valor base e fator")
    class AmountAndFactor {

        @Test
        @DisplayName("deve preservar o valor base informado")
        void deve_preservar_o_valor_base_informado() {
            // REQ-PRG-004 / AC-004.1. CADPROG.NSP:125 calcula #AMT-CALC e :130 grava esse
            // resultado no campo de valor base: quem consulta ve um numero que ninguem digitou.
            SocialProgram program = SocialProgram.register(
                    new RegisterSocialProgramCommand(
                            "0001", "Bolsa Exemplo", "BEX", SocialProgramType.ASSISTENCIA,
                            new BigDecimal("600.00"), new BigDecimal("2.0000"),
                            null, 0, 0, null, null, LocalDate.of(2020, 1, 1)),
                    OPERATOR, CLOCK);

            assertThat(program.amountBase()).isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("deve reproduzir o fator derivado do legado")
        void deve_reproduzir_o_fator_derivado_do_legado() {
            // REQ-PRG-005 / AC-005.1. CADPROG.NSP:124 calcula 1.00 + (FACTOR-ADJUST * 0.347215).
            // O coeficiente e preservado; o que muda e onde ele mora.
            SocialProgram program = SocialProgram.register(
                    new RegisterSocialProgramCommand(
                            "0001", "Bolsa Exemplo", null, SocialProgramType.ASSISTENCIA,
                            new BigDecimal("600.00"), new BigDecimal("2.0000"),
                            null, 0, 0, null, null, LocalDate.of(2020, 1, 1)),
                    OPERATOR, CLOCK);

            BigDecimal derived = program.derivedFactor(new BigDecimal("0.347215"));

            assertThat(derived).isEqualByComparingTo("1.6944300");
        }
    }

    @Nested
    @DisplayName("alteracao e situacao")
    class StatusAndUpdate {

        @Test
        @DisplayName("deve devolver os campos alterados quando o programa e atualizado")
        void deve_devolver_os_campos_alterados() {
            // REQ-PRG-006 / AC-006.2. Operacao que o legado nao possui.
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            Map<String, Change> changes = program.update(
                    new UpdateSocialProgramCommand(
                            "Bolsa Reajustada", null, new BigDecimal("650.00"),
                            null, null, 0, 0, null),
                    OPERATOR, CLOCK);

            assertThat(changes)
                    .containsEntry("name", new Change("Bolsa Exemplo", "Bolsa Reajustada"))
                    .containsEntry("amountBase", new Change("600.00", "650.00"));
        }

        @Test
        @DisplayName("deve preservar a situacao quando os parametros sao alterados")
        void deve_preservar_a_situacao_na_alteracao() {
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);
            program.changeStatus(SocialProgramStatus.INATIVO, "revisao", null, OPERATOR, CLOCK);

            program.update(
                    new UpdateSocialProgramCommand(
                            "Bolsa Exemplo", null, new BigDecimal("650.00"), null, null, 0, 0, null),
                    OPERATOR, CLOCK);

            assertThat(program.status()).isEqualTo(SocialProgramStatus.INATIVO);
        }

        @Test
        @DisplayName("deve exigir data quando o programa e encerrado")
        void deve_exigir_data_quando_encerrado() {
            // REQ-PRG-007 / AC-007.1
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            assertThatThrownBy(() -> program.changeStatus(
                            SocialProgramStatus.ENCERRADO, "fim do programa", null, OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("data de encerramento");
        }

        @Test
        @DisplayName("deve recusar qualquer transicao a partir de encerrado")
        void deve_recusar_transicao_a_partir_de_encerrado() {
            // Encerrado e terminal, ancorado em SOCPROG.ddm:36 (DT-CLOSURE com 0=ACTIVE).
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);
            program.changeStatus(
                    SocialProgramStatus.ENCERRADO, "fim", LocalDate.of(2026, 1, 1), OPERATOR, CLOCK);

            assertThatThrownBy(() -> program.changeStatus(
                            SocialProgramStatus.ATIVO, "reativacao", null, OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("nao e permitida");
        }

        @Test
        @DisplayName("deve permitir reativar programa inativo")
        void deve_permitir_reativar_programa_inativo() {
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);
            program.changeStatus(SocialProgramStatus.INATIVO, "suspensao", null, OPERATOR, CLOCK);
            program.changeStatus(SocialProgramStatus.ATIVO, "retomada", null, OPERATOR, CLOCK);

            assertThat(program.status()).isEqualTo(SocialProgramStatus.ATIVO);
        }

        @Test
        @DisplayName("deve recusar alteracao de programa encerrado")
        void deve_recusar_alteracao_de_programa_encerrado() {
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);
            program.changeStatus(
                    SocialProgramStatus.ENCERRADO, "fim", LocalDate.of(2026, 1, 1), OPERATOR, CLOCK);

            assertThatThrownBy(() -> program.update(
                            new UpdateSocialProgramCommand(
                                    "Outro", null, new BigDecimal("1.00"), null, null, 0, 0, null),
                            OPERATOR, CLOCK))
                    .isInstanceOf(DomainRuleException.class);
        }
    }

    @Nested
    @DisplayName("parametrizacao")
    class Parameters {

        @Test
        @DisplayName("deve aceitar faixas contiguas")
        void deve_aceitar_faixas_contiguas() {
            // REQ-PRG-010 / AC-010.1
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            program.replaceBands(new ReplaceCalculationBandsCommand(List.of(
                    band("0.00", "500.00", "1.0000"),
                    band("500.00", "1000.00", "0.8500"),
                    band("1000.00", null, "0.7000"))));

            assertThat(program.bands()).hasSize(3);
        }

        @Test
        @DisplayName("deve recusar faixas sobrepostas")
        void deve_recusar_faixas_sobrepostas() {
            // REQ-PRG-010 / AC-010.2. Invariante do agregado: sobreposicao exige comparar
            // pares, o que nenhuma restricao declarativa expressa.
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            assertThatThrownBy(() -> program.replaceBands(
                            new ReplaceCalculationBandsCommand(List.of(
                                    band("0.00", "600.00", "1.0000"),
                                    band("500.00", "1000.00", "0.8500")))))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("sobrepoem");
        }

        @Test
        @DisplayName("deve recusar duas faixas abertas no topo")
        void deve_recusar_duas_faixas_abertas_no_topo() {
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            assertThatThrownBy(() -> program.replaceBands(
                            new ReplaceCalculationBandsCommand(List.of(
                                    band("0.00", null, "1.0000"),
                                    band("500.00", null, "0.8500")))))
                    .isInstanceOf(DomainRuleException.class);
        }

        @Test
        @DisplayName("deve recusar mais faixas que o limite do grupo periodico")
        void deve_recusar_mais_de_cinco_faixas() {
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);
            List<BandData> six = List.of(
                    band("0.00", "100.00", "1.0"), band("100.00", "200.00", "1.0"),
                    band("200.00", "300.00", "1.0"), band("300.00", "400.00", "1.0"),
                    band("400.00", "500.00", "1.0"), band("500.00", "600.00", "1.0"));

            assertThatThrownBy(() ->
                            program.replaceBands(new ReplaceCalculationBandsCommand(six)))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("maximo 5");
        }

        @Test
        @DisplayName("deve aceitar as seis regioes do dicionario")
        void deve_aceitar_as_seis_regioes() {
            // REQ-PRG-011. SOCPROG.ddm:89-90 declara 01-05 e 99.
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            program.replaceRegions(new ReplaceRegionalParametersCommand(List.of(
                    region("01"), region("02"), region("03"),
                    region("04"), region("05"), region("99"))));

            assertThat(program.regions()).hasSize(6);
        }

        @ParameterizedTest(name = "regiao \"{0}\" e recusada")
        @ValueSource(strings = {"06", "00", "ZZ", "1"})
        @DisplayName("deve recusar codigo de regiao fora do dominio")
        void deve_recusar_regiao_fora_do_dominio(String region) {
            // REQ-PRG-011 / AC-011.2
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            assertThatThrownBy(() -> program.replaceRegions(
                            new ReplaceRegionalParametersCommand(List.of(region(region)))))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("fora do dominio");
        }

        @Test
        @DisplayName("deve recusar regiao repetida no mesmo programa")
        void deve_recusar_regiao_repetida() {
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);

            assertThatThrownBy(() -> program.replaceRegions(
                            new ReplaceRegionalParametersCommand(List.of(region("01"), region("01")))))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("repetida");
        }

        @Test
        @DisplayName("deve substituir o conjunto inteiro quando as faixas sao trocadas")
        void deve_substituir_o_conjunto_inteiro() {
            SocialProgram program = register(new BigDecimal("600.00"), 0, 0);
            program.replaceBands(new ReplaceCalculationBandsCommand(List.of(
                    band("0.00", "500.00", "1.0000"), band("500.00", null, "0.8500"))));

            program.replaceBands(new ReplaceCalculationBandsCommand(List.of(
                    band("0.00", null, "1.0000"))));

            assertThat(program.bands()).hasSize(1);
        }
    }

    // ------------------------------------------------------------- auxiliares

    private static SocialProgram register(BigDecimal amount, int ageMin, int ageMax) {
        return SocialProgram.register(
                command("0001", "Bolsa Exemplo", SocialProgramType.ASSISTENCIA, amount, ageMin, ageMax),
                OPERATOR,
                CLOCK);
    }

    private static RegisterSocialProgramCommand command(
            String code, String name, SocialProgramType type, BigDecimal amount, int ageMin, int ageMax) {
        return new RegisterSocialProgramCommand(
                code, name, "BEX", type, amount, BigDecimal.ZERO,
                new BigDecimal("300.00"), ageMin, ageMax, "RD   ", "Lei 10836",
                LocalDate.of(2020, 1, 1));
    }

    private static BandData band(String from, String to, String multiplier) {
        return new BandData(
                new BigDecimal(from),
                to == null ? null : new BigDecimal(to),
                new BigDecimal(multiplier),
                BigDecimal.ZERO,
                false);
    }

    private static RegionData region(String code) {
        return new RegionData(code, new BigDecimal("1.2000"), BigDecimal.ZERO, true);
    }
}
