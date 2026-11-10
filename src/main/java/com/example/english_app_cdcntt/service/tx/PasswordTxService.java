package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import com.example.english_app_cdcntt.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write transaction of {@code POST /api/auth/change-password}: update the BCrypt hash and
 * revoke every refresh token of the account in one commit. The token deletion is what turns a
 * password change into "all other sessions are logged out"; keeping both writes in one
 * transaction guarantees the client never sees a success while an old session is still alive.
 */
@Service
public class PasswordTxService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public PasswordTxService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void changePasswordAndRevokeSessions(Long userId, String encodedPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        user.changePassword(encodedPassword);
        refreshTokenRepository.deleteByUser_Id(userId);
    }
}
