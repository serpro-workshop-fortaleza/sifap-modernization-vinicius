package br.gov.sifap.beneficiary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.gov.sifap.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** T-211 — cobre {@code REQ-BEN-001} a {@code REQ-BEN-018} pela borda HTTP. */
@AutoConfigureMockMvc
class BeneficiaryApiIT extends AbstractIntegrationTest {

    private static final String CPF = "11144477735";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        jdbcTemplate.update("DELETE FROM beneficiary_migration_issue");
        jdbcTemplate.update("DELETE FROM dependent");
        jdbcTemplate.update("DELETE FROM beneficiary");
    }

    @Test
    @DisplayName("deve criar o beneficiario e devolver o documento mascarado")
    void deve_criar_o_beneficiario() throws Exception {
        mockMvc.perform(asOperator(post("/api/v1/beneficiaries")).content(registerBody(CPF)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedCpf").value("***.444.777-**"))
                .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    @DisplayName("deve devolver o REQ-ID quando a regra recusa a operacao")
    void deve_devolver_o_requisito_quando_a_regra_recusa() throws Exception {
        // A rastreabilidade acompanha o erro devolvido, e nao apenas o commit.
        mockMvc.perform(asOperator(post("/api/v1/beneficiaries")).content(registerBody("11111111111")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requirementId").value("REQ-BEN-003"));
    }

    @Test
    @DisplayName("deve recusar a inclusao repetida com conflito")
    void deve_recusar_inclusao_repetida() throws Exception {
        mockMvc.perform(asOperator(post("/api/v1/beneficiaries")).content(registerBody(CPF)));

        mockMvc.perform(asOperator(post("/api/v1/beneficiaries")).content(registerBody(CPF)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.requirementId").value("REQ-BEN-001"));
    }

    @Test
    @DisplayName("deve alterar dados cadastrais sem afetar a situacao")
    void deve_alterar_sem_afetar_a_situacao() throws Exception {
        mockMvc.perform(asOperator(post("/api/v1/beneficiaries")).content(registerBody(CPF)));
        mockMvc.perform(asOperator(post("/api/v1/beneficiaries/" + CPF + "/status"))
                        .content("{\"newStatus\":\"SUSPENSO\",\"reason\":\"revisao cadastral\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(asOperator(put("/api/v1/beneficiaries/" + CPF))
                        .content("{\"fullName\":\"Maria Silva Souza\",\"programCode\":\"0001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENSO"));
    }

    @Test
    @DisplayName("deve incluir dependente e devolver a contagem de ativos")
    void deve_incluir_dependente() throws Exception {
        mockMvc.perform(asOperator(post("/api/v1/beneficiaries")).content(registerBody(CPF)));

        mockMvc.perform(asOperator(post("/api/v1/beneficiaries/" + CPF + "/dependents"))
                        .content("{\"fullName\":\"Ana Souza\",\"relation\":\"FI\",\"disability\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activeDependentCount").value(1));
    }

    @Test
    @DisplayName("deve consultar por CPF e por NIS")
    void deve_consultar_por_cpf_e_por_nis() throws Exception {
        mockMvc.perform(asOperator(post("/api/v1/beneficiaries")).content(registerBody(CPF)));

        mockMvc.perform(asOperator(get("/api/v1/beneficiaries/" + CPF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria Silva"));

        mockMvc.perform(asOperator(get("/api/v1/beneficiaries").param("nis", "12345678919")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deve devolver nao encontrado quando o CPF nao esta cadastrado")
    void deve_devolver_nao_encontrado() throws Exception {
        mockMvc.perform(asOperator(get("/api/v1/beneficiaries/52998224725")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requirementId").value("REQ-BEN-015"));
    }

    @Test
    @DisplayName("deve exigir autor e perfil em toda operacao")
    void deve_exigir_autor_e_perfil() throws Exception {
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(CPF)))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder asOperator(MockHttpServletRequestBuilder builder) {
        return builder
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Sifap-Actor-Id", "op.silva")
                .header("X-Sifap-Actor-Profile", "SUPERVISOR");
    }

    private String registerBody(String cpf) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "cpf", cpf,
                "nis", "12345678919",
                "fullName", "Maria Silva",
                "birthDate", "1990-05-20",
                "sex", "F",
                "programCode", "0001",
                "familyIncome", "1200.00"));
    }
}
