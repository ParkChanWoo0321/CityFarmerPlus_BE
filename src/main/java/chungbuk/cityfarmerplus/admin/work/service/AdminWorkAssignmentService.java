package chungbuk.cityfarmerplus.admin.work.service;

import chungbuk.cityfarmerplus.admin.work.dto.AttendanceCorrectionRequest;
import chungbuk.cityfarmerplus.admin.work.dto.WorkAssignmentCorrectionResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.entity.WorkAssignmentCorrection;
import chungbuk.cityfarmerplus.work.exception.WorkAssignmentException;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentCorrectionRepository;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminWorkAssignmentService {

    private final WorkAssignmentRepository workAssignmentRepository;
    private final WorkAssignmentCorrectionRepository correctionRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkAssignmentCorrectionResponse correctAttendance(
            Long adminId,
            Long assignmentId,
            AttendanceCorrectionRequest request
    ) {
        User admin = requireCenterAdmin(adminId);
        WorkAssignment assignment = workAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(WorkAssignmentException::notFound);

        if (assignment.getAttendanceStatus() == WorkAssignment.AttendanceStatus.NOT_RECORDED) {
            throw WorkAssignmentException.invalidState(
                    "아직 최초 출결이 등록되지 않았습니다. 정정이 아니라 최초 등록이 필요합니다."
            );
        }

        WorkAssignment.WorkStatus previousWorkStatus = assignment.getStatus();
        WorkAssignment.AttendanceStatus previousAttendanceStatus = assignment.getAttendanceStatus();

        try {
            assignment.correctAttendance(request.status(), Instant.now());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw WorkAssignmentException.invalidState(exception.getMessage());
        }

        WorkAssignment.WorkStatus newWorkStatus = assignment.getStatus();
        WorkAssignment.AttendanceStatus newAttendanceStatus = assignment.getAttendanceStatus();

        if (newWorkStatus == WorkAssignment.WorkStatus.SCHEDULED) {
            JobPosting posting = jobPostingRepository
                    .findByIdForUpdate(assignment.getJobPostingId())
                    .orElseThrow(JobPostingException::notFound);
            if (posting.getStatus() == JobPosting.JobPostingStatus.WORK_COMPLETED) {
                posting.reopenAfterAttendanceCorrection();
            }
        }

        WorkAssignmentCorrection correction = correctionRepository.save(
                WorkAssignmentCorrection.record(
                        assignment,
                        previousWorkStatus,
                        newWorkStatus,
                        previousAttendanceStatus,
                        newAttendanceStatus,
                        admin,
                        request.reason()
                )
        );
        return WorkAssignmentCorrectionResponse.from(correction);
    }

    @Transactional(readOnly = true)
    public List<WorkAssignmentCorrectionResponse> getCorrectionHistory(
            Long adminId,
            Long assignmentId
    ) {
        requireCenterAdmin(adminId);
        if (!workAssignmentRepository.existsById(assignmentId)) {
            throw WorkAssignmentException.notFound();
        }
        return correctionRepository
                .findAllByWorkAssignmentIdOrderByCorrectedAtDesc(assignmentId)
                .stream()
                .map(WorkAssignmentCorrectionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<WorkAssignmentResponse> list(
            Long adminId,
            WorkAssignment.WorkStatus status,
            Pageable pageable
    ) {
        requireCenterAdmin(adminId);
        Page<WorkAssignment> assignments = status == null
                ? workAssignmentRepository.findAll(pageable)
                : workAssignmentRepository.findByStatus(status, pageable);
        return assignments.map(WorkAssignmentResponse::from);
    }

    @Transactional(readOnly = true)
    public WorkAssignmentResponse getDetail(Long adminId, Long assignmentId) {
        requireCenterAdmin(adminId);
        WorkAssignment assignment = workAssignmentRepository.findById(assignmentId)
                .orElseThrow(WorkAssignmentException::notFound);
        return WorkAssignmentResponse.from(assignment);
    }

    private User requireCenterAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(AuthException::userNotFound);
        if (!admin.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (admin.getUserType() != User.UserType.CENTER_ADMIN) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "CENTER_ADMIN_ROLE_REQUIRED",
                    "관리자 계정만 사용할 수 있습니다."
            );
        }
        return admin;
    }
}
