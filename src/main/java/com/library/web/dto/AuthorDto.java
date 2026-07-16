package com.library.web.dto;

// DTO at the boundary - the Author entity never leaves the service layer.
public record AuthorDto(Long id, String name) {
}
