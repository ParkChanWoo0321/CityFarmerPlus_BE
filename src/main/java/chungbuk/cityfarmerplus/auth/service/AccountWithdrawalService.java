package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountWithdrawalService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final List<AccountDataCleaner> dataCleaners;

    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthException::userNotFound);

        if (!user.canWithdraw()) {
            throw AuthException.withdrawalNotAllowed();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw AuthException.invalidPassword();
        }

        dataCleaners.forEach(cleaner -> cleaner.clean(userId));
        user.withdraw();
    }
}
