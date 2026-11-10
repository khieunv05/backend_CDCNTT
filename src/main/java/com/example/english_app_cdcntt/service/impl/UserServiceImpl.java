package com.example.english_app_cdcntt.service.impl;

import com.example.english_app_cdcntt.dto.UserProfileDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.UserService;
import com.example.english_app_cdcntt.service.tx.UserProfileTxService;
import org.springframework.stereotype.Service;

/**
 * Read is a plain repository lookup; the rename write happens in {@link UserProfileTxService} so the
 * HTTP layer never runs inside a transaction (same pattern as {@code AuthServiceImpl}).
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserProfileTxService userProfileTxService;

    public UserServiceImpl(UserRepository userRepository, UserProfileTxService userProfileTxService) {
        this.userRepository = userRepository;
        this.userProfileTxService = userProfileTxService;
    }

    @Override
    public UserProfileDto getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        return toDto(user);
    }

    @Override
    public UserProfileDto updateName(String username, String newName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        return userProfileTxService.rename(user.getId(), newName.trim());
    }

    private static UserProfileDto toDto(User user) {
        return new UserProfileDto(user.getId(), user.getUsername(), user.getName());
    }
}
