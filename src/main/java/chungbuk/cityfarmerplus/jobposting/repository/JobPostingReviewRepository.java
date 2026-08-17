package chungbuk.cityfarmerplus.jobposting.repository;

import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobPostingReviewRepository extends JpaRepository<JobPostingReview, Long> {

    List<JobPostingReview> findByJobPostingIdOrderByCreatedAtDescIdDesc(
            Long jobPostingId
    );

    Optional<JobPostingReview> findFirstByJobPostingIdOrderByIdDesc(
            Long jobPostingId
    );

    @Query("""
            select review
            from JobPostingReview review
            where review.jobPosting.id in :jobPostingIds
            order by review.id desc
            """)
    List<JobPostingReview> findAllNewestFirstByJobPostingIds(
            @Param("jobPostingIds") Collection<Long> jobPostingIds
    );

    @Modifying
    @Query("delete from JobPostingReview review where review.jobPosting.id = :jobPostingId")
    int deleteAllByJobPostingId(@Param("jobPostingId") Long jobPostingId);
}
