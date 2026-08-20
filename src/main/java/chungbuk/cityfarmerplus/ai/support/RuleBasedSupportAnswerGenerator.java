package chungbuk.cityfarmerplus.ai.support;

import org.springframework.stereotype.Component;

@Component
public class RuleBasedSupportAnswerGenerator implements SupportAnswerGenerator {

    @Override
    public SupportAnswer answer(String message) {
        String normalized = message.trim().toLowerCase();
        if (containsAny(normalized, "계좌", "송금", "결제", "정산", "지급")) {
            return new SupportAnswer(
                    "민감 업무",
                    "이 서비스는 결제·송금·정산을 처리하지 않습니다. 금전 관련 내용은 공고의 농가 또는 공식 담당자에게 직접 확인해 주세요. 계좌번호 같은 개인정보는 채팅에 입력하지 마세요.",
                    true
            );
        }
        if (containsAny(normalized, "교육", "이수증", "수료증")) {
            return new SupportAnswer(
                    "교육",
                    "교육 안내에서 필수 과정을 확인한 뒤 외부 교육 사이트에서 수강하세요. 8시간 이상의 교육을 이수하고 PDF 또는 이미지 이수증을 제출하면 담당자가 검토합니다. 승인 전에는 공고 지원이 제한됩니다.",
                    false
            );
        }
        if (isFarmPostingQuestion(normalized)) {
            return new SupportAnswer(
                    "농가 공고",
                    "농가 소유 증빙 승인을 받은 뒤 작업 날짜·시간·인원·장소·인건비를 입력해 공고 초안을 만드세요. 담당자가 승인해야 공고가 공개됩니다.",
                    false
            );
        }
        if (containsAny(normalized, "장갑", "준비", "복장", "안전")) {
            return new SupportAnswer(
                    "근무 준비",
                    "공고와 확정 근무의 작업 안내를 먼저 확인하세요. 기본적으로 작업 장갑, 모자, 편한 작업복과 작업화를 준비하고 농가의 현장 안전 지시를 따라 주세요.",
                    false
            );
        }
        if (containsAny(normalized, "지원", "취소", "공고")) {
            return new SupportAnswer(
                    "공고 지원",
                    "담당자가 승인해 모집 중인 공고만 조회하고 지원할 수 있습니다. 같은 공고에는 한 번만 지원할 수 있고, 최종 매칭 확정 전에는 지원을 취소할 수 있습니다.",
                    false
            );
        }
        return new SupportAnswer(
                "일반 안내",
                "질문의 정책이나 세부 기준이 확정되지 않았을 수 있습니다. 교육, 공고 지원, 농가 공고 작성, 근무 준비 중 궁금한 항목을 조금 더 구체적으로 입력해 주세요. 행정 판단이 필요한 내용은 공식 담당자 확인이 필요합니다.",
                true
        );
    }

    private boolean containsAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFarmPostingQuestion(String message) {
        if (containsAny(
                message,
                "공고 작성",
                "공고 등록",
                "공고 생성",
                "공고 게시",
                "사람 모집",
                "인력 모집"
        )) {
            return true;
        }

        return message.contains("농가")
                && containsAny(message, "모집", "공고")
                && !containsAny(message, "지원", "취소");
    }
}
