package com.library.repository;

import com.library.domain.Borrower;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    // Used by the UserDetailsService bean to load the principal at login time.
    Optional<Borrower> findByEmail(String email);
}
