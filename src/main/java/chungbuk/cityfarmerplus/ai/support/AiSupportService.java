package chungbuk.cityfarmerplus.ai.support;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiSupportService {

    private final UserRepository userRepository;
    private final SupportInquiryRepository inquiryRepository;
    private final SupportAnswerGenerator generator;

    @Transactional
    public SupportMessageResponse send(Long userId, SupportMessageRequest request) {
        User user = getActiveUser(userId);
        String question = request.message().trim();
        SupportAnswer answer = generator.answer(question);
        return SupportMessageResponse.from(
                inquiryRepository.save(SupportInquiry.create(user, question, answer))
        );
    }

    public PageResponse<SupportMessageResponse> getMine(Long userId, int page, int size) {
        getActiveUser(userId);
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        return PageResponse.from(
                inquiryRepository.findByUserId(userId, pageable),
                SupportMessageResponse::from
        );
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        return user;
    }
}
