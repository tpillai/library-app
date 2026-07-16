package com.library.web;

import com.library.service.LoanException;
import com.library.service.LoanService;
import com.library.web.dto.LendRequest;
import com.library.web.dto.LoanDto;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    // Constructor injection - no @Autowired field injection.
    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    // Controllers call only services - never repositories directly. The borrower is
    // always the authenticated caller, never a value taken from the request body.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanDto lend(@Valid @RequestBody LendRequest request, Principal principal) throws LoanException {
        return loanService.lend(request.isbn(), principal.getName());
    }

    @PostMapping("/{id}/return")
    public LoanDto returnBook(@PathVariable Long id) {
        return loanService.returnBook(id);
    }

    @GetMapping("/{id}")
    public LoanDto getLoan(@PathVariable Long id) {
        return loanService.getLoan(id);
    }

    @GetMapping("/mine")
    public List<LoanDto> mine(Principal principal) {
        return loanService.getMyLoans(principal.getName());
    }
}
