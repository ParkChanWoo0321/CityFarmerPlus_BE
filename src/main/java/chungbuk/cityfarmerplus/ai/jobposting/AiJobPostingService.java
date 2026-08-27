package chungbuk.cityfarmerplus.ai.jobposting;

import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingAccessService;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingScheduleValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiJobPostingService {

    private final JobPostingAccessService accessService;
    private final JobPostingScheduleValidator scheduleValidator;
    private final JobPostingTextGenerator generator;

    public AiJobPostingPreviewResponse preview(
            Long farmUserId,
            AiJobPostingPreviewRequest request
    ) {
        accessService.requireFarmProfile(farmUserId);
        scheduleValidator.validate(
                request.workDate(),
                request.startTime(),
                request.endTime()
        );
        try {
            return generator.generate(request);
        } catch (IllegalArgumentException exception) {
            throw JobPostingException.invalidDetails(exception.getMessage());
        }
    }
}
