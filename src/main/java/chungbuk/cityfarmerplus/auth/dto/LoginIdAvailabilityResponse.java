package chungbuk.cityfarmerplus.auth.dto;

public record LoginIdAvailabilityResponse(
        String loginId,
        boolean available
) {
}
