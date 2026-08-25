package chungbuk.cityfarmerplus.ai.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {

    Page<SupportInquiry> findByUserId(Long userId, Pageable pageable);
}
