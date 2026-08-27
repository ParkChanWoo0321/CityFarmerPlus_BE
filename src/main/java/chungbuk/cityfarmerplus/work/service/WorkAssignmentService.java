package chungbuk.cityfarmerplus.work.service;

import chungbuk.cityfarmerplus.application.exception.JobApplicationException;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingAccessService;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentView;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.exception.WorkAssignmentException;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@Transactional(readOnly = true)
public class WorkAssignmentService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final WorkAssignmentRepository assignmentRepository;
    private final JobPostingRepository postingRepository;
    private final UserRepository userRepository;
    private final JobPostingAccessService accessService;
    private final Clock clock;

    @Autowired
    public WorkAssignmentService(
            WorkAssignmentRepository assignmentRepository,
            JobPostingRepository postingRepository,
            UserRepository userRepository,
            JobPostingAccessService accessService
    ) {
        this(
                assignmentRepository,
                postingRepository,
                userRepository,
                accessService,
                Clock.systemUTC()
        );
    }

    WorkAssignmentService(
            WorkAssignmentRepository assignmentRepository,
            JobPostingRepository postingRepository,
            UserRepository userRepository,
            JobPostingAccessService accessService,
            Clock clock
    ) {
        this.assignmentRepository = assignmentRepository;
        this.postingRepository = postingRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.clock = clock;
    }

    public PageResponse<WorkAssignmentResponse> getUrbanFarmerAssignments(
            Long userId,
            int page,
            int size
    ) {
        return getUrbanFarmerAssignments(userId, WorkAssignmentView.ALL, page, size);
    }

    public PageResponse<WorkAssignmentResponse> getUrbanFarmerAssignments(
            Long userId,
            WorkAssignmentView view,
            int page,
            int size
    ) {
        requireActiveUrbanFarmer(userId);
        Page<WorkAssignment> assignments = switch (view) {
            case ALL -> assignmentRepository.findByUrbanFarmerId(
                    userId,
                    PageRequest.of(page, size, Sort.by(
                            Sort.Order.desc("workDate"),
                            Sort.Order.desc("startTime"),
                            Sort.Order.desc("id")
                    ))
            );
            case UPCOMING -> {
                ZonedDateTime now = ZonedDateTime.now(clock.withZone(SERVICE_ZONE));
                yield assignmentRepository.findTimelineUpcomingByUrbanFarmerId(
                        userId,
                        now.toLocalDate(),
                        now.toLocalTime(),
                        PageRequest.of(page, size)
                );
            }
            case PAST -> {
                ZonedDateTime now = ZonedDateTime.now(clock.withZone(SERVICE_ZONE));
                yield assignmentRepository.findTimelinePastByUrbanFarmerId(
                        userId,
                        now.toLocalDate(),
                        now.toLocalTime(),
                        PageRequest.of(page, size)
                );
            }
        };
        return PageResponse.from(
                assignments,
                WorkAssignmentResponse::from
        );
    }

    public WorkAssignmentResponse getUrbanFarmerAssignment(
            Long userId,
            Long assignmentId
    ) {
        requireActiveUrbanFarmer(userId);
        WorkAssignment assignment = getAssignment(assignmentId);
        if (!assignment.getUrbanFarmer().getId().equals(userId)) {
            throw WorkAssignmentException.notOwner();
        }
        return WorkAssignmentResponse.from(assignment);
    }

    public PageResponse<WorkAssignmentResponse> getFarmAssignments(
            Long farmUserId,
            int page,
            int size
    ) {
        FarmProfile farm = accessService.requireFarmProfile(farmUserId);
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("workDate"),
                Sort.Order.desc("startTime"),
                Sort.Order.desc("id")
        ));
        return PageResponse.from(
                assignmentRepository.findByFarmProfileId(farm.getId(), pageable),
                WorkAssignmentResponse::from
        );
    }

    @Transactional
    public WorkAssignmentResponse recordAttendance(
            Long farmUserId,
            Long assignmentId,
            WorkAssignment.AttendanceStatus status
    ) {
        FarmProfile farm = accessService.requireFarmProfileForUpdate(farmUserId);
        WorkAssignment snapshot = getFarmOwnedAssignment(farm, assignmentId);
        JobPosting posting = postingRepository.findByIdForUpdate(snapshot.getJobPostingId())
                .orElseThrow(JobPostingException::notFound);
        WorkAssignment assignment = getFarmOwnedAssignmentForUpdate(farm, assignmentId);
        boolean sameAttendanceRetry = status != WorkAssignment.AttendanceStatus.NOT_RECORDED
                && assignment.getAttendanceStatus() == status;
        if (!sameAttendanceRetry) {
            requireWorkStarted(assignment);
        }
        User farmUser = farm.getOwner();
        try {
            assignment.recordAttendance(status, farmUser, clock.instant());
        } catch (IllegalStateException exception) {
            throw WorkAssignmentException.invalidState(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw WorkAssignmentException.invalidState(exception.getMessage());
        }
        if (!sameAttendanceRetry) {
            completePostingWhenAllAssignmentsResolved(posting);
        }
        return WorkAssignmentResponse.from(assignment);
    }

    @Transactional
    public WorkAssignmentResponse completeByFarm(Long farmUserId, Long assignmentId) {
        FarmProfile farm = accessService.requireFarmProfileForUpdate(farmUserId);
        WorkAssignment snapshot = getFarmOwnedAssignment(farm, assignmentId);
        JobPosting posting = postingRepository.findByIdForUpdate(snapshot.getJobPostingId())
                .orElseThrow(JobPostingException::notFound);
        WorkAssignment assignment = getFarmOwnedAssignmentForUpdate(farm, assignmentId);
        requireWorkEnded(assignment);
        try {
            assignment.completeByFarm(clock.instant());
        } catch (IllegalStateException exception) {
            throw WorkAssignmentException.invalidState(exception.getMessage());
        }

        completePostingWhenAllAssignmentsResolved(posting);
        return WorkAssignmentResponse.from(assignment);
    }

    public WorkAssignment getUrbanFarmerOwnedEntity(Long userId, Long assignmentId) {
        requireActiveUrbanFarmer(userId);
        WorkAssignment assignment = getAssignment(assignmentId);
        if (!assignment.getUrbanFarmer().getId().equals(userId)) {
            throw WorkAssignmentException.notOwner();
        }
        return assignment;
    }

    private WorkAssignment getFarmOwnedAssignment(FarmProfile farm, Long assignmentId) {
        WorkAssignment assignment = getAssignment(assignmentId);
        if (!assignment.getFarmProfileId().equals(farm.getId())) {
            throw WorkAssignmentException.notOwner();
        }
        return assignment;
    }

    private WorkAssignment getFarmOwnedAssignmentForUpdate(
            FarmProfile farm,
            Long assignmentId
    ) {
        WorkAssignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(WorkAssignmentException::notFound);
        if (!assignment.getFarmProfileId().equals(farm.getId())) {
            throw WorkAssignmentException.notOwner();
        }
        return assignment;
    }

    private void completePostingWhenAllAssignmentsResolved(JobPosting posting) {
        long unresolvedCount = assignmentRepository.countByJobPostingIdAndStatus(
                posting.getId(),
                WorkAssignment.WorkStatus.SCHEDULED
        );
        if (unresolvedCount == 0) {
            Instant now = clock.instant();
            if (posting.getStatus() == JobPosting.JobPostingStatus.OPEN) {
                posting.close(now);
            }
            if (posting.getStatus() == JobPosting.JobPostingStatus.CLOSED) {
                posting.markWorkCompleted(now);
            }
        }
    }

    private void requireWorkStarted(WorkAssignment assignment) {
        ZonedDateTime serviceNow = ZonedDateTime.now(clock.withZone(SERVICE_ZONE));
        LocalDate today = serviceNow.toLocalDate();
        LocalTime now = serviceNow.toLocalTime();
        if (today.isBefore(assignment.getWorkDate())
                || today.isEqual(assignment.getWorkDate())
                && now.isBefore(assignment.getStartTime())) {
            throw WorkAssignmentException.invalidState(
                    "작업 시작 전에는 출결을 등록할 수 없습니다."
            );
        }
    }

    private void requireWorkEnded(WorkAssignment assignment) {
        ZonedDateTime serviceNow = ZonedDateTime.now(clock.withZone(SERVICE_ZONE));
        LocalDate today = serviceNow.toLocalDate();
        LocalTime now = serviceNow.toLocalTime();
        if (today.isBefore(assignment.getWorkDate())
                || today.isEqual(assignment.getWorkDate())
                && now.isBefore(assignment.getEndTime())) {
            throw WorkAssignmentException.invalidState(
                    "작업 종료 전에는 근무 완료를 확정할 수 없습니다."
            );
        }
    }

    private WorkAssignment getAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(WorkAssignmentException::notFound);
    }

    private User requireActiveUrbanFarmer(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (user.getUserType() != User.UserType.URBAN_FARMER) {
            throw JobApplicationException.urbanFarmerRequired();
        }
        return user;
    }
}
