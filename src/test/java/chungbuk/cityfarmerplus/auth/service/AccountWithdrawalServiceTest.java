package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountWithdrawalServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountDataCleaner firstCleaner;

    @Mock
    private AccountDataCleaner secondCleaner;

    private AccountWithdrawalService withdrawalService;

    @BeforeEach
    void setUp() {
        withdrawalService = new AccountWithdrawalService(
                userRepository,
                passwordEncoder,
                List.of(firstCleaner, secondCleaner)
        );
    }

    @Test
    void matchingPasswordCleansFeatureDataBeforeMarkingAccountWithdrawn() {
        User user = user(User.AccountStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encoded-password"))
                .thenReturn(true);

        withdrawalService.withdraw(1L, "password123!");

        assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.WITHDRAWN);
        InOrder order = inOrder(firstCleaner, secondCleaner);
        order.verify(firstCleaner).clean(1L);
        order.verify(secondCleaner).clean(1L);
        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    void suspendedAccountCannotUseTheActiveAccountWithdrawalFlow() {
        User user = user(User.AccountStatus.SUSPENDED);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> withdrawalService.withdraw(1L, "password123!"))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_WITHDRAWAL_NOT_ALLOWED");

        assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.SUSPENDED);
        verifyNoInteractions(passwordEncoder, firstCleaner, secondCleaner);
    }

    @Test
    void zeroRegisteredCleanersStillAllowsWithdrawal() {
        AccountWithdrawalService serviceWithoutCleaners =
                new AccountWithdrawalService(
                        userRepository,
                        passwordEncoder,
                        List.of()
                );
        User user = user(User.AccountStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encoded-password"))
                .thenReturn(true);

        serviceWithoutCleaners.withdraw(1L, "password123!");

        assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.WITHDRAWN);
    }

    @Test
    void wrongPasswordDoesNotCleanDataOrChangeAccountStatus() {
        User user = user(User.AccountStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password"))
                .thenReturn(false);

        assertThatThrownBy(() -> withdrawalService.withdraw(1L, "wrong-password"))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INVALID_PASSWORD");

        assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
        verifyNoInteractions(firstCleaner, secondCleaner);
    }

    @Test
    void alreadyWithdrawnAccountIsRejectedUsingLatestDatabaseState() {
        User user = user(User.AccountStatus.WITHDRAWN);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> withdrawalService.withdraw(1L, "password123!"))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_WITHDRAWAL_NOT_ALLOWED");

        verify(passwordEncoder, never()).matches("password123!", "encoded-password");
        verifyNoInteractions(firstCleaner, secondCleaner);
    }

    @Test
    void cleanerFailurePreventsAccountStatusChange() {
        User user = user(User.AccountStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encoded-password"))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("cleanup failed"))
                .when(firstCleaner).clean(1L);

        assertThatThrownBy(() -> withdrawalService.withdraw(1L, "password123!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cleanup failed");

        assertThat(user.getAccountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
        verify(secondCleaner, never()).clean(1L);
    }

    private User user(User.AccountStatus status) {
        User user = User.register(
                "urban_user",
                "encoded-password",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "accountStatus", status);
        return user;
    }
}
