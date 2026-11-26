package com.example.english_app_cdcntt.support;

import com.example.english_app_cdcntt.entity.User;

/**
 * Test fixture for accounts. {@link User#create} requires an email because it is the recovery
 * channel, so fixtures derive a deterministic one from the username; pass the four-argument
 * overload when a test needs a specific mailbox.
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static User create(String username, String name, String password) {
        return User.create(username, name, username + "@example.test", password);
    }

    public static User create(String username, String name, String email, String password) {
        return User.create(username, name, email, password);
    }
}
