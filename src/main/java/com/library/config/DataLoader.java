package com.library.config;

import com.library.domain.Loan;
import com.library.repository.BookRepository;
import com.library.repository.BorrowerRepository;
import com.library.repository.LoanRepository;
import java.time.LocalDate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Author/Book/Borrower reference data comes from Flyway's V2__seed_data.sql, which
// runs in every environment. This runner only adds one demo *active loan* so
// GET /api/loans/mine has something to show - and it must never run in prod.
@Component
@Profile("!prod")
public class DataLoader implements ApplicationRunner {

    private static final int LOAN_PERIOD_DAYS = 14;

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final BorrowerRepository borrowerRepository;

    public DataLoader(LoanRepository loanRepository, BookRepository bookRepository,
            BorrowerRepository borrowerRepository) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.borrowerRepository = borrowerRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (loanRepository.count() > 0) {
            return; // idempotent - already seeded on a previous run
        }
        bookRepository.findByIsbn("978-0-261-10221-7").ifPresent(hobbit ->
                borrowerRepository.findByEmail("anna@library.nl").ifPresent(anna -> {
                    LocalDate today = LocalDate.now();
                    loanRepository.save(new Loan(hobbit, anna, today, today.plusDays(LOAN_PERIOD_DAYS)));
                }));
    }
}
