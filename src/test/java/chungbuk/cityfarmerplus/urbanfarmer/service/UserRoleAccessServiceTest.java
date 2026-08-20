package chungbuk.cityfarmerplus.urbanfarmer.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleAccessServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void databaseRoleNotJwtClaimDeterminesUrbanFarmerAccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                user(User.UserType.FARM, User.AccountStatus.ACTIVE)
        ));
        UserRoleAccessService service = new UserRoleAccessService(userRepository);

        assertThatThrownBy(() -> service.requireUrbanFarmer(1L))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("URBAN_FARMER_ROLE_REQUIRED");
    }

    @Test
    void suspendedDatabaseAccountIsRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                user(User.UserType.URBAN_FARMER, User.AccountStatus.SUSPENDED)
        ));
        UserRoleAccessService service = new UserRoleAccessService(userRepository);

        assertThatThrownBy(() -> service.requireUrbanFarmer(1L))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INACTIVE_ACCOUNT");
    }

    @Test
    void writeAccessRechecksTheUrbanFarmerWhileHoldingTheUserLock() {
        User urbanFarmer = user(
                User.UserType.URBAN_FARMER,
                User.AccountStatus.ACTIVE
        );
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(urbanFarmer));
        UserRoleAccessService service = new UserRoleAccessService(userRepository);

        assertThat(service.requireUrbanFarmerForUpdate(1L)).isSameAs(urbanFarmer);
        verify(userRepository).findByIdForUpdate(1L);
    }

    private User user(User.UserType type, User.AccountStatus status) {
        User user = type == User.UserType.CENTER_ADMIN
                ? User.registerCenterAdmin("admin_1", "encoded", "담당자")
                : User.register("user_1", "encoded", "사용자", type);
        ReflectionTestUtils.setField(user, "accountStatus", status);
        return user;
    }
}
