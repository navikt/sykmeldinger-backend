CREATE TABLE arbeidsforhold
(
    id                 INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fnr                VARCHAR not null,
    orgnummer          VARCHAR not null,
    juridisk_orgnummer VARCHAR not null,
    orgnavn            VARCHAR not null,
    fom                DATE    not null,
    tom                DATE
);

create index arbeidsforhold_fnr_idx on arbeidsforhold (fnr);