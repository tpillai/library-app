package com.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.domain.Author;
import com.library.domain.Book;
import com.library.domain.Borrower;
import com.library.domain.Loan;
import com.library.domain.Role;
import com.library.repository.BookRepository;
import com.library.repository.BorrowerRepository;
import com.library.repository.LoanRepository;
import com.library.web.dto.LoanDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// Plain unit test - no Spring context, business rules verified with Mockito alone.
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BorrowerRepository borrowerRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private LoanService loanService;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, bookRepository, borrowerRepository, meterRegistry);
        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    }

    @Test
    void lend_throwsWhenBookDoesNotExist() {
        when(bookRepository.findByIsbn("missing-isbn")).thenReturn(Optional.empty());

        assertThrows(LoanException.class, () -> loanService.lend("missing-isbn", "anna@library.nl"));
    }

    @Test
    void lend_throwsWhenBookAlreadyOnLoan() {
        Book book = new Book("Dune", "isbn-1", new Author("Frank Herbert"));
        when(bookRepository.findByIsbn("isbn-1")).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIsbnAndReturnedFalse("isbn-1")).thenReturn(true);

        assertThrows(LoanException.class, () -> loanService.lend("isbn-1", "anna@library.nl"));
    }

    @Test
    void lend_throwsWhenBorrowerHasFiveActiveLoans() {
        Book book = new Book("Dune", "isbn-1", new Author("Frank Herbert"));
        Borrower borrower = new Borrower("anna@library.nl", "hash", Role.MEMBER);
        when(bookRepository.findByIsbn("isbn-1")).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIsbnAndReturnedFalse("isbn-1")).thenReturn(false);
        when(borrowerRepository.findByEmail("anna@library.nl")).thenReturn(Optional.of(borrower));
        when(loanRepository.countByBorrowerEmailAndReturnedFalse("anna@library.nl")).thenReturn(5L);

        assertThrows(LoanException.class, () -> loanService.lend("isbn-1", "anna@library.nl"));
    }

    @Test
    void lend_savesLoanAndRecordsMetricsWhenRulesPass() throws LoanException {
        Book book = new Book("Dune", "isbn-1", new Author("Frank Herbert"));
        ReflectionTestUtils.setField(book, "id", 1L);
        Borrower borrower = new Borrower("anna@library.nl", "hash", Role.MEMBER);
        when(bookRepository.findByIsbn("isbn-1")).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIsbnAndReturnedFalse("isbn-1")).thenReturn(false);
        when(borrowerRepository.findByEmail("anna@library.nl")).thenReturn(Optional.of(borrower));
        when(loanRepository.countByBorrowerEmailAndReturnedFalse("anna@library.nl")).thenReturn(0L);
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> {
            Loan loan = invocation.getArgument(0);
            ReflectionTestUtils.setField(loan, "id", 42L);
            return loan;
        });

        LoanDto dto = loanService.lend("isbn-1", "anna@library.nl");

        assertThat(dto.borrowerEmail()).isEqualTo("anna@library.nl");
        assertThat(dto.bookId()).isEqualTo(1L);
        assertThat(dto.returned()).isFalse();
        verify(counter, org.mockito.Mockito.times(2)).increment();
    }

    @Test
    void returnBook_marksLoanReturnedAndDecrementsActiveMetric() {
        Book book = new Book("Dune", "isbn-1", new Author("Frank Herbert"));
        ReflectionTestUtils.setField(book, "id", 1L);
        Borrower borrower = new Borrower("anna@library.nl", "hash", Role.MEMBER);
        Loan loan = new Loan(book, borrower, LocalDate.now().minusDays(1), LocalDate.now().plusDays(13));
        ReflectionTestUtils.setField(loan, "id", 7L);
        when(loanRepository.findById(7L)).thenReturn(Optional.of(loan));

        LoanDto dto = loanService.returnBook(7L);

        assertThat(dto.returned()).isTrue();
        verify(counter).increment(-1);
    }

    @Test
    void getLoan_throwsWhenLoanDoesNotExist() {
        when(loanRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> loanService.getLoan(99L));
    }
}
