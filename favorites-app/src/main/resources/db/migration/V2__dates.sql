alter table GUEST_PREFERENCE
    add column DATE_CREATED timestamp not null DEFAULT NOW(),
    add column DATE_UPDATED timestamp not null DEFAULT NOW();

alter table LIST
    add column DATE_CREATED timestamp not null DEFAULT NOW(),
    add column DATE_UPDATED timestamp not null DEFAULT NOW();

