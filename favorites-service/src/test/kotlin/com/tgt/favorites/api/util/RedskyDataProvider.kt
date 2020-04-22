package com.tgt.favorites.api.util

import com.tgt.favorites.client.redsky.getItemHydrationwithvariation.*
import com.tgt.favorites.client.redsky.getItemHydrationwithvariation.Product
import com.tgt.favorites.client.redsky.getitemhydration.ItemDetailVO
import com.tgt.favorites.transport.ItemRelationshipType
import java.util.*

class RedskyDataProvider {

    fun getItemDetailVO(tcins: List<String>): ItemDetailVO {
        return ItemDetailVO(tcins.map { getProduct(it) })
    }

    fun getItemDetailWithVariationVO(tcin: String): ItemDetailWithVariationVO {
        return ItemDetailWithVariationVO(getProductWithVariation(tcin))
    }

    fun getProduct(tcin: String): Product {
        val item = Item(ItemRelationshipType.SA.value, Fulfillment(false),
            "Ready", "Ready", Date().toString(), listOf(Vendor("vendor")))
        val price = Price("1", "1", "Reg", null, true, "1", "1", null)
        val ratingReview = RatingReview(Statistics(Rating(1.0), 1))
        val atp = AvailableToPromise(Qualitative(Date().toString(), "available", false))
        return Product(tcin, item, price, ratingReview, null, atp)
    }

    fun getProductWithVariation(tcin: String): Product {
        val item = Item(ItemRelationshipType.SA.value, Fulfillment(false),
            "Ready", "Ready", Date().toString(), listOf(Vendor("vendor")))
        val price = Price("1", "1", "Reg", null, true, "1", "1", null)
        val ratingReview = RatingReview(Statistics(Rating(1.0), 1))
        val variations = listOf(VariationHierarchy("name", "value", tcin + 1, "url1", "url1"),
            VariationHierarchy("name", "value", tcin + 2, "url2", "url2"))
        return Product(tcin, item, price, ratingReview, variations, null)
    }
}
