package com.library.service;

import com.library.domain.Author;
import com.library.domain.Book;
import com.library.repository.AuthorRepository;
import com.library.repository.BookRepository;
import com.library.web.dto.AddBookRequest;
import com.library.web.dto.BookDto;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;

    // Constructor injection - no @Autowired field injection.
    public BookService(BookRepository bookRepository, AuthorRepository authorRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
    }

    @Transactional(readOnly = true) // no dirty-check overhead
    public Page<BookDto> getCatalogue(Pageable page) {
        // N+1 fix: findAllBy carries @EntityGraph(attributePaths = "author"), so the
        // author is fetched in the same query instead of one extra query per book.
        return bookRepository.findAllBy(page).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public BookDto getBook(Long id) {
        return bookRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("No book with id " + id));
    }

    @Transactional(readOnly = true)
    public List<BookDto> searchByTitle(String part) {
        return bookRepository.findByTitleContaining(part).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BookDto> findOverdueByAuthor(String authorName) {
        return bookRepository.findOverdueByAuthor(authorName, LocalDate.now()).stream()
                .map(this::toDto)
                .toList();
    }

    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional
    public BookDto addBook(AddBookRequest request) {
        Author author = authorRepository.findByName(request.authorName())
                .orElseGet(() -> authorRepository.save(new Author(request.authorName())));
        Book book = new Book(request.title(), request.isbn(), author);
        return toDto(bookRepository.save(book));
    }

    @Transactional // dirty checking saves renameBook without an explicit save()
    public BookDto renameBook(Long id, String newTitle) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No book with id " + id));
        // Dirty checking: Hibernate compares this managed entity against its loaded
        // snapshot at flush time and issues an UPDATE for changed fields - no save() needed.
        book.setTitle(newTitle);
        // Self-invocation pitfall: calling this.renameBook(...) from another method on
        // BookService bypasses the Spring AOP proxy, so @Transactional would be skipped -
        // only calls that go through the injected bean get the transaction.
        return toDto(book);
    }

    @Transactional
    public void deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new EntityNotFoundException("No book with id " + id);
        }
        bookRepository.deleteById(id);
    }

    private BookDto toDto(Book book) {
        return new BookDto(book.getId(), book.getTitle(), book.getIsbn(), book.getAuthor().getName());
    }
}
