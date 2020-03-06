package com.tgt.favorites.api.util

import com.tgt.lists.lib.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.lib.api.util.Direction
import java.util.*

class GuestPrefSortOrderDataProvider {

    fun getEditItemSortOrderRequestTO(
        listId: UUID,
        primaryItemId: UUID,
        secondaryItemId: UUID,
        direction: Direction
    ): EditItemSortOrderRequestTO {
        return EditItemSortOrderRequestTO(listId = listId, primaryItemId = primaryItemId,
            secondaryItemId = secondaryItemId, direction = direction)
    }
}
