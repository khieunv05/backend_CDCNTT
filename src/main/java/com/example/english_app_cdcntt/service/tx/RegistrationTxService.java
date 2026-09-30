package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.DuplicateUsernameException;
import com.example.english_app_cdcntt.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short write transaction of §5.2:234 (register): pre-check username → BCrypt → save. The
 * DB UNIQUE constraint stays the last line of defense — a lost race surfaces as
 * {@code DataIntegrityViolationException} after this transaction rolled back, and the caller
 * ({@code AuthService}) maps only the {@code uk_users_username} violation to 409.
 */
@Service
public class RegistrationTxService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationTxService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void createUser(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException();
        }
        userRepository.save(User.create(username, passwordEncoder.encode(rawPassword)));
    }
}
