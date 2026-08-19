package chungbuk.cityfarmerplus.dashboard.service;

import chungbuk.cityfarmerplus.dashboard.dto.FarmHomeResponse;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingAccessService;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingResponseAssembler;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingSpecifications;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmHomeService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final JobPostingAccessService accessService;
    private final JobPostingRepository postingRepository;
    private final WorkAssignmentRepository assignmentRepository;
    private final JobPostingResponseAssembler postingResponseAssembler;

    public FarmHomeResponse get(Long userId) {
        FarmProfile farm = accessService.requireFarmProfile(userId);
        ZonedDateTime now = ZonedDateTime.now(SERVICE_ZONE);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (JobPosting.JobPostingStatus status : JobPosting.JobPostingStatus.values()) {
            long count = postingRepository.countByFarmProfileOwnerIdAndStatus(
                    userId,
                    status
            );
            counts.put(status.name(), count);
        }
        Map<String, Long> displayCounts = new LinkedHashMap<>();
        displayCounts.put(
                "PENDING",
                counts.get(JobPosting.JobPostingStatus.PENDING_REVIEW.name())
        );
        displayCounts.put(
                "APPROVED",
                postingRepository.countCurrentlyOpenByFarmOwnerId(
                        userId,
                        now.toLocalDate(),
                        now.toLocalTime()
                )
        );
        displayCounts.put(
                "CLOSED",
                counts.get(JobPosting.JobPostingStatus.CLOSED.name())
                        + counts.get(JobPosting.JobPostingStatus.WORK_COMPLETED.name())
                        + postingRepository.countExpiredOpenByFarmOwnerId(
                                userId,
                                now.toLocalDate(),
                                now.toLocalTime()
                        )
        );
        displayCounts.put(
                "REJECTED",
                postingRepository.count(
                        JobPostingSpecifications.belongsToFarmOwner(userId)
                                .and(JobPostingSpecifications.hasStatus(
                                        JobPosting.JobPostingStatus.DRAFT
                                ))
                                .and(JobPostingSpecifications.hasCurrentRejection())
                )
        );
        List<JobPostingResponse> recent = postingResponseAssembler.assembleAll(
                postingRepository.findTop5ByFarmProfileOwnerIdOrderByUpdatedAtDesc(userId)
        );
        List<WorkAssignmentResponse> upcoming = assignmentRepository
                .findUpcomingByFarmProfileId(
                        farm.getId(),
                        now.toLocalDate(),
                        now.toLocalTime(),
                        PageRequest.of(0, 5)
                ).stream().map(WorkAssignmentResponse::from).toList();

        return new FarmHomeResponse(
                FarmProfileResponse.from(farm),
                Map.copyOf(counts),
                Map.copyOf(displayCounts),
                recent,
                upcoming
        );
    }
}
