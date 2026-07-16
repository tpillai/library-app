insert into author (name) values
    ('J.R.R. Tolkien'),
    ('Frank Herbert'),
    ('Ursula K. Le Guin');

insert into book (title, isbn, author_id) values
    ('The Hobbit', '978-0-261-10221-7', (select id from author where name = 'J.R.R. Tolkien')),
    ('Dune', '978-0-441-17271-9', (select id from author where name = 'Frank Herbert')),
    ('The Left Hand of Darkness', '978-0-441-47812-5', (select id from author where name = 'Ursula K. Le Guin'));

-- Passwords are BCrypt hashes, never plain text:
--   anna@library.nl  -> member123
--   bob@library.nl   -> member123
--   admin@library.nl -> admin123
insert into borrower (email, password, role) values
    ('anna@library.nl', '$2a$10$hVnUuMAXcxIqek9963do0.ZJ.X5KdhTYt9GO/tTxywhr2wDgndZ2S', 'MEMBER'),
    ('bob@library.nl', '$2a$10$hVnUuMAXcxIqek9963do0.ZJ.X5KdhTYt9GO/tTxywhr2wDgndZ2S', 'MEMBER'),
    ('admin@library.nl', '$2a$10$Y/Eh/IW2uKYNT1nr/pnQAelpBkvMR7d3CP9V5G0WJPt/PPHzrRrcK', 'LIBRARIAN');
