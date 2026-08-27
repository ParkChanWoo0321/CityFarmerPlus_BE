package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.jobposting.dto.PublicJobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.PublicRecruitmentStatus;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicJobPostingService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public PageResponse<PublicJobPostingResponse> getOpenPostings(
            ChungbukCityCounty region,
            LocalDate dateFrom,
            LocalDate dateTo,
            String workType,
            int page,
            int size
    ) {
        return getOpenPostings(
                (String) null,
                region,
                dateFrom,
                dateTo,
                workType,
                page,
                size
        );
    }

    public PageResponse<PublicJobPostingResponse> getOpenPostings(
            String keyword,
            ChungbukCityCounty region,
            LocalDate dateFrom,
            LocalDate dateTo,
            String workType,
            int page,
            int size
    ) {
        return getPostings(
                null,
                keyword,
                region,
                null,
                dateFrom,
                dateTo,
                workType,
                PublicRecruitmentStatus.OPEN,
                page,
                size
        );
    }

    public PageResponse<PublicJobPostingResponse> getOpenPostings(
            Long currentUserId,
            ChungbukCityCounty region,
            LocalDate dateFrom,
            LocalDate dateTo,
            String workType,
            int page,
            int size
    ) {
        return getPostings(
                currentUserId,
                null,
                region,
                null,
                dateFrom,
                dateTo,
                workType,
                PublicRecruitmentStatus.OPEN,
                page,
                size
        );
    }

    public PageResponse<PublicJobPostingResponse> getPostings(
            Long currentUserId,
            String keyword,
            ChungbukCityCounty region,
            String crop,
            LocalDate dateFrom,
            LocalDate dateTo,
            String workType,
            PublicRecruitmentStatus recruitmentStatus,
            int page,
            int size
    ) {
        validateDateRange(dateFrom, dateTo);
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        LocalTime now = LocalTime.now(SERVICE_ZONE);
        PublicRecruitmentStatus resolvedStatus = recruitmentStatus == null
                ? PublicRecruitmentStatus.OPEN
                : recruitmentStatus;
        Specification<JobPosting> specification = JobPostingSpecifications
                .hasPublicRecruitmentStatus(
                        resolvedStatus,
                        today,
                        now
                )
                .and(JobPostingSpecifications.wasApprovedForPublicView())
                .and(JobPostingSpecifications.hasActiveFarmOwner());
        specification = andIfPresent(
                specification,
                JobPostingSpecifications.keywordContains(keyword)
        );
        specification = andIfPresent(
                specification,
                JobPostingSpecifications.hasRegion(region)
        );
        specification = andIfPresent(
                specification,
                JobPostingSpecifications.hasCrop(crop)
        );
        specification = andIfPresent(
                specification,
                JobPostingSpecifications.workDateFrom(dateFrom)
        );
        specification = andIfPresent(
                specification,
                JobPostingSpecifications.workDateTo(dateTo)
        );
        specification = andIfPresent(
                specification,
                JobPostingSpecifications.workTypeContains(workType)
        );
        var pageable = resolvedStatus == PublicRecruitmentStatus.ALL
                ? PageRequest.of(page, size)
                : PageRequest.of(page, size, Sort.by(
                Sort.Order.asc("workDate"),
                Sort.Order.asc("startTime"),
                Sort.Order.desc("approvedAt"),
                Sort.Order.asc("id")
        ));
        if (resolvedStatus == PublicRecruitmentStatus.ALL) {
            specification = specification.and(
                    JobPostingSpecifications.orderOpenFirst(today, now)
            );
        }
        var postings = jobPostingRepository.findAll(specification, pageable);
        Map<Long, JobApplication> myApplications = findMyApplications(
                currentUserId,
                postings.getContent()
        );
        List<PublicJobPostingResponse> content = postings.getContent().stream()
                .map(posting -> PublicJobPostingResponse.from(
                        posting,
                        myApplications.get(posting.getId()),
                        today,
                        now
                ))
                .toList();
        return new PageResponse<>(
                content,
                postings.getNumber(),
                postings.getSize(),
                postings.getTotalElements(),
                postings.getTotalPages(),
                postings.hasNext()
        );
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_JOB_POSTING_DATE_RANGE",
                    "조회 시작일은 종료일보다 늦을 수 없습니다."
            );
        }
    }

    public PublicJobPostingResponse getOpenPosting(Long postingId) {
        return getPosting(null, postingId, false);
    }

    public PublicJobPostingResponse getPosting(
            Long currentUserId,
            Long postingId,
            boolean includeClosed
    ) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        LocalTime now = LocalTime.now(SERVICE_ZONE);
        JobPosting posting = jobPostingRepository.findById(postingId)
                .filter(value -> isPubliclyReadable(
                        value,
                        includeClosed,
                        today,
                        now
                ))
                .orElseThrow(JobPostingException::notOpen);
        JobApplication myApplication = currentUserId == null
                ? null
                : jobApplicationRepository
                .findByJobPostingIdAndUrbanFarmerId(postingId, currentUserId)
                .orElse(null);
        return PublicJobPostingResponse.from(
                posting,
                myApplication,
                today,
                now
        );
    }

    private Map<Long, JobApplication> findMyApplications(
            Long currentUserId,
            List<JobPosting> postings
    ) {
        if (currentUserId == null || postings.isEmpty()) {
            return Map.of();
        }
        List<Long> postingIds = postings.stream().map(JobPosting::getId).toList();
        return jobApplicationRepository
                .findAllByJobPostingIdInAndUrbanFarmerId(
                        postingIds,
                        currentUserId
                ).stream()
                .collect(Collectors.toMap(
                        application -> application.getJobPosting().getId(),
                        Function.identity()
                ));
    }

    private boolean isPubliclyReadable(
            JobPosting posting,
            boolean includeClosed,
            LocalDate today,
            LocalTime now
    ) {
        if (posting.getApprovedAt() == null
                || !posting.getFarmProfile().getOwner().isActive()) {
            return false;
        }
        if (posting.isAcceptingApplications(today, now)) {
            return true;
        }
        if (!includeClosed) {
            return false;
        }
        return posting.getStatus() == JobPosting.JobPostingStatus.CLOSED
                || posting.getStatus() == JobPosting.JobPostingStatus.WORK_COMPLETED
                || posting.getStatus() == JobPosting.JobPostingStatus.OPEN
                && (posting.getWorkDate().isBefore(today)
                || posting.getWorkDate().isEqual(today)
                && !posting.getStartTime().isAfter(now));
    }

    private Specification<JobPosting> andIfPresent(
            Specification<JobPosting> specification,
            Specification<JobPosting> optional
    ) {
        return optional == null ? specification : specification.and(optional);
    }
}
