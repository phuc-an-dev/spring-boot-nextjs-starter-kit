create table if not exists revoked_token
(
    jti        varchar(255) not null,
    revoked_at datetime(6) null,
    expires_at datetime(6) null,
    primary key (jti)
) engine = InnoDB;

create index idx_revoked_token_expires_at on revoked_token (expires_at);
