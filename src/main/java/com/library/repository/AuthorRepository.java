package com.library.repository;

import com.library.domain.Author;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // Derived query - used by BookService.addBook() to find-or-create an author.
    Optional<Author> findByName(String name);
}
