package br.gov.sifap.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integracao.
 *
 * <p>PostgreSQL real, e nao banco em memoria: particionamento declarativo, permissoes de
 * tabela, restricao {@code UNIQUE} com {@code NULL} e {@code JSONB} sao exatamente as
 * garantias sob teste, e nenhuma delas e reproduzida por um banco simulado.
 *
 * <p>O container e estatico e compartilhado por todas as classes da mesma JVM.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("sifap")
                    .withUsername("sifap_owner")
                    .withPassword("sifap_test_only");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
