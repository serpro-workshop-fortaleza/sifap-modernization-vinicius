package br.gov.sifap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * T-009 — fronteiras do monolito modular.
 *
 * <p>Sem estas regras a separacao entre modulos e convencao. O legado mostra o custo:
 * a rotina normativa de CPF existia desde 2005 e cinco copias divergentes conviveram
 * com ela por catorze anos, porque nada impedia a copia.
 */
@AnalyzeClasses(packages = "br.gov.sifap", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule kernel_de_documento_nao_expoe_seu_pacote_interno =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("br.gov.sifap.shared.document..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("br.gov.sifap.shared.document.internal..")
                    .because("modulos consomem o kernel pela interface DocumentValidator "
                            + "e pelos value objects Cpf e Nis, nunca pela implementacao");
}
