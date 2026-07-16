package com.library.web;

import com.library.service.BookService;
import com.library.web.dto.AddBookRequest;
import com.library.web.dto.BookDto;
import com.library.web.dto.RenameBookRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    // Constructor injection - no @Autowired field injection.
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // Controllers call only services - never repositories directly.
    @GetMapping
    public Page<BookDto> getCatalogue(Pageable page) {
        return bookService.getCatalogue(page);
    }

    @GetMapping("/{id}")
    public BookDto getBook(@PathVariable Long id) {
        return bookService.getBook(id);
    }

    @GetMapping("/search")
    public List<BookDto> search(@RequestParam String title) {
        return bookService.searchByTitle(title);
    }

    @GetMapping("/overdue")
    public List<BookDto> overdue(@RequestParam String author) {
        return bookService.findOverdueByAuthor(author);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookDto addBook(@Valid @RequestBody AddBookRequest request) {
        return bookService.addBook(request);
    }

    @PatchMapping("/{id}/title")
    public BookDto renameBook(@PathVariable Long id, @Valid @RequestBody RenameBookRequest request) {
        return bookService.renameBook(id, request.title());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }
}
