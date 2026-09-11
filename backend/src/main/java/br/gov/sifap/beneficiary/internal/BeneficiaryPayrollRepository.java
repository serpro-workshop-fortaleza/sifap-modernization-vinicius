package br.gov.sifap.beneficiary.internal;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BeneficiaryPayrollRepository extends JpaRepository<Beneficiary, Long> {

    /**
     * Pagina de candidatos ordenada por CPF.
     *
     * <p>A contagem de dependentes ativos sai em subconsulta: trazer a colecao e contar em
     * memoria custaria uma consulta por beneficiario, que e o padrao N+1 que o
     * {@code BATCHPGT.NSP:214} reproduz com {@code FIND} dentro de {@code READ LOGICAL}.
     */
    @Query("""
            select new br.gov.sifap.beneficiary.internal.PayrollRow(
                b.cpf,
                b.programCode,
                b.status,
                b.familyIncome,
                b.address.regionCode,
                b.birthDate,
                (select count(d) from Dependent d
                  where d.beneficiary = b and d.status = br.gov.sifap.beneficiary.DependentStatus.ATIVO)
            )
            from Beneficiary b
            where b.programCode = :programCode and b.cpf > :afterCpf
            order by b.cpf
            """)
    List<PayrollRow> findPayrollPage(
            @Param("programCode") String programCode,
            @Param("afterCpf") String afterCpf,
            Limit limit);
}
