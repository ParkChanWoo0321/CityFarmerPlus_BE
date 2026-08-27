package chungbuk.cityfarmerplus.admin.dashboard.service;

import chungbuk.cityfarmerplus.admin.dashboard.dto.AdminDashboardResponse;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final ParticipationApplicationRepository participationApplicationRepository;
    private final EducationCertificateSubmissionRepository educationCertificateSubmissionRepository;
    private final FarmProfileRepository farmProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final WorkAssignmentRepository workAssignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        return new AdminDashboardResponse(
                participationApplicationRepository.countByStatus(
                        ParticipationApplication.ParticipationStatus.SUBMITTED
                ),
                educationCertificateSubmissionRepository.countByStatus(
                        EducationCertificateSubmission.SubmissionStatus.PENDING_REVIEW
                ),
                farmProfileRepository.countByStatus(
                        FarmProfile.FarmProfileStatus.PENDING_REVIEW
                ),
                jobPostingRepository.countByStatus(
                        JobPosting.JobPostingStatus.PENDING_REVIEW
                ),
                jobPostingRepository.countByStatus(
                        JobPosting.JobPostingStatus.OPEN
                ),
                jobApplicationRepository.countByStatus(
                        JobApplication.ApplicationStatus.APPLIED
                ),
                workAssignmentRepository.countByStatus(
                        WorkAssignment.WorkStatus.SCHEDULED
                ),
                workAssignmentRepository.countByStatus(
                        WorkAssignment.WorkStatus.COMPLETED
                ),
                userRepository.countByUserTypeAndAccountStatus(
                        User.UserType.URBAN_FARMER, User.AccountStatus.ACTIVE
                ),
                userRepository.countByUserTypeAndAccountStatus(
                        User.UserType.FARM, User.AccountStatus.ACTIVE
                ),
                userRepository.countByUserTypeAndAccountStatus(
                        User.UserType.CENTER_ADMIN, User.AccountStatus.ACTIVE
                )
        );
    }
}
