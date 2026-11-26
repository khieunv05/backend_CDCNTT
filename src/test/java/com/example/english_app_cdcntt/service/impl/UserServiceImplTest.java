package com.example.english_app_cdcntt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.english_app_cdcntt.dto.UserProfileDto;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.exception.InvalidCredentialsException;
import com.example.english_app_cdcntt.repository.UserRepository;
import com.example.english_app_cdcntt.service.tx.UserProfileTxService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileTxService userProfileTxService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, userProfileTxService);
    }

    @Test
    @DisplayName("getProfile maps id, username and display name of the authenticated account")
    void getProfileMapsEntity() {
        User user = mock(User.class);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(7L);
        when(user.getUsername()).thenReturn("alice");
        when(user.getName()).thenReturn("Alice Nguyen");

        assertThat(userService.getProfile("alice"))
                .isEqualTo(new UserProfileDto(7L, "alice", "Alice Nguyen"));
    }

    @Test
    @DisplayName("getProfile rejects a token whose account no longer exists")
    void getProfileRejectsMissingAccount() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile("ghost"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("updateName trims the name and delegates the write to the transaction service")
    void updateNameTrimsAndDelegates() {
        User user = mock(User.class);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(7L);
        UserProfileDto updated = new UserProfileDto(7L, "alice", "Alice mới");
        when(userProfileTxService.rename(7L, "Alice mới")).thenReturn(updated);

        assertThat(userService.updateName("alice", "  Alice mới  ")).isEqualTo(updated);
        verify(userProfileTxService).rename(7L, "Alice mới");
    }

    @Test
    @DisplayName("updateName rejects a token whose account no longer exists")
    void updateNameRejectsMissingAccount() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateName("ghost", "Alice"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
