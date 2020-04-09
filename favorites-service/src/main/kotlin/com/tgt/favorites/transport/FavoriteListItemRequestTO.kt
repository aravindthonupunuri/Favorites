package com.tgt.favorites.transport

import com.tgt.lists.lib.api.exception.BadRequestException
import com.tgt.lists.lib.api.transport.ListItemRequestTO
import com.tgt.lists.lib.api.util.AppErrorCodes
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.shoppinglist.util.populateItemRefId
import javax.validation.constraints.NotNull

class FavoriteListItemRequestTO(
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType,
    val tcin: String?,
    val itemTitle: String?,
    val promotionId: String?,
    val itemNote: String? = null
) {
    init {
        this.validate()
    }

    fun validate(): FavoriteListItemRequestTO {
        when (itemType) {
            ItemType.GENERIC_ITEM -> validateGenericItem()
            ItemType.TCIN -> validateTcinItem()
            ItemType.OFFER -> validateOfferItem()
        }
        return this
    }

    private fun validateTcinItem() {
        if (this.tcin == null || this.tcin.trim().toIntOrNull() == null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Required field tcin is missing or invalid")))
    }

    private fun validateGenericItem() {
        if (this.tcin != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field tcin present for generic item")))
        val itemTitle: String = this.itemTitle ?: throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Required field item title is missing")))
        if (itemTitle.trim().toIntOrNull() != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Invalid item title")))
    }

    private fun validateOfferItem() {
        if (this.tcin != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field tcin present for offer item")))
        if (this.itemTitle != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field item title present for offer item")))
    }

    fun toListItemRequestTO(): ListItemRequestTO {
        return ListItemRequestTO(
            itemType = this.itemType,
            itemRefId = populateItemRefId(itemType, tcin, itemTitle, promotionId),
            tcin = this.tcin,
            itemTitle = this.itemTitle,
            itemNote = this.itemNote,
            metadata = FavoriteListItemMetaDataTO.getFavoriteListItemMetadataMap(itemType, promotionId)
        )
    }
}
