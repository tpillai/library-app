package com.library.config;

import com.library.repository.LoanRepository;
import java.time.LocalDate;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

// Custom actuator health indicator - shows up under /actuator/health as "library".
@Component("library")
public class LibraryHealthIndicator implements HealthIndicator {

    private final LoanRepository loanRepository;

    public LibraryHealthIndicator(LoanRepository loanRepository) {
        this.loanRepository = loanRepository;
    }

    @Override
    public Health health() {
        long overdueLoans = loanRepository.countByReturnedFalseAndDueDateBefore(LocalDate.now());
        return Health.up().withDetail("overdueLoans", overdueLoans).build();
    }
}
