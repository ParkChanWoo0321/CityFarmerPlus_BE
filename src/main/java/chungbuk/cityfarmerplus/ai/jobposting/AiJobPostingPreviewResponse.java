package chungbuk.cityfarmerplus.ai.jobposting;

public record AiJobPostingPreviewResponse(
        String title,
        String description,
        String supplies,
        String precautions,
        String beginnerGuide,
        String generator
) {
}
