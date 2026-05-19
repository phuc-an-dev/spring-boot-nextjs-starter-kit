create table revoked_tokens
(
    jti        varchar(255) not null,
    revoked_at datetime(6) not null,
    expires_at datetime(6) not null,
    primary key (jti)
) engine = InnoDB;

create index idx_revoked_tokens_expires_at on revoked_tokens (expires_at);
