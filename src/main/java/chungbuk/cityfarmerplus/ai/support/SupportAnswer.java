package chungbuk.cityfarmerplus.ai.support;

public record SupportAnswer(
        String category,
        String answer,
        boolean officialConfirmationRequired
) {
}
