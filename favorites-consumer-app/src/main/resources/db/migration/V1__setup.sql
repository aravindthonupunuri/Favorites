create table GUEST_PREFERENCE (
    GUEST_ID text not null,
    LIST_SORT_ORDER text not null,
    CONSTRAINT GUEST_LIST_PK PRIMARY KEY (GUEST_ID)
);
create table LIST (
    LIST_ID uuid not null,
    LIST_ITEM_SORT_ORDER text not null,
    CONSTRAINT LIST_PK PRIMARY KEY (LIST_ID)
);
