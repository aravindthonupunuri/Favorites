package com.tgt.favorites.client.redsky.getItemHydrationwithvariation

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

data class ItemDetailWithVariationVO(
    @JsonProperty("product")
    val product: Product
)

data class Product(
    @JsonProperty("tcin")
    val tcin: String?,
    @JsonProperty("item")
    val item: Item?,
    @JsonProperty("price")
    val price: Price?,
    @JsonProperty("ratings_and_reviews")
    val ratingsAndReviews: RatingReview?,
    @JsonProperty("variation_hierarchy")
    val variationHierarchy: List<VariationHierarchy>?,
    @JsonProperty("available_to_promise")
    val availableToPromise: AvailableToPromise?
)

data class Item(
    @JsonProperty("relationship_type")
    val relationShipType: String?,
    @JsonProperty("fulfillment")
    val fulfillment: Fulfillment?,
    @JsonProperty("item_state")
    val itemState: String?,
    @JsonProperty("estore_item_status_code")
    val estoreItemStatusCode: String?,
    @JsonProperty("launch_date_time")
    val launchDateTime: String?,
    @JsonProperty("product_vendors")
    val productVendors: List<Vendor>?
)

data class Fulfillment(
    @JsonProperty("is_market_place")
    @field:Schema(name = "is_market_place") val marketPlace: Boolean? = false
)

data class Vendor(
    @JsonProperty("vendor_name")
    val vendorName: String?
)

data class Price(
    @JsonProperty("formatted_comparison_price")
    val formattedComparisonPrice: String?,
    @JsonProperty("formatted_current_price")
    val formattedCurrentPrice: String?,
    @JsonProperty("formatted_current_price_type")
    val formattedCurrentPriceType: String?,
    @JsonProperty("hide_price")
    val hidePrice: String?,
    @JsonProperty("is_current_price_range")
    @field:Schema(name = "is_current_price_range") val currentPriceRange: Boolean? = false,
    @JsonProperty("unmasked_formatted_comparison_price")
    val unmaskedFormattedComparisonPrice: String?,
    @JsonProperty("unmasked_formatted_current_price")
    val unmaskedFormattedCurrentPrice: String?,
    @JsonProperty("unmasked_formatted_current_price_type")
    val unmaskedFormattedCurrentPriceType: String?
)

data class RatingReview(
    @JsonProperty("statistics") val statistics: Statistics
)

data class Statistics(
    @JsonProperty("rating") val rating: Rating?,
    @JsonProperty("review_count") val reviewCount: Int?
)

data class Rating(
    @JsonProperty("average") val average: Double?
)

data class VariationHierarchy(
    @JsonProperty("name") val name: String?,
    @JsonProperty("value") val value: String?,
    @JsonProperty("tcin") val tcin: String?,
    @JsonProperty("swatch_image_url") val swatchImageUrl: String?,
    @JsonProperty("primary_image_url") val primaryImageUrl: String?
)

data class AvailableToPromise(
    @JsonProperty("qualitative") val qualitative: Qualitative?
)

data class Qualitative(
    @JsonProperty("street_date") val streetDate: String?,
    @JsonProperty("availability_status") val availabilityStatus: String?,
    @JsonProperty("is_out_of_stock_in_all_store_locations")
    @field:Schema(name = "is_out_of_stock_in_all_store_locations") val outOfStockInAllStoreLocations: Boolean? = false
)
