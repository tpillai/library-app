create table author (
    id   bigint auto_increment primary key,
    name varchar(255) not null
);

create table book (
    id        bigint auto_increment primary key,
    title     varchar(255) not null,
    isbn      varchar(255) not null,
    author_id bigint       not null,
    constraint uq_book_isbn unique (isbn),
    constraint fk_book_author foreign key (author_id) references author (id)
);

create table borrower (
    id       bigint auto_increment primary key,
    email    varchar(255) not null,
    password varchar(255) not null,
    role     varchar(255) not null,
    constraint uq_borrower_email unique (email)
);

create table loan (
    id          bigint auto_increment primary key,
    book_id     bigint  not null,
    borrower_id bigint  not null,
    loan_date   date    not null,
    due_date    date    not null,
    returned    boolean not null,
    constraint fk_loan_book foreign key (book_id) references book (id),
    constraint fk_loan_borrower foreign key (borrower_id) references borrower (id)
);
