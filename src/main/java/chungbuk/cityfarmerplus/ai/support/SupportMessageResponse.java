package chungbuk.cityfarmerplus.ai.support;

import java.time.Instant;

public record SupportMessageResponse(
        Long id,
        String question,
        String category,
        String answer,
        boolean officialConfirmationRequired,
        Instant createdAt
) {

    public static SupportMessageResponse from(SupportInquiry inquiry) {
        return new SupportMessageResponse(
                inquiry.getId(),
                inquiry.getQuestion(),
                inquiry.getCategory(),
                inquiry.getAnswer(),
                inquiry.isOfficialConfirmationRequired(),
                inquiry.getCreatedAt()
        );
    }
}
