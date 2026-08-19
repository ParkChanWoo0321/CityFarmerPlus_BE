package chungbuk.cityfarmerplus.dashboard.service;

import chungbuk.cityfarmerplus.application.exception.JobApplicationException;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.dashboard.dto.UrbanFarmerHomeResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.service.EducationProgressService;
import chungbuk.cityfarmerplus.jobposting.dto.PublicJobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.service.PublicJobPostingService;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;
import chungbuk.cityfarmerplus.urbanfarmer.preference.repository.UrbanFarmerWorkPreferenceRepository;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UrbanFarmerHomeService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final EducationCertificationRepository certificationRepository;
    private final EducationProgressService educationProgressService;
    private final ParticipationApplicationRepository participationRepository;
    private final UrbanFarmerWorkPreferenceRepository preferenceRepository;
    private final WorkAssignmentRepository assignmentRepository;
    private final PublicJobPostingService publicJobPostingService;

    public UrbanFarmerHomeResponse get(Long userId) {
        requireActiveUrbanFarmer(userId);
        ZonedDateTime now = ZonedDateTime.now(SERVICE_ZONE);
        LocalDate today = now.toLocalDate();
        EducationCertification.CertificationStatus educationStatus =
                educationProgressService.getProgress(
                        userId,
                        certificationRepository.findByUrbanFarmerId(userId).orElse(null)
                ).status();
        ParticipationApplication latest = participationRepository
                .findByUrbanFarmerIdAndProgramYear(userId, today.getYear())
                .orElse(null);
        Optional<UrbanFarmerWorkPreference> workPreference = preferenceRepository
                .findByUrbanFarmerId(userId);
        List<WorkAssignmentResponse> upcoming = assignmentRepository
                .findUpcomingByUrbanFarmerId(
                        userId,
                        today,
                        now.toLocalTime(),
                        PageRequest.of(0, 5)
                ).stream().map(WorkAssignmentResponse::from).toList();
        List<PublicJobPostingResponse> recent = publicJobPostingService
                .getOpenPostings(
                        userId,
                        null,
                        today,
                        null,
                        null,
                        0,
                        5
                )
                .content();

        return new UrbanFarmerHomeResponse(
                educationStatus,
                latest == null ? null : latest.getId(),
                latest == null ? null : latest.getStatus(),
                latest == null ? null : latest.getProgramYear(),
                latest == null ? null : latest.getSubmittedAt(),
                workPreference.isPresent(),
                workPreference
                        .map(UrbanFarmerWorkPreference::getPreferredRegions)
                        .map(List::copyOf)
                        .orElseGet(List::of),
                workPreference
                        .map(UrbanFarmerWorkPreference::getAvailableDays)
                        .map(List::copyOf)
                        .orElseGet(List::of),
                upcoming,
                recent
        );
    }

    private void requireActiveUrbanFarmer(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (user.getUserType() != User.UserType.URBAN_FARMER) {
            throw JobApplicationException.urbanFarmerRequired();
        }
    }
}
