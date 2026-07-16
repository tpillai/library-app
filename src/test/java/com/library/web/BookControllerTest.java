package com.library.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.service.BookService;
import com.library.web.dto.AddBookRequest;
import com.library.web.dto.BookDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// @WebMvcTest - slices in only the web layer; BookService is mocked so no JPA/DB is involved.
@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookService bookService;

    @Test
    @WithMockUser(roles = "MEMBER")
    void getCatalogue_returnsPagedBooks() throws Exception {
        BookDto book = new BookDto(1L, "The Hobbit", "978-0-261-10221-7", "J.R.R. Tolkien");
        given(bookService.getCatalogue(any())).willReturn(new PageImpl<>(List.of(book), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("The Hobbit"));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void getBook_returnsSingleBook() throws Exception {
        BookDto book = new BookDto(1L, "Dune", "978-0-441-17271-9", "Frank Herbert");
        given(bookService.getBook(1L)).willReturn(book);

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("978-0-441-17271-9"));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void search_delegatesToServiceWithTitleParam() throws Exception {
        given(bookService.searchByTitle("Hob")).willReturn(
                List.of(new BookDto(1L, "The Hobbit", "978-0-261-10221-7", "J.R.R. Tolkien")));

        mockMvc.perform(get("/api/books/search").param("title", "Hob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("The Hobbit"));
    }

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void addBook_returns201WithCreatedBook() throws Exception {
        AddBookRequest request = new AddBookRequest("Dune", "978-0-441-17271-9", "Frank Herbert");
        BookDto created = new BookDto(2L, "Dune", "978-0-441-17271-9", "Frank Herbert");
        given(bookService.addBook(any())).willReturn(created);

        mockMvc.perform(post("/api/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Dune"));
    }

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void deleteBook_returns204() throws Exception {
        mockMvc.perform(delete("/api/books/1").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
