package chungbuk.cityfarmerplus.admin.farm.service;

import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipSubmissionRepository;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFarmOwnershipServiceTest {

    @Mock
    private FarmProfileRepository farmProfileRepository;

    @Mock
    private FarmOwnershipSubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminFarmOwnershipService service;

    @Test
    void omittedStatusReturnsEveryFarmProfile() {
        when(farmProfileRepository.findAllByOrderByUpdatedAtDesc())
                .thenReturn(List.of());

        assertThat(service.list(null)).isEmpty();

        verify(farmProfileRepository).findAllByOrderByUpdatedAtDesc();
        verify(farmProfileRepository, never())
                .findAllByStatusOrderByUpdatedAtDesc(
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void suppliedStatusReturnsOnlyMatchingFarmProfiles() {
        FarmProfile.FarmProfileStatus status =
                FarmProfile.FarmProfileStatus.PENDING_REVIEW;
        when(farmProfileRepository.findAllByStatusOrderByUpdatedAtDesc(status))
                .thenReturn(List.of());

        assertThat(service.list(status)).isEmpty();

        verify(farmProfileRepository)
                .findAllByStatusOrderByUpdatedAtDesc(status);
        verify(farmProfileRepository, never())
                .findAllByOrderByUpdatedAtDesc();
    }
}
