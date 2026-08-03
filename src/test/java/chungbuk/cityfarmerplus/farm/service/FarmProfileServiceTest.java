package chungbuk.cityfarmerplus.farm.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileCreateRequest;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmProfileServiceTest {

    @Mock
    private FarmProfileRepository farmProfileRepository;

    @Mock
    private UserRepository userRepository;

    private FarmProfileService farmProfileService;

    @BeforeEach
    void setUp() {
        farmProfileService = new FarmProfileService(
                farmProfileRepository,
                userRepository
        );
    }

    @Test
    void activeFarmAccountCreatesNormalizedDraftProfile() {
        User farmOwner = user(1L, User.UserType.FARM, User.AccountStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmOwner));
        when(farmProfileRepository.existsByOwnerId(1L)).thenReturn(false);
        when(farmProfileRepository.saveAndFlush(any(FarmProfile.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0), 100L));

        FarmProfileResponse response = farmProfileService.create(1L, createRequest());

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.cityCounty()).isEqualTo(ChungbukCityCounty.CHUNGJU);
        assertThat(response.crops()).containsExactly("사과", "Apple", "복숭아");
        assertThat(response.contactNumber()).isEqualTo("01012345678");
        assertThat(response.businessRegistrationNumber()).isEqualTo("1234567890");
        assertThat(response.status()).isEqualTo(FarmProfile.FarmProfileStatus.DRAFT);

        ArgumentCaptor<FarmProfile> profileCaptor =
                ArgumentCaptor.forClass(FarmProfile.class);
        verify(farmProfileRepository).saveAndFlush(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getOwner().getId()).isEqualTo(1L);
    }

    @Test
    void duplicateProfileIsRejected() {
        User farmOwner = user(1L, User.UserType.FARM, User.AccountStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmOwner));
        when(farmProfileRepository.existsByOwnerId(1L)).thenReturn(true);

        assertThatThrownBy(() -> farmProfileService.create(1L, createRequest()))
                .isInstanceOf(FarmProfileException.class)
                .extracting("code")
                .isEqualTo("FARM_PROFILE_ALREADY_EXISTS");

        verify(farmProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonFarmDatabaseAccountIsRejectedEvenWhenJwtRoleWasFarm() {
        User urbanFarmer = user(
                2L,
                User.UserType.URBAN_FARMER,
                User.AccountStatus.ACTIVE
        );
        when(userRepository.findById(2L)).thenReturn(Optional.of(urbanFarmer));

        assertThatThrownBy(() -> farmProfileService.create(2L, createRequest()))
                .isInstanceOf(FarmProfileException.class)
                .extracting("code")
                .isEqualTo("FARM_ROLE_REQUIRED");

        verifyNoInteractions(farmProfileRepository);
    }

    @Test
    void inactiveFarmAccountIsRejected() {
        User suspendedFarm = user(
                3L,
                User.UserType.FARM,
                User.AccountStatus.SUSPENDED
        );
        when(userRepository.findById(3L)).thenReturn(Optional.of(suspendedFarm));

        assertThatThrownBy(() -> farmProfileService.create(3L, createRequest()))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INACTIVE_ACCOUNT");

        verifyNoInteractions(farmProfileRepository);
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> farmProfileService.create(99L, createRequest()))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("USER_NOT_FOUND");

        verifyNoInteractions(farmProfileRepository);
    }

    @Test
    void databaseConstraintRaceIsReturnedAsDataConflict() {
        User farmOwner = user(1L, User.UserType.FARM, User.AccountStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmOwner));
        when(farmProfileRepository.existsByOwnerId(1L)).thenReturn(false);
        when(farmProfileRepository.saveAndFlush(any(FarmProfile.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate profile"));

        assertThatThrownBy(() -> farmProfileService.create(1L, createRequest()))
                .isInstanceOf(FarmProfileException.class)
                .extracting("code")
                .isEqualTo("FARM_PROFILE_DATA_CONFLICT");
    }

    @Test
    void getMineReturnsOnlyTheAuthenticatedOwnersProfile() {
        User farmOwner = user(1L, User.UserType.FARM, User.AccountStatus.ACTIVE);
        FarmProfile profile = profile(farmOwner, 100L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmOwner));
        when(farmProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(profile));

        FarmProfileResponse response = farmProfileService.getMine(1L);

        assertThat(response.id()).isEqualTo(100L);
        verify(farmProfileRepository).findByOwnerId(1L);
    }

    @Test
    void getMineRejectsMissingProfile() {
        User farmOwner = user(1L, User.UserType.FARM, User.AccountStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmOwner));
        when(farmProfileRepository.findByOwnerId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> farmProfileService.getMine(1L))
                .isInstanceOf(FarmProfileException.class)
                .extracting("code")
                .isEqualTo("FARM_PROFILE_NOT_FOUND");
    }

    private FarmProfileCreateRequest createRequest() {
        return new FarmProfileCreateRequest(
                " 충주 사과농원 ",
                " 홍길동 ",
                "010-1234-5678",
                " 충청북도 충주시 예시로 1 ",
                ChungbukCityCounty.CHUNGJU,
                List.of(" 사과 ", "사과", " Apple ", "apple", " 복숭아 "),
                " 사과 재배와 수확 작업을 합니다. ",
                "123-45-67890"
        );
    }

    private User user(Long id, User.UserType type, User.AccountStatus status) {
        User user = User.register(
                "user_" + id,
                "encoded-password",
                "사용자 " + id,
                type
        );
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "accountStatus", status);
        return user;
    }

    private FarmProfile profile(User owner, Long id) {
        return persisted(FarmProfile.createDraft(
                owner,
                "충주 사과농원",
                "홍길동",
                "01012345678",
                "충청북도 충주시 예시로 1",
                ChungbukCityCounty.CHUNGJU,
                List.of("사과", "복숭아"),
                "사과 재배와 수확 작업을 합니다.",
                "1234567890"
        ), id);
    }

    private FarmProfile persisted(FarmProfile profile, Long id) {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        ReflectionTestUtils.setField(profile, "id", id);
        ReflectionTestUtils.setField(profile, "createdAt", now);
        ReflectionTestUtils.setField(profile, "updatedAt", now);
        return profile;
    }
}
