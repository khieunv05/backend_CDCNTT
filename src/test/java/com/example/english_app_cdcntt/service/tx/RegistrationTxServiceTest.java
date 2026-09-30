package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link RegistrationTxService}.
 *
 * <p>No Spring context, no database: the repository and the {@link PasswordEncoder} are Mockito
 * mocks, so the tests assert the hashing contract rather than any real encoding algorithm.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationTxServiceTest {

    private static final String USERNAME = "alice";
    private static final String RAW_PASSWORD = "raw-secret-password";
    private static final String ENCODED_PASSWORD = "$2a$12$encoded-password-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegistrationTxService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationTxService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("createUser encodes the raw password and saves a user holding that hash")
    void createUser_encodesRawPasswordAndSavesHashedUser() {
        // Given
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        service.createUser(USERNAME, RAW_PASSWORD);

        // Then
        verify(passwordEncoder).encode(RAW_PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo(USERNAME);
        assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);
    }

    @Test
    @DisplayName("createUser never persists a user holding the raw password")
    void createUser_neverSavesRawPassword() {
        // Given
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        service.createUser(USERNAME, RAW_PASSWORD);

        // Then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(saved.getPassword()).doesNotContain(RAW_PASSWORD);
        assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);
        verify(passwordEncoder, never()).encode(ENCODED_PASSWORD);
    }

    @Test
    @DisplayName("createUser completes without exception on the happy path and saves exactly one user")
    void createUser_happyPath_completesWithoutException() {
        // Given
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When / Then
        assertThatCode(() -> service.createUser(USERNAME, RAW_PASSWORD))
                .doesNotThrowAnyException();

        verify(userRepository).save(any(User.class));
    }
}
