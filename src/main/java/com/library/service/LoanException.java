package com.library.service;

// Checked exception - forces LoanService.lend() callers to handle the business
// rule violations explicitly, and is named in @Transactional(rollbackFor = ...)
// because checked exceptions do not roll back a Spring transaction by default.
public class LoanException extends Exception {

    public LoanException(String message) {
        super(message);
    }
}
