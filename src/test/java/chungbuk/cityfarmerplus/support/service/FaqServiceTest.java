package chungbuk.cityfarmerplus.support.service;

import chungbuk.cityfarmerplus.support.dto.FaqResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FaqServiceTest {

    private final FaqService service = new FaqService();

    @Test
    void returnsAllFaqsInThePublishedOrder() {
        var responses = service.getAll();

        assertThat(responses)
                .hasSize(6)
                .extracting(FaqResponse::category, FaqResponse::question)
                .containsExactly(
                        tuple("회원", "교육을 받기 전에도 가입할 수 있나요?"),
                        tuple("교육", "교육 이수증은 어떤 파일로 제출하나요?"),
                        tuple("지원", "같은 시간의 여러 공고에 지원할 수 있나요?"),
                        tuple("매칭", "농가가 수락하면 바로 매칭되나요?"),
                        tuple("농가", "작성한 공고는 바로 공개되나요?"),
                        tuple("인건비", "서비스에서 인건비를 결제하나요?")
                );
        assertThat(responses)
                .allSatisfy(response -> assertThat(response.answer()).isNotBlank());
    }
}
