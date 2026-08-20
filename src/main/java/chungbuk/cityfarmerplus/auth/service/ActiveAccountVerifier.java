package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActiveAccountVerifier {

    private final UserRepository userRepository;

    public boolean isValid(Long userId, String tokenRole) {
        return userRepository.findById(userId)
                .filter(user -> user.isActive()
                        && user.getUserType().name().equals(tokenRole))
                .isPresent();
    }
}
