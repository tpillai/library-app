# Prompt — Generate the Library Spring Boot Application

Paste the entire content below as your first message to a fresh Claude conversation.

---

## What to build

Create a **complete, runnable Spring Boot 3.x application** called `library-app` that
demonstrates every feature from the Day 3 (Spring Data JPA) and Day 4 (Spring Security)
topics using a **single consistent domain**: a small library.

---

## Domain model (use exactly these names)

```
Author   – id (Long, generated), name (String)
Book     – id (Long, generated), title (String), isbn (String, unique),
           author (→ Author, ManyToOne LAZY)
Borrower – id (Long, generated), email (String, unique), password (encoded),
           role (enum: MEMBER | LIBRARIAN)
Loan     – id (Long, generated), book (→ Book, ManyToOne LAZY),
           borrower (→ Borrower, ManyToOne LAZY),
           loanDate (LocalDate), dueDate (LocalDate), returned (boolean)
```

All four entities must be in the same Maven module. Do **not** add entities that are
not listed above.

---

## Project skeleton

```
library-app/
  pom.xml                        ← parent, Java 21, Spring Boot 3.x
  src/main/java/com/library/
    LibraryApplication.java
    config/
      SecurityConfig.java        ← filter chain bean
      JwtConfig.java             ← @ConfigurationProperties("library.jwt")
    domain/
      Author.java  Book.java  Borrower.java  Loan.java
    repository/
      AuthorRepository.java  BookRepository.java
      BorrowerRepository.java  LoanRepository.java
    service/
      BookService.java  LoanService.java  AuthService.java
    web/
      dto/                       ← one DTO per entity (never expose entities directly)
      BookController.java  LoanController.java  AuthController.java
    security/
      JwtFilter.java
      BorrowerPrincipal.java     ← implements UserDetails
  src/main/resources/
    application.yml              ← default (dev profile: H2, show-sql, debug logging)
    application-prod.yml         ← prod overrides (validate, JSON logging)
    db/migration/
      V1__schema.sql             ← Flyway baseline
      V2__seed_data.sql          ← dev seed — @Profile("!prod") guard in Java too
  src/test/java/com/library/
    repository/BookRepositoryTest.java   ← @DataJpaTest
    service/LoanServiceTest.java         ← plain unit test (Mockito)
    web/BookControllerTest.java          ← @WebMvcTest
```

---

## Day 3 features to demonstrate (Spring Data JPA)

### 1 · Spring Data JPA

Implement `BookRepository` with at least:

```java
List<Book> findByTitleContaining(String part);          // derived query
List<Book> findByAuthorName(String name);               // cross-relation derived
Page<Book> findAll(Pageable page);                      // pagination
Optional<Book> findByIsbn(String isbn);                 // Optional, not null

@Query("select b from Book b join b.loans l "
     + "where b.author.name = :name and l.dueDate < :today")
List<Book> findOverdueByAuthor(String name, LocalDate today); // JPQL
```

### 2 · Hibernate integration

In `BookService.renameBook(Long id, String newTitle)`:
- Load the entity, call `setTitle`, return — **no explicit `save()`**.
- Add a comment that names dirty checking.
- Enable `spring.jpa.show-sql: true` and `spring.jpa.properties.hibernate.generate_statistics: true` in `application.yml`.

### 3 · Repository pattern

- `LoanService.lend(String isbn, String borrowerEmail)` is the single place that enforces:
  - The book must exist.
  - The book must not already be on loan.
  - The borrower must have fewer than **5** active loans.
- The service calls only repositories — no `EntityManager`, no JPQL inline.
- Controllers call only services — never repositories directly.

### 4 · JPQL and native queries

Add to `LoanRepository`:

```java
// JPQL — entity-oriented
@Query("select l from Loan l where l.borrower.email = :email and l.returned = false")
List<Loan> findActiveByBorrower(String email);

// Native — same question, raw SQL, for comparison
@Query(value = "select * from loan where borrower_id = "
             + "(select id from borrower where email = :email) "
             + "and returned = false",
       nativeQuery = true)
List<Loan> findActiveByBorrowerNative(String email);
```

Include a comment on each explaining when to prefer it.

### 5 · Entity relationships

- `Book` → `Author`: `@ManyToOne(fetch = FetchType.LAZY)` — owning side.
- `Author` → `Book`: `@OneToMany(mappedBy = "author")` — inverse, no extra column.
- `Book` → `Loan`: `@OneToMany(mappedBy = "book")` — inverse.
- `Loan` → `Book` and `Loan` → `Borrower`: both `LAZY`.
- In `BookService.getCatalogue()` use `@EntityGraph(attributePaths = "author")` to
  prevent the N+1 problem. Add a comment labelling the N+1 fix.

### 6 · Transaction management

```java
@Transactional(readOnly = true)   // no dirty-check overhead
public Page<BookDto> getCatalogue(Pageable page) { ... }

@Transactional                    // dirty checking saves renameBook without save()
public BookDto renameBook(Long id, String title) { ... }

@Transactional(rollbackFor = LoanException.class)
public LoanDto lend(String isbn, String email) throws LoanException { ... }
```

Add a code comment on `renameBook` explaining self-invocation pitfall
(calling from the same bean bypasses the proxy).

---

## Day 4 features to demonstrate (Spring Security & best practices)

### 1 · Spring Security fundamentals

`SecurityConfig` must produce a `SecurityFilterChain` bean that:
- Is **stateless** (`SessionCreationPolicy.STATELESS`).
- Adds `JwtFilter` before `UsernamePasswordAuthenticationFilter`.
- Disables CSRF (stateless API — include a comment stating why).
- Exposes `POST /api/auth/login` and `GET /actuator/health` without authentication.
- Requires `ROLE_MEMBER` for `GET /api/books/**` and `POST /api/loans`.
- Requires `ROLE_LIBRARIAN` for `POST /api/books`, `DELETE /api/books/**`.
- Requires `ROLE_ACTUATOR` for all other `/actuator/**` endpoints.

### 2 · Authentication and authorization

- `BorrowerPrincipal implements UserDetails` wraps the `Borrower` entity.
- `UserDetailsService` bean loads by email from `BorrowerRepository`.
- All passwords stored with `BCryptPasswordEncoder` (a `@Bean`, not inline `new`).
- Use `@PreAuthorize("hasRole('LIBRARIAN')")` on `BookService.addBook()`.
- Use `@PostAuthorize("returnObject.borrowerEmail == authentication.name")`
  on `LoanService.getLoan(Long id)`.
- Add `@EnableMethodSecurity` to `SecurityConfig`.

### 3 · JWT authentication

`JwtConfig` (bound by `@ConfigurationProperties("library.jwt")`):

```yaml
library:
  jwt:
    secret: ${JWT_SECRET:dev-secret-change-in-prod-min-32-chars}
    ttl-minutes: 15
    refresh-ttl-days: 7
```

`AuthController.login(LoginRequest)`:
1. Authenticate credentials via `AuthenticationManager`.
2. Build a JWT with claims: `sub` = email, `roles` = list of role names, `exp` = now + ttl.
3. Return `{ "token": "...", "expiresIn": 900 }`.

`JwtFilter`:
- Extract the `Bearer` token from `Authorization` header.
- Parse and validate the signature; on failure return 401, do **not** throw.
- On success, build a `UsernamePasswordAuthenticationToken` and set it on
  `SecurityContextHolder`.
- Add a comment: "Anyone can read the JWT payload — never put a password in it."

### 4 · Spring Boot Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,loggers
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
```

Add a custom health indicator `LibraryHealthIndicator` that reports
`{ "overdueLoans": N }` in its detail map.

### 5 · Logging and monitoring

In `LoanService.lend()`:

```java
MDC.put("loanId", loan.getId().toString());
log.info("book_lent isbn={} borrower={} durationMs={}", isbn, borrowerEmail, elapsed);
// Do NOT log the full email if it contains PII in production — log the borrower id instead.
meterRegistry.counter("library.loans.created").increment();
meterRegistry.counter("library.loans.active").increment();
```

In `LoanService.returnBook()`:
```java
meterRegistry.counter("library.loans.active").increment(-1);
```

Use SLF4J placeholders throughout — never string concatenation in log calls.

### 6 · Application profiles

```yaml
# application.yml (default = dev)
spring:
  datasource.url: jdbc:h2:mem:library
  jpa:
    show-sql: true
    hibernate.ddl-auto: validate
  flyway.enabled: true
logging.level.com.library: DEBUG

# application-prod.yml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa.show-sql: false
logging:
  pattern.console: ""                       # suppress plain console
  pattern.file: '{"ts":"%d","lvl":"%p","msg":"%m"}%n'
```

- Annotate the `DataLoader` seed bean with `@Profile("!prod")`.
- Bind all custom config with `@ConfigurationProperties`, not `@Value`.

### 7 · Spring framework best practices

Apply every item below and add a single-line comment marking it:

| Practice | Where |
|---|---|
| Constructor injection (no `@Autowired` on fields) | All services and controllers |
| DTO at the boundary | `BookDto`, `LoanDto`, `BorrowerDto` — entities never leave the service layer |
| `@ConfigurationProperties` for custom config | `JwtConfig` |
| Fail fast at startup | `@Validated` on `JwtConfig`, `@Min(1)` on `ttlMinutes` |
| `@DataJpaTest` for repo | `BookRepositoryTest` |
| `@WebMvcTest` for controller | `BookControllerTest` |
| Plain unit test for service | `LoanServiceTest` with Mockito |
| `readOnly = true` on queries | All `get*` service methods |

---

## REST API summary

| Method | Path | Role | What it does |
|---|---|---|---|
| `POST` | `/api/auth/login` | open | Returns JWT |
| `GET` | `/api/books` | MEMBER | Paginated catalogue |
| `GET` | `/api/books/{id}` | MEMBER | Single book |
| `POST` | `/api/books` | LIBRARIAN | Add book |
| `PATCH` | `/api/books/{id}/title` | LIBRARIAN | Rename (dirty-check demo) |
| `DELETE` | `/api/books/{id}` | LIBRARIAN | Remove book |
| `GET` | `/api/books/search?title=` | MEMBER | Derived query demo |
| `GET` | `/api/books/overdue?author=` | LIBRARIAN | JPQL query demo |
| `POST` | `/api/loans` | MEMBER | Lend a book |
| `POST` | `/api/loans/{id}/return` | MEMBER | Return a book |
| `GET` | `/api/loans/mine` | MEMBER | Active loans (JPQL) |
| `GET` | `/actuator/health` | open | Health (with overdue detail) |
| `GET` | `/actuator/metrics` | ACTUATOR | Micrometer metrics |

---

## Seed data (loaded by `DataLoader` — `@Profile("!prod")`)

```
Authors:  J.R.R. Tolkien,  Frank Herbert,  Ursula K. Le Guin
Books:    The Hobbit (978-0-261-10221-7),  Dune (978-0-441-17271-9),
          The Left Hand of Darkness (978-0-441-47812-5)
Borrowers:
  anna@library.nl  / password: member123  / role: MEMBER
  bob@library.nl   / password: member123  / role: MEMBER
  admin@library.nl / password: admin123   / role: LIBRARIAN
```

Passwords must be BCrypt-encoded in the seed, not plain text.

---

## Output format

1. **`pom.xml`** — all dependencies listed, Java 21 compiler plugin.
2. **Every `.java` file** in the structure above — complete, no `// TODO` placeholders.
3. **`application.yml`** and **`application-prod.yml`** — complete.
4. **`V1__schema.sql`** and **`V2__seed_data.sql`** — Flyway-compatible H2 SQL.
5. **All three test classes** — complete, runnable with `./mvnw test`.

After each file output a short (≤ 3 line) comment block explaining which Day 3 or
Day 4 feature it demonstrates and the line(s) where the key pattern appears.

---

## Constraints

- Java 21, Spring Boot 3.x, Maven.
- H2 for dev, schema via Flyway (`ddl-auto: validate` everywhere — never `create-drop`
  or `update`).
- Use `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` for JWT.
- No Lombok — explicit constructors, getters, setters (or Java records for DTOs).
- No Spring WebFlux — servlet stack only.
- No additional entities or tables beyond the four listed.
- Every file must compile with `./mvnw package -DskipTests`.
- The three test classes must pass with `./mvnw test` against the H2 dev profile.
