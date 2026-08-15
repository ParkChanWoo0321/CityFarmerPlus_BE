package chungbuk.cityfarmerplus.common.web;

import chungbuk.cityfarmerplus.auth.exception.AuthException;
import org.springframework.security.core.Authentication;

public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static Long id(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw AuthException.invalidAuthentication();
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw AuthException.invalidAuthentication();
        }
    }
}
