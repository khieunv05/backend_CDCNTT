package com.example.english_app_cdcntt.controller;

import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.dto.MessageResponse;
import com.example.english_app_cdcntt.form.ChangePasswordForm;
import com.example.english_app_cdcntt.form.ForgotPasswordForm;
import com.example.english_app_cdcntt.form.LoginForm;
import com.example.english_app_cdcntt.form.LogoutForm;
import com.example.english_app_cdcntt.form.RefreshForm;
import com.example.english_app_cdcntt.form.RegisterConfirmForm;
import com.example.english_app_cdcntt.form.RegisterForm;
import com.example.english_app_cdcntt.form.ResetPasswordForm;
import com.example.english_app_cdcntt.service.AuthService;
import com.example.english_app_cdcntt.service.PasswordResetService;
import com.example.english_app_cdcntt.service.RegistrationService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The auth endpoints (§4.1): the public session, registration and recovery POSTs, plus the
 * authenticated {@code change-password} route (matched by {@code /api/**}). Error bodies come from
 * {@code GlobalExceptionHandler}; this class only defines the success shapes and the hardcoded
 * messages of the contract, and every method here calls exactly one service method.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REGISTER_CODE_SENT_MESSAGE = "Mã xác thực đã được gửi tới email";
    static final String REGISTER_SUCCESS_MESSAGE = "Tạo tài khoản thành công";
    static final String FORGOT_PASSWORD_MESSAGE =
            "Nếu email này đã đăng ký, mã xác thực đã được gửi tới email đó";
    static final String RESET_PASSWORD_SUCCESS_MESSAGE = "Đặt lại mật khẩu thành công";
    static final String LOGOUT_SUCCESS_MESSAGE = "Đăng xuất thành công";
    static final String CHANGE_PASSWORD_SUCCESS_MESSAGE = "Đổi mật khẩu thành công";

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, RegistrationService registrationService,
            PasswordResetService passwordResetService) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
    }

    /** Step 1 of registration: the answer says a code was sent, not that a draft exists. */
    @PostMapping("/register")
    MessageResponse register(@Valid @RequestBody RegisterForm form) {
        registrationService.requestRegistration(form.username(), form.name(), form.email(), form.password());
        return new MessageResponse(REGISTER_CODE_SENT_MESSAGE);
    }

    /** Step 2 of registration: only here does the account row appear. */
    @PostMapping("/register/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse confirmRegistration(@Valid @RequestBody RegisterConfirmForm form) {
        registrationService.confirmRegistration(form.email(), form.code());
        return new MessageResponse(REGISTER_SUCCESS_MESSAGE);
    }

    /** Always 200 with the same text, whether or not the email belongs to an account. */
    @PostMapping("/forgot-password")
    MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordForm form) {
        passwordResetService.requestReset(form.email());
        return new MessageResponse(FORGOT_PASSWORD_MESSAGE);
    }

    /** Sets the new password and revokes every session; it deliberately issues no token. */
    @PostMapping("/reset-password")
    MessageResponse resetPassword(@Valid @RequestBody ResetPasswordForm form) {
        passwordResetService.resetPassword(form.email(), form.code(), form.newPassword());
        return new MessageResponse(RESET_PASSWORD_SUCCESS_MESSAGE);
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