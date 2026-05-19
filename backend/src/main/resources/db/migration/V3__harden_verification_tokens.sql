set @add_expires_at = (
    select if(
        count(*) = 0,
        'alter table verification_code add column expires_at datetime(6) null',
        'select 1'
    )
    from information_schema.columns
    where table_schema = database()
      and table_name = 'verification_code'
      and column_name = 'expires_at'
);
prepare add_expires_at_statement from @add_expires_at;
execute add_expires_at_statement;
deallocate prepare add_expires_at_statement;

set @add_consumed_at = (
    select if(
        count(*) = 0,
        'alter table verification_code add column consumed_at datetime(6) null',
        'select 1'
    )
    from information_schema.columns
    where table_schema = database()
      and table_name = 'verification_code'
      and column_name = 'consumed_at'
);
prepare add_consumed_at_statement from @add_consumed_at;
execute add_consumed_at_statement;
deallocate prepare add_consumed_at_statement;

update verification_code
set expires_at = date_add(current_timestamp(6), interval 1 day)
where expires_at is null;
