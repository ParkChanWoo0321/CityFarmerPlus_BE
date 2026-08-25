package chungbuk.cityfarmerplus.ai.jobposting;

public interface JobPostingTextGenerator {

    AiJobPostingPreviewResponse generate(AiJobPostingPreviewRequest request);
}
