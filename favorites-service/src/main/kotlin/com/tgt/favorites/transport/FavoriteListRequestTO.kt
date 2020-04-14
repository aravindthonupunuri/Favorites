package com.tgt.favorites.transport

import com.tgt.lists.lib.api.transport.ListRequestTO
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

class FavoriteListRequestTO(
    @field:NotNull(message = "Channel must not be empty") val channel: LIST_CHANNEL,
    @field:NotEmpty(message = "List title must not be empty") val listTitle: String,
    val shortDescription: String? = null
) {
    fun toListRequestTO(): ListRequestTO {
        return ListRequestTO(channel, listTitle, 3991, shortDescription)
    }
}
