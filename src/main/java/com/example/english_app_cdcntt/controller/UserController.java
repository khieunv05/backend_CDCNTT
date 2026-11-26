package com.example.english_app_cdcntt.controller;

import com.example.english_app_cdcntt.config.UserPrincipal;
import com.example.english_app_cdcntt.dto.SuccessResponse;
import com.example.english_app_cdcntt.dto.UserProfileDto;
import com.example.english_app_cdcntt.form.UpdateNameForm;
import com.example.english_app_cdcntt.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile of the authenticated account. Both routes are matched by {@code /api/**} and therefore
 * require a Bearer access token; the account id/username always comes from the verified token, never
 * from the request. GET is returned bare like the other read endpoints; PUT uses the
 * {@code {"message","data"}} success envelope of writes.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    static final String UPDATE_NAME_SUCCESS_MESSAGE = "Cập nhật tên thành công";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    UserProfileDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getProfile(principal.username());
    }

    @PutMapping("/me")
    SuccessResponse<UserProfileDto> updateName(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateNameForm form) {
        return new SuccessResponse<>(UPDATE_NAME_SUCCESS_MESSAGE,
                userService.updateName(principal.username(), form.name()));
    }
}
