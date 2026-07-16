package com.library.service;

import com.library.domain.Book;
import com.library.domain.Borrower;
import com.library.domain.Loan;
import com.library.repository.BookRepository;
import com.library.repository.BorrowerRepository;
import com.library.repository.LoanRepository;
import com.library.web.dto.LoanDto;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

    private static final int LOAN_PERIOD_DAYS = 14;
    private static final int MAX_ACTIVE_LOANS = 5;

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final BorrowerRepository borrowerRepository;
    private final MeterRegistry meterRegistry;

    // Constructor injection - no @Autowired field injection.
    public LoanService(LoanRepository loanRepository, BookRepository bookRepository,
            BorrowerRepository borrowerRepository, MeterRegistry meterRegistry) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.borrowerRepository = borrowerRepository;
        this.meterRegistry = meterRegistry;
    }

    // Single place that enforces the lending rules - controllers/other services
    // must go through this method rather than saving a Loan themselves.
    @Transactional(rollbackFor = LoanException.class)
    public LoanDto lend(String isbn, String borrowerEmail) throws LoanException {
        long start = System.currentTimeMillis();

        Book book = bookRepository.findByIsbn(isbn)
                .orElseThrow(() -> new LoanException("No book with isbn " + isbn));

        if (loanRepository.existsByBookIsbnAndReturnedFalse(isbn)) {
            throw new LoanException("Book " + isbn + " is already on loan");
        }

        Borrower borrower = borrowerRepository.findByEmail(borrowerEmail)
                .orElseThrow(() -> new LoanException("No borrower with email " + borrowerEmail));

        long activeLoans = loanRepository.countByBorrowerEmailAndReturnedFalse(borrowerEmail);
        if (activeLoans >= MAX_ACTIVE_LOANS) {
            throw new LoanException("Borrower " + borrowerEmail + " already has " + MAX_ACTIVE_LOANS + " active loans");
        }

        LocalDate today = LocalDate.now();
        Loan loan = loanRepository.save(new Loan(book, borrower, today, today.plusDays(LOAN_PERIOD_DAYS)));

        long elapsed = System.currentTimeMillis() - start;
        MDC.put("loanId", loan.getId().toString());
        try {
            // Do NOT log the full email if it contains PII in production - log the
            // borrower id instead. Kept as email here for readability in this demo.
            log.info("book_lent isbn={} borrower={} durationMs={}", isbn, borrowerEmail, elapsed);
        } finally {
            MDC.remove("loanId");
        }

        meterRegistry.counter("library.loans.created").increment();
        meterRegistry.counter("library.loans.active").increment();

        return toDto(loan);
    }

    @Transactional
    public LoanDto returnBook(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new EntityNotFoundException("No loan with id " + loanId));
        loan.setReturned(true); // dirty checking - no explicit save() needed

        meterRegistry.counter("library.loans.active").increment(-1);

        log.info("book_returned loanId={}", loanId);
        return toDto(loan);
    }

    @Transactional(readOnly = true)
    @PostAuthorize("returnObject.borrowerEmail == authentication.name")
    public LoanDto getLoan(Long id) {
        return loanRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("No loan with id " + id));
    }

    @Transactional(readOnly = true)
    public List<LoanDto> getMyLoans(String borrowerEmail) {
        return loanRepository.findActiveByBorrower(borrowerEmail).stream().map(this::toDto).toList();
    }

    private LoanDto toDto(Loan loan) {
        return new LoanDto(
                loan.getId(),
                loan.getBook().getId(),
                loan.getBook().getTitle(),
                loan.getBorrower().getEmail(),
                loan.getLoanDate(),
                loan.getDueDate(),
                loan.isReturned());
    }
}
