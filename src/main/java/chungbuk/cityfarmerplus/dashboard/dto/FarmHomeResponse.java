package chungbuk.cityfarmerplus.dashboard.dto;

import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;

import java.util.List;
import java.util.Map;

public record FarmHomeResponse(
        FarmProfileResponse farmProfile,
        Map<String, Long> postingCounts,
        Map<String, Long> displayPostingCounts,
        List<JobPostingResponse> recentPostings,
        List<WorkAssignmentResponse> upcomingWork
) {
}
