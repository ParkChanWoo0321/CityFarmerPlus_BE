package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.jobposting.dto.PublicRecruitmentStatus;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class JobPostingSpecifications {

    private JobPostingSpecifications() {
    }

    public static Specification<JobPosting> hasStatus(JobPosting.JobPostingStatus status) {
        return status == null ? null : (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<JobPosting> hasStatusIn(
            Collection<JobPosting.JobPostingStatus> statuses
    ) {
        return statuses == null || statuses.isEmpty()
                ? null
                : (root, query, builder) -> root.get("status").in(statuses);
    }

    public static Specification<JobPosting> belongsToFarmOwner(Long ownerId) {
        return (root, query, builder) -> builder.equal(
                root.get("farmProfile").get("owner").get("id"),
                ownerId
        );
    }

    public static Specification<JobPosting> hasCurrentRejection() {
        return (root, query, builder) -> {
            var latestReviewId = query.subquery(Long.class);
            var latestReview = latestReviewId.from(JobPostingReview.class);
            latestReviewId.select(builder.max(latestReview.get("id")))
                    .where(builder.equal(latestReview.get("jobPosting"), root));

            var matchingReview = query.subquery(Long.class);
            var review = matchingReview.from(JobPostingReview.class);
            matchingReview.select(review.get("id")).where(
                    builder.equal(review.get("jobPosting"), root),
                    builder.equal(review.get("id"), latestReviewId),
                    builder.equal(
                            review.get("action"),
                            JobPostingReview.ReviewAction.REJECTED
                    ),
                    builder.greaterThanOrEqualTo(
                            review.<Instant>get("createdAt"),
                            root.<Instant>get("reviewRequestedAt")
                    )
            );
            return builder.and(
                    builder.isNotNull(root.get("reviewRequestedAt")),
                    builder.exists(matchingReview)
            );
        };
    }

    public static Specification<JobPosting> doesNotHaveCurrentRejection() {
        return Specification.not(hasCurrentRejection());
    }

    public static Specification<JobPosting> hasRegion(ChungbukCityCounty region) {
        return region == null ? null : (root, query, builder) ->
                builder.equal(root.get("farmProfile").get("cityCounty"), region);
    }

    public static Specification<JobPosting> workDateFrom(LocalDate date) {
        return date == null ? null : (root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("workDate"), date);
    }

    public static Specification<JobPosting> workDateTo(LocalDate date) {
        return date == null ? null : (root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("workDate"), date);
    }

    public static Specification<JobPosting> workTypeContains(String workType) {
        if (workType == null || workType.isBlank()) {
            return null;
        }
        String keyword = "%" + workType.trim().toLowerCase() + "%";
        return (root, query, builder) ->
                builder.like(builder.lower(root.get("workType")), keyword);
    }

    public static Specification<JobPosting> hasCrop(String crop) {
        if (crop == null || crop.isBlank()) {
            return null;
        }
        String exactCrop = crop.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> builder.equal(
                builder.lower(root.get("crop")),
                exactCrop
        );
    }

    public static Specification<JobPosting> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + normalizedKeyword + "%";
        List<ChungbukCityCounty> matchingRegions = Arrays.stream(
                        ChungbukCityCounty.values()
                )
                .filter(region -> region.name().toLowerCase(Locale.ROOT)
                        .contains(normalizedKeyword)
                        || region.getKoreanName().toLowerCase(Locale.ROOT)
                        .contains(normalizedKeyword))
                .toList();
        return (root, query, builder) -> {
            List<Predicate> predicates = new java.util.ArrayList<>(List.of(
                    builder.like(builder.lower(root.get("title")), pattern),
                    builder.like(builder.lower(root.get("crop")), pattern),
                    builder.like(builder.lower(root.get("workType")), pattern),
                    builder.like(
                            builder.lower(root.get("farmProfile").get("farmName")),
                            pattern
                    ),
                    builder.like(builder.lower(root.get("meetingPlace")), pattern)
            ));
            if (!matchingRegions.isEmpty()) {
                predicates.add(root.get("farmProfile").get("cityCounty")
                        .in(matchingRegions));
            }
            return builder.or(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<JobPosting> hasPublicRecruitmentStatus(
            PublicRecruitmentStatus recruitmentStatus,
            LocalDate today,
            LocalTime now
    ) {
        PublicRecruitmentStatus resolvedStatus = recruitmentStatus == null
                ? PublicRecruitmentStatus.OPEN
                : recruitmentStatus;
        Specification<JobPosting> open = Specification
                .where(hasStatus(JobPosting.JobPostingStatus.OPEN))
                .and(startsAfter(today, now));
        Specification<JobPosting> closed = Specification
                .where(hasStatusIn(List.of(
                        JobPosting.JobPostingStatus.CLOSED,
                        JobPosting.JobPostingStatus.WORK_COMPLETED
                )))
                .or(Specification
                        .where(hasStatus(JobPosting.JobPostingStatus.OPEN))
                        .and(startsAtOrBefore(today, now)));
        return switch (resolvedStatus) {
            case OPEN -> open;
            case CLOSED -> closed;
            case ALL -> open.or(closed);
        };
    }

    public static Specification<JobPosting> wasApprovedForPublicView() {
        return (root, query, builder) -> builder.isNotNull(root.get("approvedAt"));
    }

    public static Specification<JobPosting> orderOpenFirst(
            LocalDate today,
            LocalTime now
    ) {
        return (root, query, builder) -> {
            if (query.getResultType() != Long.class
                    && query.getResultType() != long.class) {
                Predicate accepting = builder.and(
                        builder.equal(
                                root.get("status"),
                                JobPosting.JobPostingStatus.OPEN
                        ),
                        builder.or(
                                builder.greaterThan(root.get("workDate"), today),
                                builder.and(
                                        builder.equal(root.get("workDate"), today),
                                        builder.greaterThan(root.get("startTime"), now)
                                )
                        )
                );
                Expression<Integer> statusOrder = builder.<Integer>selectCase()
                        .when(accepting, 0)
                        .otherwise(1);
                query.orderBy(
                        builder.asc(statusOrder),
                        builder.asc(root.get("workDate")),
                        builder.asc(root.get("startTime")),
                        builder.desc(root.get("approvedAt"))
                );
            }
            return builder.conjunction();
        };
    }

    public static Specification<JobPosting> startsAfter(
            LocalDate today,
            LocalTime now
    ) {
        return (root, query, builder) -> builder.or(
                builder.greaterThan(root.get("workDate"), today),
                builder.and(
                        builder.equal(root.get("workDate"), today),
                        builder.greaterThan(root.get("startTime"), now)
                )
        );
    }

    public static Specification<JobPosting> startsAtOrBefore(
            LocalDate today,
            LocalTime now
    ) {
        return (root, query, builder) -> builder.or(
                builder.lessThan(root.get("workDate"), today),
                builder.and(
                        builder.equal(root.get("workDate"), today),
                        builder.lessThanOrEqualTo(root.get("startTime"), now)
                )
        );
    }

    public static Specification<JobPosting> hasActiveApprovedFarm() {
        return (root, query, builder) -> builder.and(
                builder.equal(root.get("farmProfile").get("status"),
                        chungbuk.cityfarmerplus.farm.entity.FarmProfile
                                .FarmProfileStatus.APPROVED),
                builder.equal(root.get("farmProfile").get("owner")
                        .get("accountStatus"),
                        chungbuk.cityfarmerplus.auth.entity.User.AccountStatus.ACTIVE)
        );
    }
}
