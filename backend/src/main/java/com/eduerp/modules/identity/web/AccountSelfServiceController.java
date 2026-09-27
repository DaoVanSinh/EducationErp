package com.eduerp.modules.identity.web;

import com.eduerp.modules.identity.AccountPrincipal;
import com.eduerp.modules.identity.dto.ChangePasswordRequest;
import com.eduerp.modules.identity.dto.ForgotPasswordRequest;
import com.eduerp.modules.identity.dto.ProfileUpdateRequest;
import com.eduerp.modules.identity.dto.ResetPasswordRequest;
import com.eduerp.modules.identity.usecase.ChangePassword;
import com.eduerp.modules.identity.usecase.ForgotPassword;
import com.eduerp.modules.identity.usecase.ResetPassword;
import com.eduerp.modules.identity.usecase.UpdateProfile;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
class AccountSelfServiceController {

    private final ChangePassword changePassword;
    private final ForgotPassword forgotPassword;
    private final ResetPassword resetPassword;
    private final UpdateProfile updateProfile;

    AccountSelfServiceController(ChangePassword changePassword, ForgotPassword forgotPassword,
            ResetPassword resetPassword, UpdateProfile updateProfile) {
        this.changePassword = changePassword;
        this.forgotPassword = forgotPassword;
        this.resetPassword = resetPassword;
        this.updateProfile = updateProfile;
    }

    @PostMapping("/change-password")
    void changePassword(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        changePassword.execute(principal.accountId(), request);
    }

    @PostMapping("/forgot-password")
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        forgotPassword.execute(request);
    }

    @PostMapping("/reset-password")
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPassword.execute(request);
    }

    @PatchMapping("/profile")
    void updateProfile(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        updateProfile.execute(principal.accountId(), request);
    }
}
