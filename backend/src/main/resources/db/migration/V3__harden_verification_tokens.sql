alter table verification_code
    add column expires_at datetime(6) null,
    add column consumed_at datetime(6) null;

update verification_code
set expires_at = date_add(current_timestamp(6), interval 1 day)
where expires_at is null;
