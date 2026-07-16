package com.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AddBookRequest(
        @NotBlank String title,
        @NotBlank String isbn,
        @NotBlank String authorName) {
}
