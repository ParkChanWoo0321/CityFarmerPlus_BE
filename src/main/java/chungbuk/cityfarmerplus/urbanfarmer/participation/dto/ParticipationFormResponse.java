package chungbuk.cityfarmerplus.urbanfarmer.participation.dto;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;
import chungbuk.cityfarmerplus.urbanfarmer.profile.entity.UrbanFarmerProfile;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ParticipationFormResponse(
        int programYear,
        ParticipationFormStatus status,
        NextAction nextAction,
        List<String> editableFields,
        Long applicationId,
        Long applicationVersion,
        Boolean agriculturalBusinessRegistered,
        String applicationNote,
        String rejectionReason,
        Long reviewedByUserId,
        Instant submittedAt,
        Instant reviewedAt,
        Instant cancelledAt,
        Long profileId,
        Long profileVersion,
        Integer experienceCount,
        String experienceNotes,
        Long workPreferenceId,
        Long workPreferenceVersion,
        List<ChungbukCityCounty> preferredRegions,
        List<DayOfWeek> availableDays,
        List<String> availableWorkTypes,
        LocalDate preferredStartDate,
        LocalDate preferredEndDate,
        Boolean canTravel,
        String workPreferenceNotes
) {

    public static ParticipationFormResponse from(
            int programYear,
            ParticipationApplication application,
            UrbanFarmerProfile profile,
            UrbanFarmerWorkPreference preference
    ) {
        ParticipationFormStatus formStatus = application == null
                ? ParticipationFormStatus.NOT_STARTED
                : ParticipationFormStatus.valueOf(application.getStatus().name());
        return new ParticipationFormResponse(
                programYear,
                formStatus,
                nextAction(formStatus),
                editableFields(formStatus),
                application == null ? null : application.getId(),
                application == null ? null : application.getVersion(),
                application != null
                        ? application.isAgriculturalBusinessRegistered()
                        : profile == null ? null : profile.isAgriculturalBusinessRegistered(),
                application == null ? null : application.getApplicationNote(),
                application == null ? null : application.getRejectionReason(),
                application == null || application.getReviewedBy() == null
                        ? null
                        : application.getReviewedBy().getId(),
                application == null ? null : application.getSubmittedAt(),
                application == null ? null : application.getReviewedAt(),
                application == null ? null : application.getCancelledAt(),
                profile == null ? null : profile.getId(),
                profile == null ? null : profile.getVersion(),
                profile == null ? null : profile.getExperienceCount(),
                profile == null ? null : profile.getNotes(),
                preference == null ? null : preference.getId(),
                preference == null ? null : preference.getVersion(),
                preference == null ? List.of() : List.copyOf(preference.getPreferredRegions()),
                preference == null ? List.of() : List.copyOf(preference.getAvailableDays()),
                preference == null ? List.of() : List.copyOf(preference.getAvailableWorkTypes()),
                preference == null ? null : preference.getPreferredStartDate(),
                preference == null ? null : preference.getPreferredEndDate(),
                preference == null ? null : preference.isCanTravel(),
                preference == null ? null : preference.getNotes()
        );
    }

    public enum ParticipationFormStatus {
        NOT_STARTED,
        DRAFT,
        SUBMITTED,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    public enum NextAction {
        SUBMIT,
        SAVE_PENDING_CHANGES,
        RESUBMIT,
        SAVE_APPROVED_PREFERENCES,
        NONE
    }

    private static NextAction nextAction(ParticipationFormStatus status) {
        return switch (status) {
            case NOT_STARTED, DRAFT -> NextAction.SUBMIT;
            case SUBMITTED -> NextAction.SAVE_PENDING_CHANGES;
            case REJECTED -> NextAction.RESUBMIT;
            case APPROVED -> NextAction.SAVE_APPROVED_PREFERENCES;
            case CANCELLED -> NextAction.NONE;
        };
    }

    private static List<String> editableFields(ParticipationFormStatus status) {
        if (status == ParticipationFormStatus.CANCELLED) {
            return List.of();
        }
        if (status == ParticipationFormStatus.APPROVED) {
            return List.of(
                    "experienceCount",
                    "experienceNotes",
                    "preferredRegions",
                    "availableDays",
                    "availableWorkTypes",
                    "preferredStartDate",
                    "preferredEndDate",
                    "canTravel",
                    "workPreferenceNotes"
            );
        }
        return List.of(
                "agriculturalBusinessRegistered",
                "experienceCount",
                "experienceNotes",
                "preferredRegions",
                "availableDays",
                "availableWorkTypes",
                "preferredStartDate",
                "preferredEndDate",
                "canTravel",
                "workPreferenceNotes",
                "applicationNote"
        );
    }
}
