package chungbuk.cityfarmerplus.ai.support;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSupportServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private SupportInquiryRepository inquiryRepository;
    @Mock
    private SupportAnswerGenerator generator;

    private AiSupportService service;

    @BeforeEach
    void setUp() {
        service = new AiSupportService(userRepository, inquiryRepository, generator);
    }

    @Test
    void trimsQuestionGeneratesAnswerAndPersistsInquiryForAuthenticatedUser() {
        User user = activeUser(User.UserType.URBAN_FARMER);
        SupportAnswer generated = new SupportAnswer(
                "교육",
                "교육 이수증을 제출해 주세요.",
                false
        );
        Instant createdAt = Instant.parse("2026-08-20T01:02:03Z");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(generator.answer("교육 이수증 제출 방법"))
                .thenReturn(generated);
        when(inquiryRepository.save(any(SupportInquiry.class)))
                .thenAnswer(invocation -> {
                    SupportInquiry inquiry = invocation.getArgument(0);
                    ReflectionTestUtils.setField(inquiry, "id", 41L);
                    ReflectionTestUtils.setField(inquiry, "createdAt", createdAt);
                    return inquiry;
                });

        SupportMessageResponse response = service.send(
                USER_ID,
                new SupportMessageRequest("  교육 이수증 제출 방법  ")
        );

        ArgumentCaptor<SupportInquiry> inquiryCaptor =
                ArgumentCaptor.forClass(SupportInquiry.class);
        verify(inquiryRepository).save(inquiryCaptor.capture());
        SupportInquiry saved = inquiryCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getQuestion()).isEqualTo("교육 이수증 제출 방법");
        assertThat(saved.getCategory()).isEqualTo("교육");
        assertThat(saved.getAnswer()).isEqualTo("교육 이수증을 제출해 주세요.");
        assertThat(saved.isOfficialConfirmationRequired()).isFalse();
        assertThat(response).isEqualTo(new SupportMessageResponse(
                41L,
                "교육 이수증 제출 방법",
                "교육",
                "교육 이수증을 제출해 주세요.",
                false,
                createdAt
        ));
        verify(generator).answer("교육 이수증 제출 방법");
    }

    @Test
    void readsOnlyTheAuthenticatedUsersHistoryWithStableNewestFirstOrder() {
        User user = activeUser(User.UserType.FARM);
        Instant createdAt = Instant.parse("2026-08-20T02:00:00Z");
        SupportInquiry inquiry = SupportInquiry.create(
                user,
                "공고 작성 방법",
                new SupportAnswer("농가 공고", "공고 작성 안내", false)
        );
        ReflectionTestUtils.setField(inquiry, "id", 51L);
        ReflectionTestUtils.setField(inquiry, "createdAt", createdAt);
        PageRequest pageable = PageRequest.of(
                1,
                5,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(inquiryRepository.findByUserId(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(inquiry), pageable, 6));

        var response = service.getMine(USER_ID, 1, 5);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(6);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.content())
                .containsExactly(new SupportMessageResponse(
                        51L,
                        "공고 작성 방법",
                        "농가 공고",
                        "공고 작성 안내",
                        false,
                        createdAt
                ));
        verify(inquiryRepository).findByUserId(USER_ID, pageable);
    }

    @Test
    void missingUserCannotGenerateOrPersistAnInquiry() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(
                USER_ID,
                new SupportMessageRequest("교육 문의")
        ))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("USER_NOT_FOUND");

        verifyNoInteractions(generator, inquiryRepository);
    }

    @Test
    void inactiveUserCannotReadInquiryHistory() {
        User user = activeUser(User.UserType.URBAN_FARMER);
        user.withdraw();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getMine(USER_ID, 0, 20))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INACTIVE_ACCOUNT");

        verifyNoInteractions(inquiryRepository);
    }

    private User activeUser(User.UserType userType) {
        User user = User.register(
                "support_user",
                "encoded-password",
                "상담 사용자",
                userType
        );
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
