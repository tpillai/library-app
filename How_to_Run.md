# How to Run — Use Cases

This walks through every major use case of `library-app` as a request/response
transcript: what you send, what happens inside the code, and exactly what comes
back. Every input/output pair below was captured by actually running the app —
nothing here is hypothetical.

For setup instructions (build, profiles, seed credentials) see
[`README.md`](README.md). This document assumes the app is already running:

```bash
./mvnw spring-boot:run
# app listens on http://localhost:8080 (examples below use 8080)
```

Seed accounts used throughout:

| Email | Password | Role |
|---|---|---|
| `anna@library.nl` | `member123` | MEMBER |
| `bob@library.nl` | `member123` | MEMBER |
| `admin@library.nl` | `admin123` | LIBRARIAN |

---

## 1. Log in as a member

**Who:** anyone with a borrower account.
**Endpoint:** `POST /api/auth/login` — open, no token required.

**Processing steps**
1. `AuthController.login()` receives and validates the `LoginRequest` body (`@Valid`).
2. `AuthService.login()` builds a `UsernamePasswordAuthenticationToken` and hands it
   to Spring Security's `AuthenticationManager`.
3. `DaoAuthenticationProvider` calls the `UserDetailsService` bean
   (`SecurityConfig.userDetailsService`), which loads the `Borrower` via
   `BorrowerRepository.findByEmail(email)` and wraps it in a `BorrowerPrincipal`.
4. The submitted password is checked against the stored BCrypt hash by the
   `PasswordEncoder` bean.
5. On success, `JwtService.generateToken()` builds a JWT: `sub` = email,
   `roles` = `["ROLE_MEMBER"]`, `exp` = now + 15 minutes.
6. `AuthController` returns `{ token, expiresIn }`.

**Input**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"anna@library.nl","password":"member123"}'
```

**Output** — `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbm5hQGxpYnJhcnkubmwiLCJyb2xlcyI6WyJST0xFX01FTUJFUiJdLCJpYXQiOjE3ODQyNDA4NTQsImV4cCI6MTc4NDI0MTc1NH0.yi9efG2H8yjBKm0MvNGQqXcSKUO7EB3bPpGS9MGO1Yo",
  "expiresIn": 900
}
```
Save this token — every use case below sends it as `Authorization: Bearer <token>`.

---

## 2. Log in as a librarian

Same flow as above, different account — the JWT's `roles` claim is what changes.

**Input**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@library.nl","password":"admin123"}'
```

**Output** — `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBsaWJyYXJ5Lm5sIiwicm9sZXMiOlsiUk9MRV9MSUJSQVJJQU4iXSwiaWF0IjoxNzg0MjQwODU0LCJleHAiOjE3ODQyNDE3NTR9.-IWaIqPwaYMA67nsqRk6pSPDB_qwGIZnkk-n-oWBXzU",
  "expiresIn": 900
}
```
Decode the middle segment (base64) and you'll see `"roles":["ROLE_LIBRARIAN"]` instead
of `ROLE_MEMBER` — that claim is what `JwtFilter` turns into Spring Security
authorities on every subsequent request.

---

## 3. Failed login — wrong password

**Processing:** `AuthenticationManager.authenticate()` throws `BadCredentialsException`
before a token is ever built; `GlobalExceptionHandler.handleBadCredentials()` maps it
to a generic 401 (it never reveals *why* — wrong email vs. wrong password look
identical to the caller).

**Input**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"anna@library.nl","password":"wrong"}'
```

**Output** — `401 Unauthorized`
```json
{"timestamp":"2026-07-16T22:27:34.359831Z","status":401,"message":"Invalid credentials"}
```

---

## 4. Browse the paginated catalogue

**Who:** MEMBER.
**Endpoint:** `GET /api/books?page=0&size=2`

**Processing steps**
1. `BookController.getCatalogue()` binds `?page=&size=` to a `Pageable` automatically.
2. `BookService.getCatalogue()` runs inside `@Transactional(readOnly = true)`.
3. It calls `BookRepository.findAllBy(page)`, which carries
   `@EntityGraph(attributePaths = "author")` — the author is fetched in the *same*
   query as the book, avoiding one extra `SELECT` per row (the N+1 problem you'd get
   from plain lazy loading over a page of results).
4. Each `Book` is mapped to a `BookDto` (entity never leaves the service layer).

**Input**
```bash
curl "http://localhost:8080/api/books?page=0&size=2" \
  -H "Authorization: Bearer $ANNA_TOKEN"
```

**Output** — `200 OK`
```json
{
  "content": [
    {"id":1,"title":"The Hobbit","isbn":"978-0-261-10221-7","authorName":"J.R.R. Tolkien"},
    {"id":2,"title":"Dune","isbn":"978-0-441-17271-9","authorName":"Frank Herbert"}
  ],
  "totalElements": 3,
  "totalPages": 2,
  "number": 0,
  "size": 2,
  "first": true,
  "last": false
}
```
(Full response also includes `pageable` and `sort` metadata, trimmed here.)

---

## 5. Get a single book

**Input**
```bash
curl http://localhost:8080/api/books/2 -H "Authorization: Bearer $ANNA_TOKEN"
```

**Output** — `200 OK`
```json
{"id":2,"title":"Dune","isbn":"978-0-441-17271-9","authorName":"Frank Herbert"}
```
**Processing:** `BookService.getBook()` → `BookRepository.findById()` → maps to
`BookDto` or throws `EntityNotFoundException` (→ `404` via `GlobalExceptionHandler`)
if the id doesn't exist.

---

## 6. Search books by title (derived query)

**Processing:** `BookRepository.findByTitleContaining(part)` — a Spring Data
*derived query*, generated entirely from the method name (`title LIKE %part%`), no
`@Query` needed.

**Input**
```bash
curl "http://localhost:8080/api/books/search?title=Dark" -H "Authorization: Bearer $ANNA_TOKEN"
```

**Output** — `200 OK`
```json
[{"id":3,"title":"The Left Hand of Darkness","isbn":"978-0-441-47812-5","authorName":"Ursula K. Le Guin"}]
```

---

## 7. List overdue books by author (librarian report, JPQL)

**Who:** LIBRARIAN only — this is the one `GET /api/books/**` path that does **not**
accept a MEMBER token (checked explicitly in `SecurityConfig` before the general
MEMBER rule).

**Processing:** `BookRepository.findOverdueByAuthor(name, today)` runs the JPQL

```java
select b from Book b join b.loans l
where b.author.name = :name and l.dueDate < :today and l.returned = false
```

— an entity-oriented query across the `Book → Loan` relationship.

**Input**
```bash
curl "http://localhost:8080/api/books/overdue?author=J.R.R.%20Tolkien" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Output** — `200 OK` (empty because nothing is overdue in a fresh seed)
```json
[]
```

---

## 8. Add a new book (librarian)

**Processing steps**
1. `BookController.addBook()` validates the `AddBookRequest` body.
2. `BookService.addBook()` — guarded by `@PreAuthorize("hasRole('LIBRARIAN')")`, a
   second line of defense below the URL-level rule in `SecurityConfig`.
3. `AuthorRepository.findByName()` finds-or-creates the `Author` (find-or-create
   pattern, since author isn't a separate CRUD resource here).
4. A new `Book` is constructed and saved via `BookRepository.save()`.

**Input**
```bash
curl -X POST http://localhost:8080/api/books \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"A Wizard of Earthsea","isbn":"978-0-553-26250-6","authorName":"Ursula K. Le Guin"}'
```

**Output** — `201 Created`
```json
{"id":4,"title":"A Wizard of Earthsea","isbn":"978-0-553-26250-6","authorName":"Ursula K. Le Guin"}
```

---

## 9. Rename a book — dirty-checking demo

**Processing steps**
1. `BookController.renameBook()` → `BookService.renameBook(id, title)`, inside a
   plain `@Transactional` (read-write) method.
2. `BookRepository.findById()` loads a **managed** `Book` entity.
3. `book.setTitle(newTitle)` is called — **there is no `bookRepository.save(book)`
   anywhere in this method.**
4. When the transaction commits, Hibernate compares the managed entity against the
   snapshot it took at load time, sees the `title` field changed, and issues an
   `UPDATE` automatically. This is *dirty checking* — see the comment in
   `BookService.java` right above this line, which also flags the self-invocation
   pitfall (calling `this.renameBook(...)` from inside the same bean would skip the
   `@Transactional` proxy entirely).

**Input**
```bash
curl -X PATCH http://localhost:8080/api/books/2/title \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Dune (50th Anniversary Edition)"}'
```

**Output** — `200 OK`
```json
{"id":2,"title":"Dune (50th Anniversary Edition)","isbn":"978-0-441-17271-9","authorName":"Frank Herbert"}
```

---

## 10. Wrong role attempts a librarian action

**Processing:** `SecurityConfig`'s filter chain checks
`.requestMatchers(HttpMethod.POST, "/api/books").hasRole("LIBRARIAN")` **before** the
request ever reaches `BookController` — the 403 comes from the security filter
chain, not from application code.

**Input**
```bash
curl -X POST http://localhost:8080/api/books \
  -H "Authorization: Bearer $ANNA_TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"x","isbn":"x-1","authorName":"x"}'
```

**Output** — `403 Forbidden` (empty body)

---

## 11. Delete a book (librarian)

**Input**
```bash
curl -X DELETE http://localhost:8080/api/books/4 -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Output** — `204 No Content` (empty body)

Fetching that same id afterwards as a librarian returns `403`, not `404` — a
librarian account has no `ROLE_MEMBER`, and `GET /api/books/**` requires exactly
that role (see [Use case 20](#20-two-different-flavors-of-denied-access) for why
that's `403` and not `401`).

---

## 12. Lend a book

**Who:** MEMBER. The borrower is always the caller's own identity from the JWT
(`principal.getName()`) — the request body only carries the ISBN, never an email, so
one member can never lend a book on another member's behalf.

**Processing steps** — all inside `LoanService.lend()`, `@Transactional(rollbackFor
= LoanException.class)`, the single place these rules are enforced:
1. `BookRepository.findByIsbn(isbn)` — book must exist.
2. `LoanRepository.existsByBookIsbnAndReturnedFalse(isbn)` — book must not already
   be on loan.
3. `BorrowerRepository.findByEmail(email)` — borrower must exist.
4. `LoanRepository.countByBorrowerEmailAndReturnedFalse(email)` — borrower must have
   fewer than 5 active loans.
5. A new `Loan` is saved. `MDC.put("loanId", ...)` tags the log line, an SLF4J
   placeholder-style `log.info(...)` records the event, and two Micrometer counters
   (`library.loans.created`, `library.loans.active`) are incremented.

**Input**
```bash
curl -X POST http://localhost:8080/api/loans \
  -H "Authorization: Bearer $ANNA_TOKEN" -H "Content-Type: application/json" \
  -d '{"isbn":"978-0-441-17271-9"}'
```

**Output** — `201 Created`
```json
{"id":2,"bookId":2,"bookTitle":"Dune (50th Anniversary Edition)","borrowerEmail":"anna@library.nl","loanDate":"2026-07-17","dueDate":"2026-07-31","returned":false}
```

---

## 13. Lend a book that's already on loan

**Input** (bob tries to borrow the same Dune copy anna just took)
```bash
curl -X POST http://localhost:8080/api/loans \
  -H "Authorization: Bearer $BOB_TOKEN" -H "Content-Type: application/json" \
  -d '{"isbn":"978-0-441-17271-9"}'
```

**Output** — `409 Conflict`
```json
{"timestamp":"2026-07-16T22:28:10.268500Z","status":409,"message":"Book 978-0-441-17271-9 is already on loan"}
```
**Processing:** `LoanService.lend()` throws the checked `LoanException`;
`@Transactional(rollbackFor = LoanException.class)` rolls the transaction back
(checked exceptions don't roll back by default in Spring — this is exactly why that
attribute is there); `GlobalExceptionHandler.handleLoan()` maps it to `409`.

---

## 14. Lend a nonexistent book

**Input**
```bash
curl -X POST http://localhost:8080/api/loans \
  -H "Authorization: Bearer $BOB_TOKEN" -H "Content-Type: application/json" \
  -d '{"isbn":"000-not-real"}'
```

**Output** — `409 Conflict`
```json
{"timestamp":"2026-07-16T22:28:10.294187Z","status":409,"message":"No book with isbn 000-not-real"}
```

---

## 15. Borrower already has 5 active loans

Same code path as #13/#14 — `LoanService.lend()` throws `LoanException("... already
has 5 active loans")` once `countByBorrowerEmailAndReturnedFalse` reaches the limit,
same `409` shape. Not reproduced live in this walkthrough because the seed catalogue
only has 4 books; the rule is exercised directly (with mocked repositories) in
`LoanServiceTest.lend_throwsWhenBorrowerHasFiveActiveLoans`.

---

## 16. View your own loan

**Who:** MEMBER, and only *their own* loan.

**Processing:** `LoanService.getLoan(id)` is annotated
`@PostAuthorize("returnObject.borrowerEmail == authentication.name")` — the method
runs and loads the `Loan` *first*, then Spring Security evaluates the SpEL
expression against the **returned** `LoanDto` and the caller's identity. If they
don't match, Spring throws `AccessDeniedException` and the caller gets nothing back
(no exception happens before the DB read has happened, unlike `@PreAuthorize`).

**Input**
```bash
curl http://localhost:8080/api/loans/1 -H "Authorization: Bearer $ANNA_TOKEN"
```

**Output** — `200 OK` (loan 1 belongs to anna)
```json
{"id":1,"bookId":1,"bookTitle":"The Hobbit","borrowerEmail":"anna@library.nl","loanDate":"2026-07-17","dueDate":"2026-07-31","returned":false}
```

---

## 17. Try to view someone else's loan

**Input** (bob requests anna's loan)
```bash
curl http://localhost:8080/api/loans/1 -H "Authorization: Bearer $BOB_TOKEN"
```

**Output** — `403 Forbidden`
```json
{"timestamp":"2026-07-16T22:28:28.620795Z","status":403,"message":"Access denied"}
```

---

## 18. Return a book

**Input**
```bash
curl -X POST http://localhost:8080/api/loans/2/return -H "Authorization: Bearer $ANNA_TOKEN"
```

**Output** — `200 OK`
```json
{"id":2,"bookId":2,"bookTitle":"Dune (50th Anniversary Edition)","borrowerEmail":"anna@library.nl","loanDate":"2026-07-17","dueDate":"2026-07-31","returned":true}
```
**Processing:** `LoanService.returnBook()` loads the `Loan`, calls
`loan.setReturned(true)` (dirty checking again — no explicit `save()`), and
decrements the `library.loans.active` Micrometer counter
(`meterRegistry.counter("library.loans.active").increment(-1)`).

---

## 19. View your active loans

**Input** (after the return above — only the still-active Hobbit loan remains)
```bash
curl http://localhost:8080/api/loans/mine -H "Authorization: Bearer $ANNA_TOKEN"
```

**Output** — `200 OK`
```json
[{"id":1,"bookId":1,"bookTitle":"The Hobbit","borrowerEmail":"anna@library.nl","loanDate":"2026-07-17","dueDate":"2026-07-31","returned":false}]
```
**Processing:** `LoanRepository.findActiveByBorrower(email)` — the JPQL variant
(`select l from Loan l where l.borrower.email = :email and l.returned = false`).
`LoanRepository.findActiveByBorrowerNative(email)` answers the identical question
with raw SQL and exists purely as a side-by-side comparison; it isn't wired to any
endpoint.

---

## 20. Two different flavors of "denied access"

Both look like a blank response with a status code, but they come from different
layers and mean different things:

**No token at all → `403`** (anonymous principal denied by an authorization rule)
```bash
curl http://localhost:8080/api/books
# → 403, no body
```

**Malformed/invalid/expired token → `401`** (`JwtFilter` rejects the token itself,
before authorization is even evaluated)
```bash
curl http://localhost:8080/api/books -H "Authorization: Bearer not.a.valid.jwt"
# → 401, no body
```
**Processing:** `JwtFilter.doFilterInternal()` calls `JwtService.parseClaims()`
inside a `try/catch`; on `JwtException` it sets the response status to 401 directly
and returns — it deliberately does **not** rethrow, per the comment in the filter
("do not throw, so the request doesn't blow up the filter chain").

---

## 21. Health check — public status vs. authenticated detail

**Endpoint:** `GET /actuator/health` — the one actuator path that's `permitAll()`.

**Input (anonymous)**
```bash
curl http://localhost:8080/actuator/health
```
**Output** — `200 OK`
```json
{"status":"UP"}
```

**Input (authenticated)**
```bash
curl http://localhost:8080/actuator/health -H "Authorization: Bearer $ADMIN_TOKEN"
```
**Output** — `200 OK`
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "H2", "validationQuery": "isValid()"}},
    "diskSpace": {"status": "UP", "details": {"total": 399000969216, "free": 106157125632, "threshold": 10485760, "exists": true}},
    "library": {"status": "UP", "details": {"overdueLoans": 0}},
    "ping": {"status": "UP"}
  }
}
```
**Processing:** `management.endpoint.health.show-details: when-authorized` in
`application.yml` means the detail map only appears for an authenticated caller.
The `"library"` entry comes from the custom `LibraryHealthIndicator`, which runs
`LoanRepository.countByReturnedFalseAndDueDateBefore(today)` on every health check.

---

## 22. Metrics endpoint — needs a role nobody is seeded with

**Input**
```bash
curl http://localhost:8080/actuator/metrics -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Output** — `403 Forbidden`, even for the librarian account

**Processing:** `SecurityConfig` requires `ROLE_ACTUATOR` for every `/actuator/**`
path other than `/actuator/health`. `Borrower.role` only ever holds `MEMBER` or
`LIBRARIAN` in this app, so nothing in the seed data can reach `/actuator/metrics` —
by design, this endpoint is reserved for an operational identity that would be
provisioned separately from application borrowers in a real deployment.

---

## Summary table

| # | Use case | Role | Method/Path | Result |
|---|---|---|---|---|
| 1 | Login | any | `POST /api/auth/login` | 200 + JWT |
| 3 | Bad password | any | `POST /api/auth/login` | 401 |
| 4 | Catalogue | MEMBER | `GET /api/books` | 200, paginated |
| 6 | Search | MEMBER | `GET /api/books/search?title=` | 200 |
| 7 | Overdue report | LIBRARIAN | `GET /api/books/overdue?author=` | 200 |
| 8 | Add book | LIBRARIAN | `POST /api/books` | 201 |
| 9 | Rename book | LIBRARIAN | `PATCH /api/books/{id}/title` | 200 |
| 10 | Wrong role | MEMBER | `POST /api/books` | 403 |
| 11 | Delete book | LIBRARIAN | `DELETE /api/books/{id}` | 204 |
| 12 | Lend book | MEMBER | `POST /api/loans` | 201 |
| 13 | Already on loan | MEMBER | `POST /api/loans` | 409 |
| 14 | Unknown ISBN | MEMBER | `POST /api/loans` | 409 |
| 15 | Loan limit reached | MEMBER | `POST /api/loans` | 409 |
| 16 | View own loan | MEMBER | `GET /api/loans/{id}` | 200 |
| 17 | View others' loan | MEMBER | `GET /api/loans/{id}` | 403 |
| 18 | Return book | MEMBER | `POST /api/loans/{id}/return` | 200 |
| 19 | My active loans | MEMBER | `GET /api/loans/mine` | 200 |
| 20 | No / bad token | — | any protected path | 403 / 401 |
| 21 | Health | open/any | `GET /actuator/health` | 200 |
| 22 | Metrics | ACTUATOR only | `GET /actuator/metrics` | 403 |
