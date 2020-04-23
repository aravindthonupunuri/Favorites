package com.tgt.favorites.api.util

import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.shoppinglist.util.populateItemRefId
import java.util.*

class ListDataProvider {
    fun getListItem(
        listItemId: UUID,
        tcin: String?,
        channel: LIST_CHANNEL,
        itemTitle: String?,
        promotionId: String?,
        itemType: ItemType,
        itemRelationship: String?
    ): ListItemResponseTO {
        return ListItemResponseTO(listItemId = listItemId, tcin = tcin, itemTitle = itemTitle, channel = channel,
            itemRefId = populateItemRefId(itemType, tcin, itemTitle, promotionId),
            itemType = itemType, relationshipType = itemRelationship)
    }

    fun getListResponseTO(listId: UUID, listType: String, listTitle: String): ListResponseTO {
        return ListResponseTO(listId = listId, channel = LIST_CHANNEL.WEB, listType = listType, listTitle = listTitle,
            shortDescription = "short$listId", agentId = null, metadata = null,
            pendingListItems = emptyList(), completedListItems = emptyList(),
            addedTs = null, lastModifiedTs = null, maxPendingItemsCount = 0, maxCompletedPageCount = 0,
            maxPendingPageCount = null, maxCompletedItemsCount = null)
    }
}
