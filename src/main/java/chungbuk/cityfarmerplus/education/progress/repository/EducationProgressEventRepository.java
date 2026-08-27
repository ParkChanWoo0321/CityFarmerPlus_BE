package chungbuk.cityfarmerplus.education.progress.repository;

import chungbuk.cityfarmerplus.education.progress.entity.EducationProgressEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EducationProgressEventRepository
        extends JpaRepository<EducationProgressEvent, Long> {

    Optional<EducationProgressEvent> findByProviderAndProviderEventId(
            String provider,
            String providerEventId
    );
}
