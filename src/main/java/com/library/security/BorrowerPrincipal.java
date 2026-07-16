package com.library.security;

import com.library.domain.Borrower;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// Wraps the Borrower entity so Spring Security's authentication machinery
// (AuthenticationManager, UserDetailsService) never has to know about JPA.
public class BorrowerPrincipal implements UserDetails {

    private final Borrower borrower;

    public BorrowerPrincipal(Borrower borrower) {
        this.borrower = borrower;
    }

    public Long getId() {
        return borrower.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + borrower.getRole().name()));
    }

    @Override
    public String getPassword() {
        return borrower.getPassword();
    }

    @Override
    public String getUsername() {
        return borrower.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
