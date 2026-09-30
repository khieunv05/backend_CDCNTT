package com.example.english_app_cdcntt.config;

import com.example.english_app_cdcntt.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads the account used by the JWT filter cross-check (§5.3): the token's {@code sub} must still
 * exist and its {@code uid} must match the DB id. Username lookup is exact and case-sensitive
 * (utf8mb4_0900_bin collation). The exception message never reaches clients — the filter maps it
 * to the generic 401 contract body.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(user -> new UserPrincipal(user.getId(), user.getUsername(), user.getPassword()))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found for token subject"));
    }
}
