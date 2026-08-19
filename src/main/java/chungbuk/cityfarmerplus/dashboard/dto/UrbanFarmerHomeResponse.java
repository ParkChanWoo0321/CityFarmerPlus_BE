package chungbuk.cityfarmerplus.dashboard.dto;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.jobposting.dto.PublicJobPostingResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

public record UrbanFarmerHomeResponse(
        EducationCertification.CertificationStatus educationStatus,
        Long latestParticipationApplicationId,
        ParticipationApplication.ParticipationStatus latestParticipationStatus,
        Integer participationProgramYear,
        Instant participationSubmittedAt,
        boolean workPreferenceRegistered,
        List<ChungbukCityCounty> preferredRegions,
        List<DayOfWeek> availableDays,
        List<WorkAssignmentResponse> upcomingWork,
        List<PublicJobPostingResponse> recentOpenPostings
) {
}
