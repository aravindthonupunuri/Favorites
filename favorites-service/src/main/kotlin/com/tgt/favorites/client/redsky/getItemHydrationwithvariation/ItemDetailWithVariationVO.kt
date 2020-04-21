package com.tgt.favorites.client.redsky.getItemHydrationwithvariation

import com.fasterxml.jackson.annotation.JsonProperty

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
    val variationHierarchy: VariationHierarchy?
)

data class Item(
    @JsonProperty("relationship_type")
    val relationShipType: String?,
    @JsonProperty("fulfillment")
    val fulfillment: Fulfillment,
    @JsonProperty("item_state")
    val itemState: String?,
    @JsonProperty("estore_item_status_code")
    val estoreItemStatusCode: String?,
    @JsonProperty("launch_date_time")
    val launchDateTime: String?,
    @JsonProperty("product_vendors")
    val productVendors: List<Vendor>
)

data class Fulfillment(
    @JsonProperty("is_market_place")
    val isMarketPlace: Boolean? = false
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
    val isCurrentPriceRange: Boolean?,
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
    @JsonProperty("primary_image_url") val primaryImageUrl: String?,
    @JsonProperty("availability") val availability: Availability?
)

data class Availability(
    @JsonProperty("is_shipping_available") val isShippingAvailable: Boolean? = false,
    @JsonProperty("is_shipping_loyalty_available") val isShippingLoyaltyAvailable: Boolean? = false,
    @JsonProperty("is_scheduled_delivery_available") val isScheduledDeliveryAvailable: Boolean? = false,
    @JsonProperty("is_primary_store_available") val isPrimaryStoreAvailable: Boolean? = false,
    @JsonProperty("is_backup_store_available") val isBackupStoreAvailable: Boolean? = false
)
