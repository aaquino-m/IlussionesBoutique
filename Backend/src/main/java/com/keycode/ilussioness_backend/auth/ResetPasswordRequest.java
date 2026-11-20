package com.keycode.ilussioness_backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 4096) String token,
        @NotBlank @Size(max = 128) String newPassword
) {
}
