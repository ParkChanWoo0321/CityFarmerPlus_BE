package chungbuk.cityfarmerplus.urbanfarmer.participation.dto;

import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;

import java.time.Instant;

public record ParticipationApplicationResponse(
        Long id,
        Long urbanFarmerId,
        String urbanFarmerName,
        int programYear,
        boolean agriculturalBusinessRegistered,
        String applicationNote,
        ParticipationApplication.ParticipationStatus status,
        Long reviewedByUserId,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        Instant cancelledAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static ParticipationApplicationResponse from(
            ParticipationApplication application
    ) {
        return new ParticipationApplicationResponse(
                application.getId(),
                application.getUrbanFarmer().getId(),
                application.getUrbanFarmer().getName(),
                application.getProgramYear(),
                application.isAgriculturalBusinessRegistered(),
                application.getApplicationNote(),
                application.getStatus(),
                application.getReviewedBy() == null
                        ? null
                        : application.getReviewedBy().getId(),
                application.getRejectionReason(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getCancelledAt(),
                application.getVersion(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
