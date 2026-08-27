package chungbuk.cityfarmerplus.admin.work.dto;

import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.entity.WorkAssignmentCorrection;

import java.time.Instant;

public record WorkAssignmentCorrectionResponse(
        Long id,
        Long workAssignmentId,
        WorkAssignment.WorkStatus previousWorkStatus,
        WorkAssignment.WorkStatus newWorkStatus,
        WorkAssignment.AttendanceStatus previousAttendanceStatus,
        WorkAssignment.AttendanceStatus newAttendanceStatus,
        Long correctedByUserId,
        String correctedByName,
        String reason,
        Instant correctedAt
) {

    public static WorkAssignmentCorrectionResponse from(WorkAssignmentCorrection correction) {
        return new WorkAssignmentCorrectionResponse(
                correction.getId(),
                correction.getWorkAssignment().getId(),
                correction.getPreviousWorkStatus(),
                correction.getNewWorkStatus(),
                correction.getPreviousAttendanceStatus(),
                correction.getNewAttendanceStatus(),
                correction.getCorrectedBy().getId(),
                correction.getCorrectedBy().getName(),
                correction.getReason(),
                correction.getCorrectedAt()
        );
    }
}
