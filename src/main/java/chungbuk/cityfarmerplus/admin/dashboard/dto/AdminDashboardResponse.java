package chungbuk.cityfarmerplus.admin.dashboard.dto;

public record AdminDashboardResponse(
        long submittedParticipationApplications,
        long pendingEducationSubmissions,
        long pendingFarmOwnershipSubmissions,
        long pendingJobPostings,
        long openJobPostings,
        long pendingJobApplications,
        long scheduledWorkAssignments,
        long completedWorkAssignments,
        long activeUrbanFarmerCount,
        long activeFarmCount,
        long activeCenterAdminCount
) {
}
