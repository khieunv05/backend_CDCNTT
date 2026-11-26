package com.example.english_app_cdcntt.service;

import com.example.english_app_cdcntt.dto.UserProfileDto;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;

/**
 * Account profile of the authenticated user: read the current profile and change the display name.
 * The username comes from the verified Bearer token (principal), never from a request body, so a
 * caller can only read or rename their own account.
 */
public interface UserService {

    /**
     * @throws InvalidCredentialsException when the authenticated username no longer exists
     */
    UserProfileDto getProfile(String username);

    /**
     * Replace the display name and return the updated profile. The name is trimmed before saving.
     *
     * @throws InvalidCredentialsException when the authenticated username no longer exists
     */
    UserProfileDto updateName(String username, String newName);
}
