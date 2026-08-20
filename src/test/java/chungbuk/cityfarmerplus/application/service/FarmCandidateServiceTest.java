package chungbuk.cityfarmerplus.application.service;

import chungbuk.cityfarmerplus.application.dto.FarmOpinionRequest;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmCandidateServiceTest {

    @Mock
    private JobPostingAccessService accessService;
    @Mock
    private JobPostingRepository postingRepository;
    @Mock
    private JobApplicationRepository applicationRepository;

    private FarmCandidateService service;

    @BeforeEach
    void setUp() {
        service = new FarmCandidateService(
                accessService,
                postingRepository,
                applicationRepository
        );
    }

    @Test
    void rejectsOpinionUpdateAfterPostingHasClosedOnScreen() {
        JobPosting posting = mock(JobPosting.class);
        FarmProfile farm = mock(FarmProfile.class);
        User owner = mock(User.class);
        when(postingRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(posting));
        when(posting.getFarmProfile()).thenReturn(farm);
        when(farm.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(1L);
        when(posting.isAcceptingApplications(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.updateOpinion(
                1L,
                10L,
                20L,
                new FarmOpinionRequest(
                        JobApplication.FarmOpinion.PREFERRED,
                        "경력 우대"
                )
        ))
                .isInstanceOf(JobPostingException.class)
                .hasMessageContaining("작업 시작 전");

        verifyNoInteractions(applicationRepository);
    }

    @Test
    void exposesCandidatePhoneNumberAndKeepsItNullWhenUnregistered() {
        JobPosting posting = mock(JobPosting.class);
        FarmProfile farm = mock(FarmProfile.class);
        User owner = mock(User.class);
        JobApplication application = mock(JobApplication.class);
        JobApplication applicationWithoutPhone = mock(JobApplication.class);
        User candidate = mock(User.class);
        User candidateWithoutPhone = mock(User.class);
        when(postingRepository.findById(10L)).thenReturn(Optional.of(posting));
        when(posting.getFarmProfile()).thenReturn(farm);
        when(farm.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(1L);
        when(applicationRepository.findByJobPostingIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(application, applicationWithoutPhone));
        when(application.getUrbanFarmer()).thenReturn(candidate);
        when(applicationWithoutPhone.getUrbanFarmer()).thenReturn(candidateWithoutPhone);
        when(candidate.getPhoneNumber()).thenReturn("01012345678");

        var candidates = service.getCandidates(1L, 10L);

        assertThat(candidates)
                .extracting(response -> response.phoneNumber())
                .containsExactly("01012345678", null);
    }
}
