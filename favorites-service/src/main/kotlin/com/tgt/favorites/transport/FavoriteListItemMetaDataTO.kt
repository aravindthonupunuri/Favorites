package com.tgt.favorites.transport

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tgt.lists.lib.api.util.ItemType

@JsonIgnoreProperties(ignoreUnknown = true)
data class FavoriteListItemMetaDataTO(
    @JsonProperty("promotion_id")
    val promotionId: String? = null
) {
    companion object {
        const val FAVORITE_LIST_ITEM_METADATA = "favorite-list-item-metadata"
        val mapper = ObjectMapper()

        @JvmStatic
        fun getFavoriteListItemMetadata(metadata: Map<String, Any>?): FavoriteListItemMetaDataTO? {
            return metadata?.takeIf { metadata.containsKey(FAVORITE_LIST_ITEM_METADATA) }
                ?.let {
                    mapper.readValue<FavoriteListItemMetaDataTO>(
                        mapper.writeValueAsString(metadata[FAVORITE_LIST_ITEM_METADATA]))
                }
        }

        // ["Favorite-list-item-metadata" : [promotion_id: "1234"]
        @JvmStatic
        fun getFavoriteListItemMetadataMap(itemType: ItemType, promotionId: String?): Map<String, Any>? {
            return itemType.takeIf { it == ItemType.OFFER }
                ?.let { mapOf(FAVORITE_LIST_ITEM_METADATA to FavoriteListItemMetaDataTO(promotionId)) }
        }
    }
}
