package com.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LendRequest(@NotBlank String isbn) {
}
