package com.tgt.favorites.transport

data class GuestFavoritesResponseTO(
    val tcin: String? = null,
    val listItemDetails: List<ListItemDetailsTO>? = null
)
