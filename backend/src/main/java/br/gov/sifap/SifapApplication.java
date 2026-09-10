package br.gov.sifap;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SifapApplication {

    public static void main(String[] args) {
        SpringApplication.run(SifapApplication.class, args);
    }

    /** Injetavel para que o teste possa fixar o instante sem alterar o codigo de dominio. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
