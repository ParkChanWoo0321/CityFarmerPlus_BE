package chungbuk.cityfarmerplus.farm.ownership.dto;

import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;

import java.time.Instant;
import java.util.List;

public record FarmOwnershipSubmissionResponse(
        Long id,
        int attemptNumber,
        FarmOwnershipSubmission.SubmissionStatus status,
        FarmProfile.FarmProfileStatus farmProfileStatus,
        Instant submittedAt,
        Long reviewerId,
        String reviewerName,
        Instant reviewedAt,
        String rejectionReason,
        List<FarmOwnershipDocumentResponse> documents,
        String farmNameSnapshot,
        String representativeNameSnapshot,
        String farmAddressSnapshot,
        ChungbukCityCounty cityCountySnapshot,
        String businessRegistrationNumberSnapshot,
        Integer farmAreaPyeongSnapshot
) {

    public FarmOwnershipSubmissionResponse(
            Long id,
            int attemptNumber,
            FarmOwnershipSubmission.SubmissionStatus status,
            FarmProfile.FarmProfileStatus farmProfileStatus,
            Instant submittedAt,
            List<FarmOwnershipDocumentResponse> documents
    ) {
        this(
                id,
                attemptNumber,
                status,
                farmProfileStatus,
                submittedAt,
                null,
                null,
                null,
                null,
                documents,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static FarmOwnershipSubmissionResponse from(
            FarmOwnershipSubmission submission,
            FarmProfile.FarmProfileStatus farmProfileStatus
    ) {
        return new FarmOwnershipSubmissionResponse(
                submission.getId(),
                submission.getAttemptNumber(),
                submission.getStatus(),
                farmProfileStatus,
                submission.getSubmittedAt(),
                submission.getReviewer() == null
                        ? null
                        : submission.getReviewer().getId(),
                submission.getReviewer() == null
                        ? null
                        : submission.getReviewer().getName(),
                submission.getReviewedAt(),
                submission.getRejectionReason(),
                submission.getDocuments()
                        .stream()
                        .map(FarmOwnershipDocumentResponse::from)
                        .toList(),
                submission.getFarmNameSnapshot(),
                submission.getRepresentativeNameSnapshot(),
                submission.getFarmAddressSnapshot(),
                submission.getCityCountySnapshot(),
                submission.getBusinessRegistrationNumberSnapshot(),
                submission.getFarmAreaPyeongSnapshot()
        );
    }
}
