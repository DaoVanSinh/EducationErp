package com.eduerp.modules.identity.web;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.InvalidAvatarException;
import com.eduerp.modules.identity.dto.AvatarUploadResponse;
import com.eduerp.modules.identity.dto.ChangePasswordRequest;
import com.eduerp.modules.identity.dto.ForgotPasswordRequest;
import com.eduerp.modules.identity.dto.ProfileUpdateRequest;
import com.eduerp.modules.identity.dto.ResetPasswordRequest;
import com.eduerp.modules.identity.dto.SessionResponse;
import com.eduerp.modules.identity.usecase.ChangePassword;
import com.eduerp.modules.identity.usecase.DownloadAvatar;
import com.eduerp.modules.identity.usecase.ForgotPassword;
import com.eduerp.modules.identity.usecase.GetCurrentSession;
import com.eduerp.modules.identity.usecase.ResetPassword;
import com.eduerp.modules.identity.usecase.UpdateProfile;
import com.eduerp.modules.identity.usecase.UploadAvatar;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/account")
class AccountSelfServiceController {

    private final ChangePassword changePassword;
    private final ForgotPassword forgotPassword;
    private final ResetPassword resetPassword;
    private final UpdateProfile updateProfile;
    private final GetCurrentSession getCurrentSession;
    private final UploadAvatar uploadAvatar;
    private final DownloadAvatar downloadAvatar;

    AccountSelfServiceController(
            ChangePassword changePassword,
            ForgotPassword forgotPassword,
            ResetPassword resetPassword,
            UpdateProfile updateProfile,
            GetCurrentSession getCurrentSession,
            UploadAvatar uploadAvatar,
            DownloadAvatar downloadAvatar) {
        this.changePassword = changePassword;
        this.forgotPassword = forgotPassword;
        this.resetPassword = resetPassword;
        this.updateProfile = updateProfile;
        this.getCurrentSession = getCurrentSession;
        this.uploadAvatar = uploadAvatar;
        this.downloadAvatar = downloadAvatar;
    }

    /** Không gắn @PreAuthorize: ai đăng nhập được thì đương nhiên được biết mình là ai. */
    @GetMapping("/me")
    SessionResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return getCurrentSession.execute(principal.accountId());
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

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AvatarUploadResponse saveAvatar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "remove", defaultValue = "false") boolean remove) {
        byte[] rawBytes = extractBytes(image, remove);
        String contentType = image == null ? null : image.getContentType();
        String avatarUrl = uploadAvatar.execute(principal.accountId(), rawBytes, contentType, remove);
        return new AvatarUploadResponse(avatarUrl);
    }

    @GetMapping("/avatar")
    ResponseEntity<byte[]> readAvatar(@AuthenticationPrincipal AccountPrincipal principal) {
        var result = downloadAvatar.execute(principal.accountId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, IdentityConstants.Avatar.CACHE_CONTROL)
                .body(result.content());
    }

    private static byte[] extractBytes(MultipartFile image, boolean remove) {
        if (remove) {
            return null;
        }
        if (image == null || image.isEmpty()) {
            throw new InvalidAvatarException();
        }
        try {
            return image.getBytes();
        } catch (IOException ex) {
            throw new InvalidAvatarException();
        }
    }
}
