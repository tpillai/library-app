package com.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameBookRequest(@NotBlank String title) {
}
