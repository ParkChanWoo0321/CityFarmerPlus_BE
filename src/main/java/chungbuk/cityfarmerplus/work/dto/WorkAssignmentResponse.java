package chungbuk.cityfarmerplus.work.dto;

import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record WorkAssignmentResponse(
        Long id,
        Long jobPostingId,
        Long jobApplicationId,
        Long urbanFarmerUserId,
        String urbanFarmerName,
        Long confirmedByUserId,
        String confirmedByName,
        String confirmedByContactNumber,
        String farmName,
        String farmAddress,
        String farmContactNumber,
        String crop,
        String workType,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer recruitmentCapacity,
        String meetingPlace,
        int wageAmount,
        JobPosting.WageUnit wageUnit,
        String supplies,
        String precautions,
        WorkAssignment.WorkStatus status,
        WorkAssignment.AttendanceStatus attendanceStatus,
        Instant completedAt
) {

    public static WorkAssignmentResponse from(WorkAssignment assignment) {
        return new WorkAssignmentResponse(
                assignment.getId(),
                assignment.getJobPostingId(),
                assignment.getJobApplication().getId(),
                assignment.getUrbanFarmer().getId(),
                assignment.getUrbanFarmer().getName(),
                assignment.getJobApplication().getConfirmedBy() == null
                        ? null : assignment.getJobApplication().getConfirmedBy().getId(),
                assignment.getJobApplication().getConfirmedBy() == null
                        ? null : assignment.getJobApplication().getConfirmedBy().getName(),
                assignment.getJobApplication().getConfirmedBy() == null
                        ? null : assignment.getJobApplication().getConfirmedBy().getPhoneNumber(),
                assignment.getFarmName(),
                assignment.getFarmAddress(),
                assignment.getFarmContactNumber(),
                assignment.getCrop(),
                assignment.getWorkType(),
                assignment.getWorkDate(),
                assignment.getStartTime(),
                assignment.getEndTime(),
                assignment.getRecruitmentCapacity(),
                assignment.getMeetingPlace(),
                assignment.getWageAmount(),
                assignment.getWageUnit(),
                assignment.getSupplies(),
                assignment.getPrecautions(),
                assignment.getStatus(),
                assignment.getAttendanceStatus(),
                assignment.getCompletedAt()
        );
    }
}
