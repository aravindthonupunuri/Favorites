package com.tgt.favorites.transport

import java.util.*
import javax.validation.constraints.NotNull

data class ListItemDetailsTO(
    @field:NotNull(message = "List item id must not be empty") val listId: UUID? = null,
    val itemTitle: String? = null,
    @field:NotNull(message = "List id must not be empty") val listItemId: UUID? = null
)
