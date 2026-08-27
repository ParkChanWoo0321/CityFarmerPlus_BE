package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingAccessServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FarmProfileRepository farmProfileRepository;
    @Mock
    private User farmOwner;
    @Mock
    private FarmProfile farmProfile;

    @Test
    void savedProfileIsEnoughForPostingWriteAccessBeforeOwnershipApproval() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(farmOwner));
        when(farmOwner.isActive()).thenReturn(true);
        when(farmOwner.getUserType()).thenReturn(User.UserType.FARM);
        when(farmProfileRepository.findByOwnerIdForUpdate(1L))
                .thenReturn(Optional.of(farmProfile));

        FarmProfile result = new JobPostingAccessService(
                userRepository,
                farmProfileRepository
        ).requireFarmProfileForUpdate(1L);

        assertThat(result).isSameAs(farmProfile);
        verify(farmProfile, never()).getStatus();
    }

    @Test
    void ownershipApprovalIsStillRequiredForCandidateAndWorkManagement() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(farmOwner));
        when(farmOwner.isActive()).thenReturn(true);
        when(farmOwner.getUserType()).thenReturn(User.UserType.FARM);
        when(farmProfileRepository.findByOwnerIdForUpdate(1L))
                .thenReturn(Optional.of(farmProfile));
        when(farmProfile.getStatus()).thenReturn(FarmProfile.FarmProfileStatus.DRAFT);

        JobPostingAccessService service = new JobPostingAccessService(
                userRepository,
                farmProfileRepository
        );

        assertThatThrownBy(() -> service.requireApprovedFarmForUpdate(1L))
                .extracting("code")
                .isEqualTo("FARM_APPROVAL_REQUIRED");
    }
}
