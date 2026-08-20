package chungbuk.cityfarmerplus.education.dto;

import chungbuk.cityfarmerplus.education.entity.EducationCourse;

import java.time.Instant;

public record EducationCourseResponse(
        Long id,
        String title,
        String description,
        int requiredHours,
        String externalApplicationUrl,
        boolean mandatory,
        boolean active,
        int displayOrder,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static EducationCourseResponse from(EducationCourse course) {
        return new EducationCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getRequiredHours(),
                course.getExternalApplicationUrl(),
                course.isMandatory(),
                course.isActive(),
                course.getDisplayOrder(),
                course.getVersion(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}
