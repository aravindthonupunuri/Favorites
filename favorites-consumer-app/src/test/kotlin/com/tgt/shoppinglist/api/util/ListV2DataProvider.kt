package com.tgt.shoppinglist.api.util

import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.v2.client.transport.ListGetAllResponseTOV2
import com.tgt.lists.v2.client.transport.ListItemResponseTOV2
import com.tgt.lists.v2.client.transport.ListResponseTOV2
import java.time.LocalDateTime
import java.util.*

@Suppress("UNCHECKED_CAST")
class ListV2DataProvider {

    fun getAllListsV2(guestId: String, guestLists: List<ListResponseTOV2>): ListGetAllResponseTOV2 {
        return ListGetAllResponseTOV2(guestId.toLong(), guestLists)
    }

    private fun getListsV2(): List<ListResponseTOV2> {
        val lists = arrayListOf<ListResponseTOV2>()
        lists.add(ListResponseTOV2(UUID.randomUUID(), "WEB", "title1", true, "1375", getPendingListItems()))
        lists.add(ListResponseTOV2(UUID.randomUUID(), "WEB", "title2", true, "1375", getPendingListItems()))
        lists.add(ListResponseTOV2(UUID.randomUUID(), "WEB", "title3", null, "1375", getPendingListItems()))
        lists.add(ListResponseTOV2(UUID.randomUUID(), "WEB", "title4", false, "1375", getPendingListItems()))
        return lists
    }

    private fun getPendingListItems(): List<ListItemResponseTOV2> {
        val pendingItems = arrayListOf<ListItemResponseTOV2>()
        pendingItems.add(ListItemResponseTOV2("WEB", "1234", "title1", "note", "TCIN", 1, false, Date().toString()))
        pendingItems.add(ListItemResponseTOV2("WEB", "1234", "title1", "note", "TCIN", 2, false, Date().toString()))
        pendingItems.add(ListItemResponseTOV2("WEB", null, "generic item title", "note", "GENERIC_ITEM", 1, false, Date().toString()))
        pendingItems.add(ListItemResponseTOV2("WEB", null, "generic item title", "note", "GENERIC_ITEM", 1, false, Date().toString()))
        pendingItems.add(ListItemResponseTOV2("WEB", "1234", "title1", "note", "TCIN", 1, true, Date().toString()))
        pendingItems.add(ListItemResponseTOV2("WEB", null, "title1", "note", "TCIN", 1, false, Date().toString()))
        pendingItems.add(ListItemResponseTOV2("WEB", "1234", "generic item title", "note", "GENERIC_ITEM", 1, false, Date().toString()))
        pendingItems.add(ListItemResponseTOV2("WEB", null, null, "note", "GENERIC_ITEM", 1, false, Date().toString()))
        pendingItems.add(ListItemResponseTOV2("WEB", "1234", "title1", "note", "OFFER_ITEM", 1, false, Date().toString()))
        return pendingItems
    }

    fun getListResponse(listId: UUID): ListResponseTO {
        return ListResponseTO(listId, LIST_CHANNEL.MOBILE, "SHOPPING", "list title", "desc", "agntId", true, null, null,
            null, LocalDateTime.now().toString(), LocalDateTime.now().toString(), 0, 0)
    }
}
