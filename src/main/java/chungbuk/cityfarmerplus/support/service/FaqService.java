package chungbuk.cityfarmerplus.support.service;

import chungbuk.cityfarmerplus.support.dto.FaqResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FaqService {

    private static final List<FaqResponse> FAQS = List.of(
            new FaqResponse("회원", "교육을 받기 전에도 가입할 수 있나요?", "네. 회원가입은 가능하지만 담당자가 교육 이수증을 승인하기 전에는 공고에 지원할 수 없습니다."),
            new FaqResponse("교육", "교육 이수증은 어떤 파일로 제출하나요?", "PDF, JPG, JPEG, PNG 파일을 제출할 수 있습니다. 제출한 파일은 담당자가 확인합니다."),
            new FaqResponse("지원", "같은 시간의 여러 공고에 지원할 수 있나요?", "네. 여러 공고에 지원할 수 있지만 같은 공고에는 중복 지원할 수 없습니다."),
            new FaqResponse("매칭", "농가가 수락하면 바로 매칭되나요?", "아닙니다. 농가 의견은 담당자가 참고하며 최종 매칭은 담당자가 확정합니다."),
            new FaqResponse("농가", "작성한 공고는 바로 공개되나요?", "아닙니다. 담당자 검토와 승인을 받은 공고만 도시농부에게 공개됩니다."),
            new FaqResponse("인건비", "서비스에서 인건비를 결제하나요?", "아닙니다. 공고의 인건비 정보만 안내하며 실제 지급과 결제는 서비스 밖에서 당사자 간에 처리합니다.")
    );

    public List<FaqResponse> getAll() {
        return FAQS;
    }
}
