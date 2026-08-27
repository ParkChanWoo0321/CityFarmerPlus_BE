package chungbuk.cityfarmerplus.application.service;

import chungbuk.cityfarmerplus.application.dto.JobApplicationResponse;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.exception.JobApplicationException;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.education.service.EducationEligibilityService;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.urbanfarmer.preference.repository.UrbanFarmerWorkPreferenceRepository;
import chungbuk.cityfarmerplus.urbanfarmer.profile.repository.UrbanFarmerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository applicationRepository;
    private final EducationEligibilityService educationEligibilityService;
    private final UrbanFarmerWorkPreferenceRepository preferenceRepository;
    private final UrbanFarmerProfileRepository profileRepository;

    @Transactional
    public JobApplicationResponse apply(Long userId, Long postingId) {
        JobPosting posting = jobPostingRepository.findByIdForUpdate(postingId)
                .filter(value -> value.isAcceptingApplications(
                        LocalDate.now(SERVICE_ZONE),
                        LocalTime.now(SERVICE_ZONE)
                ))
                .orElseThrow(JobPostingException::notOpen);
        User urbanFarmer = requireActiveUrbanFarmerForUpdate(userId);
        educationEligibilityService.requireApproved(userId);

        var preference = preferenceRepository.findByUrbanFarmerId(userId);
        String regions = preference
                .map(value -> value.getPreferredRegions().stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(",")))
                .orElse(null);
        String days = preference
                .map(value -> value.getAvailableDays().stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(",")))
                .orElse(null);
        LocalDate preferredStartDate = preference
                .map(value -> value.getPreferredStartDate())
                .orElse(null);
        LocalDate preferredEndDate = preference
                .map(value -> value.getPreferredEndDate())
                .orElse(null);
        String workTypes = preference
                .map(value -> String.join(",", value.getAvailableWorkTypes()))
                .orElse(null);
        Boolean canTravel = preference
                .map(value -> value.isCanTravel())
                .orElse(null);
        int experienceCount = profileRepository.findByUrbanFarmerId(userId)
                .map(profile -> profile.getExperienceCount())
                .orElse(0);

        var existing = applicationRepository
                .findByJobPostingIdAndUrbanFarmerId(postingId, userId);
        if (existing.isPresent()) {
            JobApplication application = existing.get();
            try {
                application.reapply(
                        Instant.now(),
                        regions,
                        days,
                        preferredStartDate,
                        preferredEndDate,
                        workTypes,
                        canTravel,
                        experienceCount
                );
                return JobApplicationResponse.from(application);
            } catch (IllegalStateException exception) {
                throw JobApplicationException.duplicateApplication();
            }
        }

        JobApplication application = JobApplication.apply(
                posting,
                urbanFarmer,
                Instant.now(),
                regions,
                days,
                preferredStartDate,
                preferredEndDate,
                workTypes,
                canTravel,
                experienceCount
        );
        try {
            return JobApplicationResponse.from(
                    applicationRepository.saveAndFlush(application)
            );
        } catch (DataIntegrityViolationException exception) {
            throw JobApplicationException.duplicateApplication();
        }
    }

    public PageResponse<JobApplicationResponse> getMine(
            Long userId,
            int page,
            int size
    ) {
        requireActiveUrbanFarmer(userId);
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        return PageResponse.from(
                applicationRepository.findByUrbanFarmerId(userId, pageable),
                JobApplicationResponse::from
        );
    }

    public JobApplicationResponse getMine(Long userId, Long applicationId) {
        requireActiveUrbanFarmer(userId);
        JobApplication application = getApplication(applicationId);
        verifyOwner(application, userId);
        return JobApplicationResponse.from(application);
    }

    @Transactional
    public JobApplicationResponse withdraw(Long userId, Long applicationId) {
        requireActiveUrbanFarmer(userId);
        JobApplication snapshot = getApplication(applicationId);
        verifyOwner(snapshot, userId);
        jobPostingRepository.findByIdForUpdate(snapshot.getJobPosting().getId())
                .orElseThrow(JobPostingException::notFound);
        JobApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(JobApplicationException::notFound);
        verifyOwner(application, userId);
        try {
            application.withdraw(Instant.now());
        } catch (IllegalStateException exception) {
            throw JobApplicationException.invalidState(exception.getMessage());
        }
        return JobApplicationResponse.from(application);
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

    private User requireActiveUrbanFarmerForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (user.getUserType() != User.UserType.URBAN_FARMER) {
            throw JobApplicationException.urbanFarmerRequired();
        }
        return user;
    }

    private JobApplication getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(JobApplicationException::notFound);
    }

    private void verifyOwner(JobApplication application, Long userId) {
        if (!application.getUrbanFarmer().getId().equals(userId)) {
            throw JobApplicationException.notOwner();
        }
    }
}
