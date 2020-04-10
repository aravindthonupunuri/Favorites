package com.tgt.shoppinglist.util

import com.tgt.lists.lib.api.util.ItemRefIdBuilder.Companion.buildItemRefId
import com.tgt.lists.lib.api.util.ItemType

fun populateItemRefId(type: ItemType, tcin: String?, itemTitle: String?, promotionId: String?): String {
    return if (type == ItemType.TCIN) {
        buildItemRefId(type, tcin!!)
    } else if (type == ItemType.OFFER) {
        buildItemRefId(type, promotionId!!)
    } else if (type == ItemType.GENERIC_ITEM) {
        buildItemRefId(type, itemTitle?.replace("\\s".toRegex(), "").hashCode().toString())
    } else {
        throw RuntimeException("Unsupported ItemType: $type")
    }
}
