package com.tgt.favorites.client.redsky

import com.tgt.favorites.client.redsky.getitemhydration.ItemDetailVO
import com.tgt.favorites.client.redsky.getItemHydrationwithvariation.ItemDetailWithVariationVO
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.validation.Validated
import reactor.core.publisher.Mono

@Client(id = "redsky-api", path = "/redsky_aggregations/v1/lists")
@Validated
interface RedSkyClient {
    @Get("/favorites_list_item_hydration_v1?key=\${api-key}")
    fun getItemHydration(
        @QueryValue("store_id") storeId: String,
        @QueryValue("tcins") tcins: String
    ): Mono<RedskyResponseTO<ItemDetailVO>>

    @Get("/favorites_list_item_hydration_with_variation_v1?key=\${api-key}")
    fun getItemHydrationWithVariation(
        @QueryValue("store_id") storeId: String,
        @QueryValue("tcin") tcin: String
    ): Mono<RedskyResponseTO<ItemDetailWithVariationVO>>
}
