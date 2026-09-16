CREATE TABLE ROOM (
    id Integer primary key GENERATED ALWAYS AS IDENTITY,
    name varchar(10) not null,
    open_at time not null,
    close_at time not null
);