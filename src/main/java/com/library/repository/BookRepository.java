package com.library.repository;

import com.library.domain.Book;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    // Derived query - Spring Data generates the JPQL from the method name.
    List<Book> findByTitleContaining(String part);

    // Derived query across a relationship (Book -> Author.name).
    List<Book> findByAuthorName(String name);

    // Plain pagination - JpaRepository already provides findAll(Pageable),
    // declared here only to document the feature explicitly.
    @Override
    Page<Book> findAll(Pageable page);

    // "findAllBy" with no predicate is the same result set as findAll(Pageable),
    // but @EntityGraph lets us fetch the author eagerly in the same query to
    // avoid the classic N+1 problem when the catalogue is rendered.
    @EntityGraph(attributePaths = "author")
    Page<Book> findAllBy(Pageable page);

    // Optional, never null - callers must handle the "no such isbn" case explicitly.
    Optional<Book> findByIsbn(String isbn);

    // JPQL - entity-oriented query, walks the Book -> Loan association.
    @Query("select b from Book b join b.loans l "
         + "where b.author.name = :name and l.dueDate < :today and l.returned = false")
    List<Book> findOverdueByAuthor(@Param("name") String name, @Param("today") LocalDate today);
}
