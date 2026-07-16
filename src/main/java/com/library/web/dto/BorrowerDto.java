package com.library.web.dto;

// DTO at the boundary - the Borrower entity (and its password hash) never leaves the service layer.
public record BorrowerDto(Long id, String email, String role) {
}
