package com.tgt.favorites.client.redsky.getitemhydration

import com.fasterxml.jackson.annotation.JsonProperty
import com.tgt.favorites.client.redsky.getItemHydrationwithvariation.Product

data class ItemDetailVO(
    @JsonProperty("product_summaries")
    val products: List<Product>?
)
