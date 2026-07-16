package com.library.web.dto;

// DTO at the boundary - the Book entity (and its lazy Author) never leaves the service layer.
public record BookDto(Long id, String title, String isbn, String authorName) {
}
