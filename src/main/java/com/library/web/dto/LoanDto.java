package com.library.web.dto;

import java.time.LocalDate;

// DTO at the boundary - used by @PostAuthorize("returnObject.borrowerEmail == authentication.name")
// in LoanService.getLoan(), so the accessor name must be exactly "borrowerEmail".
public record LoanDto(
        Long id,
        Long bookId,
        String bookTitle,
        String borrowerEmail,
        LocalDate loanDate,
        LocalDate dueDate,
        boolean returned) {
}
