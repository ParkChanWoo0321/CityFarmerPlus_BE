package chungbuk.cityfarmerplus.ai.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedSupportAnswerGeneratorTest {

    private final RuleBasedSupportAnswerGenerator generator =
            new RuleBasedSupportAnswerGenerator();

    @Test
    void refusesToHandleSensitivePaymentInformation() {
        SupportAnswer answer = generator.answer("계좌로 인건비를 송금해 주세요");

        assertThat(answer.officialConfirmationRequired()).isTrue();
        assertThat(answer.answer()).contains("계좌번호", "공식 담당자");
    }

    @Test
    void explainsEducationApprovalRequirement() {
        SupportAnswer answer = generator.answer("교육 이수증은 어떻게 제출하나요?");

        assertThat(answer.category()).isEqualTo("교육");
        assertThat(answer.answer()).contains("8시간", "승인 전");
        assertThat(answer.officialConfirmationRequired()).isFalse();
    }

    @Test
    void classifiesFarmPostingCreationBeforeTheBroadPostingKeyword() {
        SupportAnswer answer = generator.answer("농가 모집 공고 작성은 어떻게 하나요?");

        assertThat(answer.category()).isEqualTo("농가 공고");
        assertThat(answer.answer()).contains("농가 소유 증빙", "담당자가 승인");
        assertThat(answer.officialConfirmationRequired()).isFalse();
    }

    @Test
    void classifiesUrbanFarmerApplicationEvenWhenFarmIsMentioned() {
        SupportAnswer answer = generator.answer("농가 공고에 지원했다가 취소할 수 있나요?");

        assertThat(answer.category()).isEqualTo("공고 지원");
        assertThat(answer.answer()).contains("최종 매칭 확정 전", "취소");
    }

    @Test
    void classifiesWorkSafetyBeforeTheBroadPostingKeyword() {
        SupportAnswer answer = generator.answer("공고 작업에 필요한 안전 복장을 알려 주세요");

        assertThat(answer.category()).isEqualTo("근무 준비");
        assertThat(answer.answer()).contains("작업 장갑", "안전 지시");
    }

    @Test
    void unknownPolicyQuestionRequiresOfficialConfirmation() {
        SupportAnswer answer = generator.answer("비가 오면 행사는 어떻게 되나요?");

        assertThat(answer.category()).isEqualTo("일반 안내");
        assertThat(answer.officialConfirmationRequired()).isTrue();
        assertThat(answer.answer()).contains("공식 담당자 확인");
    }
}
