package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.dto.UserProfileDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short write transaction of PUT {@code /api/users/me}: load the account, change the display
 * name, and let JPA dirty checking flush at commit. Username, password and tokens are untouched.
 */
@Service
public class UserProfileTxService {

    private final UserRepository userRepository;

    public UserProfileTxService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserProfileDto rename(Long userId, String newName) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        user.changeName(newName);
        return new UserProfileDto(user.getId(), user.getUsername(), user.getName());
    }
}
