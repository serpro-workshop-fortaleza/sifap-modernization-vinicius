package br.gov.sifap.audit;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integracao da trilha.
 *
 * <p>PostgreSQL real, e nao banco em memoria: particionamento declarativo, permissoes de
 * tabela e {@code JSONB} sao exatamente as garantias que a especificacao exige e que um
 * banco simulado nao reproduz.
 *
 * <p>O container e estatico e compartilhado entre as classes de teste da mesma JVM.
 */
@SpringBootTest
abstract class AbstractAuditIT {

    static final PostgreSQLContainer<?> POSTGRES =
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
