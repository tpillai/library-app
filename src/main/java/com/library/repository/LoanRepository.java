package com.library.repository;

import com.library.domain.Loan;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    // JPQL - entity-oriented, refactor-safe (field renames are caught at startup
    // via query validation) and returns managed Loan entities. Prefer this by default.
    @Query("select l from Loan l where l.borrower.email = :email and l.returned = false")
    List<Loan> findActiveByBorrower(@Param("email") String email);

    // Native - same question, raw SQL. Prefer this only when you need a DB-specific
    // feature (window functions, hints, vendor function) that JPQL cannot express.
    @Query(value = "select * from loan where borrower_id = "
                 + "(select id from borrower where email = :email) "
                 + "and returned = false",
           nativeQuery = true)
    List<Loan> findActiveByBorrowerNative(@Param("email") String email);

    // Derived query - used by LoanService.lend() to enforce the "book not already
    // on loan" rule without pulling entities into memory.
    boolean existsByBookIsbnAndReturnedFalse(String isbn);

    // Derived query - used by LoanService.lend() to enforce the "fewer than 5
    // active loans" rule.
    long countByBorrowerEmailAndReturnedFalse(String email);

    // Derived query - used by LibraryHealthIndicator to report overdue loan count.
    long countByReturnedFalseAndDueDateBefore(LocalDate date);
}
