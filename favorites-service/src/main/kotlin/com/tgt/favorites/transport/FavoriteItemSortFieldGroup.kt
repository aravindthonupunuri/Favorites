package com.tgt.favorites.transport

import com.tgt.lists.lib.api.util.ItemSortFieldGroup

enum class FavoriteItemSortFieldGroup(val value: String) {
    ITEM_TITTLE("item_title"),
    ADDED_DATE("added_date"),
    LAST_MODIFIED_DATE("last_modified_date"),
    PRICE("price"),
    AVERAGE_OVERALL_RATING("average_overall_price");

    fun toItemSortFieldGroup(): ItemSortFieldGroup {
        return ItemSortFieldGroup.valueOf(this.toString())
    }
}
