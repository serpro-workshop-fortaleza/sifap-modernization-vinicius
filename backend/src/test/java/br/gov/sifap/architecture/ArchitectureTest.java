package br.gov.sifap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

/**
 * T-009 e T-113 — fronteiras do monolito modular.
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

    @ArchTest
    static final ArchRule trilha_de_auditoria_nao_expoe_seu_pacote_interno =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("br.gov.sifap.audit..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("br.gov.sifap.audit.internal..")
                    .because("registrar auditoria e consequencia de alterar dado, nao operacao "
                            + "que um modulo solicita ao repositorio de auditoria");

    @ArchTest
    static final ArchRule auditoria_nao_conhece_os_contextos_publicadores =
            noClasses()
                    .that()
                    .resideInAPackage("br.gov.sifap.audit..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "br.gov.sifap.beneficiary..",
                            "br.gov.sifap.socialprogram..",
                            "br.gov.sifap.payment..")
                    .because("a auditoria consome o contrato AuditableEvent do kernel; conhecer "
                            + "cada publicador recriaria o acoplamento do PERFORM WRITE-AUDIT");

    @ArchTest
    static final ArchRule entidades_jpa_ficam_em_pacote_interno =
            noClasses()
                    .that()
                    .areAnnotatedWith(Entity.class)
                    .should()
                    .resideOutsideOfPackages("..internal..")
                    .because("o modelo de persistencia de um contexto nao atravessa sua fronteira");

    @ArchTest
    static final ArchRule cadastro_nao_expoe_seu_pacote_interno =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("br.gov.sifap.beneficiary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("br.gov.sifap.beneficiary.internal..")
                    .because("o cadastro e alcancado por BeneficiaryQuery, pelos comandos e pelos "
                            + "eventos publicados, nunca pelo agregado nem pelo repositorio");

    @ArchTest
    static final ArchRule cadastro_nao_conhece_os_demais_contextos =
            noClasses()
                    .that()
                    .resideInAPackage("br.gov.sifap.beneficiary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "br.gov.sifap.audit..",
                            "br.gov.sifap.socialprogram..",
                            "br.gov.sifap.payment..")
                    .because("o cadastro publica AuditableEvent e nao conhece quem o consome; "
                            + "conhecer a auditoria recriaria o acoplamento do PERFORM WRITE-AUDIT");

    @ArchTest
    static final ArchRule catalogo_nao_expoe_seu_pacote_interno =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("br.gov.sifap.socialprogram..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("br.gov.sifap.socialprogram.internal..")
                    .because("o calculo consome SocialProgramParameters, nunca o agregado");

    @ArchTest
    static final ArchRule catalogo_nao_conhece_os_demais_contextos =
            noClasses()
                    .that()
                    .resideInAPackage("br.gov.sifap.socialprogram..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "br.gov.sifap.audit..",
                            "br.gov.sifap.beneficiary..",
                            "br.gov.sifap.payment..")
                    .because("o catalogo e escritor unico do seu dado e nao le nenhum outro contexto");

    @ArchTest
    static final ArchRule folha_nao_expoe_seu_pacote_interno =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("br.gov.sifap.payment..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("br.gov.sifap.payment.internal..")
                    .because("a folha e alcancada por PaymentQuery e pela API REST, nunca pelo "
                            + "agregado nem pelo repositorio");

    @ArchTest
    static final ArchRule folha_nao_conhece_a_auditoria =
            noClasses()
                    .that()
                    .resideInAPackage("br.gov.sifap.payment..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("br.gov.sifap.audit..")
                    .because("a folha publica AuditableEvent; conhecer a auditoria repetiria a "
                            + "omissao de CALCDSCT, que altera valor financeiro sem incluir CCAUDIT");

    @ArchTest
    static final ArchRule folha_consome_os_demais_contextos_apenas_pelas_interfaces_publicas =
            noClasses()
                    .that()
                    .resideInAPackage("br.gov.sifap.payment..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "br.gov.sifap.beneficiary.internal..",
                            "br.gov.sifap.socialprogram.internal..")
                    .because("o calculo le o cadastro por BeneficiaryPayrollFeed e o catalogo por "
                            + "SocialProgramQuery; alcancar o agregado alheio traria o modelo junto");

    @ArchTest
    static final ArchRule conciliacao_e_escritora_do_mesmo_agregado_da_folha =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("br.gov.sifap.payment..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("br.gov.sifap.payment.internal.reconciliation..")
                    .because("a conciliacao vive dentro de payment porque escreve no agregado "
                            + "Payment; contexto separado repetiria o antipadrao de dois "
                            + "escritores do mesmo dado");
}
