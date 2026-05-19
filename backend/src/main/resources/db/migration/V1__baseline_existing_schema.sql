create table `user`
(
    id                bigint       not null auto_increment,
    email             varchar(255) null,
    first_name        varchar(255) null,
    last_name         varchar(255) null,
    password          varchar(255) null,
    profile_image_url varchar(255) null,
    role              enum ('ADMIN', 'USER') null,
    verified          bit          not null,
    primary key (id)
) engine = InnoDB;

create table verification_code
(
    id         bigint       not null auto_increment,
    code       varchar(255) null,
    email_sent bit          not null,
    user_id    bigint       null,
    primary key (id),
    unique key uk_verification_code_user_id (user_id),
    constraint fk_verification_code_user foreign key (user_id) references `user` (id)
) engine = InnoDB;

create table password_reset_token
(
    id         bigint       not null auto_increment,
    email_sent bit          not null,
    expires_at datetime(6)  null,
    token      varchar(255) null,
    user_id    bigint       null,
    primary key (id),
    key idx_password_reset_token_user_id (user_id),
    constraint fk_password_reset_token_user foreign key (user_id) references `user` (id)
) engine = InnoDB;

create table user_connected_account
(
    id           bigint       not null auto_increment,
    connected_at datetime(6)  null,
    provider     varchar(255) null,
    provider_id  varchar(255) null,
    user_id      bigint       null,
    primary key (id),
    key idx_user_connected_account_user_id (user_id),
    constraint fk_user_connected_account_user foreign key (user_id) references `user` (id)
) engine = InnoDB;

create table uploaded_file
(
    id                 bigint       not null auto_increment,
    created_at         datetime(6)  null,
    extension          varchar(255) null,
    original_file_name varchar(255) null,
    size               bigint       null,
    uploaded_at        datetime(6)  null,
    url                varchar(255) null,
    user_id            bigint       null,
    primary key (id),
    key idx_uploaded_file_user_id (user_id),
    constraint fk_uploaded_file_user foreign key (user_id) references `user` (id)
) engine = InnoDB;

create table push_notification_subscription
(
    id         bigint       not null auto_increment,
    auth_key   varchar(255) null,
    created_at datetime(6)  null,
    endpoint   text         null,
    p256dh_key varchar(255) null,
    primary key (id)
) engine = InnoDB;

create table notification_permission_request
(
    id            bigint      not null auto_increment,
    created_at    datetime(6) null,
    denied_reason tinyint     null,
    requested_by  tinyint     null,
    primary key (id),
    constraint notification_permission_request_denied_reason_chk check (denied_reason between 0 and 1),
    constraint notification_permission_request_requested_by_chk check (requested_by between 0 and 1)
) engine = InnoDB;

create table notification
(
    id         bigint       not null auto_increment,
    created_at datetime(6)  null,
    delivered  bit          not null,
    message    varchar(255) null,
    title      varchar(255) null,
    url        varchar(255) null,
    primary key (id)
) engine = InnoDB;
