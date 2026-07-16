package com.library.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.domain.Book;
import com.library.domain.Borrower;
import com.library.domain.Loan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

// @DataJpaTest - slices in JPA repositories only, runs against a real (embedded H2)
// database with Flyway's V1/V2 migrations applied, and rolls back after each test.
@DataJpaTest
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Test
    void findByTitleContaining_matchesPartialTitle() {
        List<Book> results = bookRepository.findByTitleContaining("Hobbit");

        assertThat(results).extracting(Book::getTitle).containsExactly("The Hobbit");
    }

    @Test
    void findByAuthorName_matchesAcrossRelationship() {
        List<Book> results = bookRepository.findByAuthorName("Frank Herbert");

        assertThat(results).extracting(Book::getTitle).containsExactly("Dune");
    }

    @Test
    void findByIsbn_returnsOptionalPresentOrEmpty() {
        Optional<Book> found = bookRepository.findByIsbn("978-0-441-17271-9");
        Optional<Book> missing = bookRepository.findByIsbn("does-not-exist");

        assertThat(found).isPresent();
        assertThat(missing).isEmpty();
    }

    @Test
    void findAll_isPaginated() {
        Page<Book> page = bookRepository.findAll(PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findOverdueByAuthor_returnsOnlyOverdueUnreturnedLoansForThatAuthor() {
        Book dune = bookRepository.findByIsbn("978-0-441-17271-9").orElseThrow();
        Borrower anna = borrowerRepository.findByEmail("anna@library.nl").orElseThrow();
        loanRepository.save(new Loan(dune, anna, LocalDate.now().minusDays(20), LocalDate.now().minusDays(6)));

        List<Book> overdue = bookRepository.findOverdueByAuthor("Frank Herbert", LocalDate.now());

        assertThat(overdue).extracting(Book::getTitle).containsExactly("Dune");
    }
}
