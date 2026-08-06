package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.dto.LoginRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User signup(SignupRequest request) {
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        User user = User.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .userType(request.getUserType())
                .build();

        return userRepository.save(user);
    }

    public User login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    public boolean isLoginIdAvailable(String loginId) {
        return !userRepository.existsByLoginId(loginId);
    }
}
