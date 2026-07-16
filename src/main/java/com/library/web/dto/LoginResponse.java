package com.library.web.dto;

public record LoginResponse(String token, long expiresIn) {
}
