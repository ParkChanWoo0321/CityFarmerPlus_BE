package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveAccountVerifierTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void activeAccountWithMatchingRoleIsValid() {
        User user = urbanFarmer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ActiveAccountVerifier verifier = new ActiveAccountVerifier(userRepository);

        assertThat(verifier.isValid(1L, "URBAN_FARMER")).isTrue();
    }

    @Test
    void withdrawnAccountIsInvalid() {
        User user = urbanFarmer();
        user.withdraw();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ActiveAccountVerifier verifier = new ActiveAccountVerifier(userRepository);

        assertThat(verifier.isValid(1L, "URBAN_FARMER")).isFalse();
    }

    @Test
    void suspendedAccountIsInvalid() {
        User user = urbanFarmer();
        ReflectionTestUtils.setField(
                user,
                "accountStatus",
                User.AccountStatus.SUSPENDED
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ActiveAccountVerifier verifier = new ActiveAccountVerifier(userRepository);

        assertThat(verifier.isValid(1L, "URBAN_FARMER")).isFalse();
    }

    @Test
    void tokenRoleDifferentFromCurrentAccountRoleIsInvalid() {
        User user = urbanFarmer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ActiveAccountVerifier verifier = new ActiveAccountVerifier(userRepository);

        assertThat(verifier.isValid(1L, "FARM")).isFalse();
    }

    private User urbanFarmer() {
        return User.register(
                "urban_1",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }
}
