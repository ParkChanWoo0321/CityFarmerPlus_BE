package chungbuk.cityfarmerplus.urbanfarmer.profile.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.profile.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.profile.repository.UrbanFarmerProfileRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrbanFarmerProfileServiceTest {

    @Mock
    private UrbanFarmerProfileRepository profileRepository;

    @Mock
    private UserRoleAccessService accessService;

    private UrbanFarmerProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new UrbanFarmerProfileService(profileRepository, accessService);
    }

    @Test
    void createsAndNormalizesProfileForCurrentUrbanFarmer() {
        User user = urbanFarmer(1L);
        when(accessService.requireUrbanFarmerForUpdate(1L)).thenReturn(user);
        when(profileRepository.existsByUrbanFarmerId(1L)).thenReturn(false);
        when(profileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            UrbanFarmerProfile profile = invocation.getArgument(0);
            ReflectionTestUtils.setField(profile, "id", 10L);
            return profile;
        });

        var response = profileService.create(
                1L,
                new UrbanFarmerProfileRequest(false, 3, "  사과 수확 경험  ")
        );

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.experienceCount()).isEqualTo(3);
        assertThat(response.notes()).isEqualTo("사과 수확 경험");
    }

    @Test
    void duplicateProfileIsRejected() {
        when(accessService.requireUrbanFarmerForUpdate(1L))
                .thenReturn(urbanFarmer(1L));
        when(profileRepository.existsByUrbanFarmerId(1L)).thenReturn(true);

        assertThatThrownBy(() -> profileService.create(
                1L,
                new UrbanFarmerProfileRequest(false, 0, null)
        ))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("URBAN_FARMER_PROFILE_ALREADY_EXISTS");
    }

    @Test
    void getMineDoesNotExposeAnotherUsersProfile() {
        when(accessService.requireUrbanFarmer(1L)).thenReturn(urbanFarmer(1L));
        when(profileRepository.findByUrbanFarmerId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getMine(1L))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("URBAN_FARMER_PROFILE_NOT_FOUND");
    }

    private User urbanFarmer(Long id) {
        User user = User.register(
                "urban_" + id,
                "encoded-password",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
