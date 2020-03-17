package com.tgt.favorites.transport

data class FavoritesTcinResponseTO(
    val tcin: String? = null,
    val listItemDetails: List<ListItemDetailsTO>? = null
)
