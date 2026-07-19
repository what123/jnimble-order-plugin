create table if not exists ord_order (
    id bigint primary key,
    order_no varchar(64) not null,
    status varchar(32) not null,
    total_amount decimal(12, 2) not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_ord_order_status on ord_order(status);
