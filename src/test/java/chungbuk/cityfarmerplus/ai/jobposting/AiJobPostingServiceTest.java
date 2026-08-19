package chungbuk.cityfarmerplus.ai.jobposting;

import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingAccessService;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingScheduleValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobPostingServiceTest {

    @Mock
    private JobPostingAccessService accessService;

    @Mock
    private JobPostingScheduleValidator scheduleValidator;

    @Mock
    private JobPostingTextGenerator generator;

    @InjectMocks
    private AiJobPostingService service;

    @Test
    void approvedFarmReceivesPreviewAfterScheduleValidation() {
        AiJobPostingPreviewRequest request = validRequest();
        AiJobPostingPreviewResponse expected = previewResponse();
        when(generator.generate(request)).thenReturn(expected);

        AiJobPostingPreviewResponse actual = service.preview(15L, request);

        assertThat(actual).isSameAs(expected);
        InOrder order = inOrder(accessService, scheduleValidator, generator);
        order.verify(accessService).requireApprovedFarm(15L);
        order.verify(scheduleValidator).validate(
                request.workDate(),
                request.startTime(),
                request.endTime()
        );
        order.verify(generator).generate(request);
    }

    @Test
    void unapprovedFarmCannotInvokeScheduleValidationOrGenerator() {
        AiJobPostingPreviewRequest request = validRequest();
        when(accessService.requireApprovedFarm(15L))
                .thenThrow(JobPostingException.farmApprovalRequired());

        assertThatThrownBy(() -> service.preview(15L, request))
                .isInstanceOf(JobPostingException.class)
                .extracting("code")
                .isEqualTo("FARM_APPROVAL_REQUIRED");

        verifyNoInteractions(scheduleValidator, generator);
    }

    @Test
    void invalidScheduleCannotInvokeGenerator() {
        AiJobPostingPreviewRequest request = validRequest();
        doThrow(JobPostingException.pastWorkDate())
                .when(scheduleValidator)
                .validate(request.workDate(), request.startTime(), request.endTime());

        assertThatThrownBy(() -> service.preview(15L, request))
                .isInstanceOf(JobPostingException.class)
                .extracting("code")
                .isEqualTo("PAST_WORK_DATE");

        verifyNoInteractions(generator);
    }

    @Test
    void generatorInputFailureUsesJobPostingErrorContract() {
        AiJobPostingPreviewRequest request = validRequest();
        when(generator.generate(request))
                .thenThrow(new IllegalArgumentException("생성 입력이 올바르지 않습니다."));

        assertThatThrownBy(() -> service.preview(15L, request))
                .isInstanceOf(JobPostingException.class)
                .hasMessage("생성 입력이 올바르지 않습니다.")
                .extracting("code")
                .isEqualTo("INVALID_JOB_POSTING_DETAILS");
    }

    private AiJobPostingPreviewRequest validRequest() {
        return new AiJobPostingPreviewRequest(
                "감자",
                "수확",
                LocalDate.of(2099, 8, 20),
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                3,
                "농장 입구",
                null,
                null
        );
    }

    private AiJobPostingPreviewResponse previewResponse() {
        return new AiJobPostingPreviewResponse(
                "감자 수확 작업자를 모집합니다",
                "감자 수확 작업을 함께할 도시농부를 모집합니다.",
                "작업 장갑",
                "안전거리를 유지해 주세요.",
                "농가의 설명을 먼저 들어 주세요.",
                "RULE_BASED_V1"
        );
    }
}
