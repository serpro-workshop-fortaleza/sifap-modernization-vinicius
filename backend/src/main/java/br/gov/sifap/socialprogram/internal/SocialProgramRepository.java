package br.gov.sifap.socialprogram.internal;

import br.gov.sifap.socialprogram.SocialProgramStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialProgramRepository extends JpaRepository<SocialProgram, Long> {

    @EntityGraph(attributePaths = {"bands", "regions"})
    Optional<SocialProgram> findByCode(String code);

    List<SocialProgram> findByStatusOrderByCodeAsc(SocialProgramStatus status);

    boolean existsByCode(String code);
}
