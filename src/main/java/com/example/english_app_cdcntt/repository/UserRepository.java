package com.example.english_app_cdcntt.repository;

import com.example.english_app_cdcntt.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** {@code username} uses the migration's binary collation, so lookup is case-sensitive. */
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
