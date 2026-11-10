package com.example.english_app_cdcntt.controller;

import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.dto.MessageResponse;
import com.example.english_app_cdcntt.form.ChangePasswordForm;
import com.example.english_app_cdcntt.form.LoginForm;
import com.example.english_app_cdcntt.form.LogoutForm;
import com.example.english_app_cdcntt.form.RefreshForm;
import com.example.english_app_cdcntt.form.RegisterForm;
import com.example.english_app_cdcntt.service.AuthService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four public auth endpoints (§4.1 rows 1–4), all permitAll POSTs (§5.3:249), plus the
 * authenticated {@code change-password} route (matched by {@code /api/**}). Error bodies come
 * from {@code GlobalExceptionHandler}; this class only defines the success shapes and the
 * hardcoded success messages of the contract.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REGISTER_SUCCESS_MESSAGE = "Tạo tài khoản thành công";
    static final String LOGOUT_SUCCESS_MESSAGE = "Đăng xuất thành công";
    static final String CHANGE_PASSWORD_SUCCESS_MESSAGE = "Đổi mật khẩu thành công";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse register(@Valid @RequestBody RegisterForm form) {
        authService.register(form.username(), form.password());
        return new MessageResponse(REGISTER_SUCCESS_MESSAGE);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginForm form) {
        return authService.login(form.username(), form.password());
    }

    @PostMapping("/logout")
    MessageResponse logout(@Valid @RequestBody LogoutForm form) {
        authService.logout(form.refreshToken());
        return new MessageResponse(LOGOUT_SUCCESS_MESSAGE);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshForm form) {
        return authService.refresh(form.refreshToken());
    }

    /**
     * Authenticated change-password. The account comes from the verified Bearer token present
     * on the request, never from the body, so a caller can only change their own password.
     */
    @PostMapping("/change-password")
    MessageResponse changePassword(Principal principal, @Valid @RequestBody ChangePasswordForm form) {
        authService.changePassword(principal.getName(), form.currentPassword(), form.newPassword());
        return new MessageResponse(CHANGE_PASSWORD_SUCCESS_MESSAGE);
    }
}
