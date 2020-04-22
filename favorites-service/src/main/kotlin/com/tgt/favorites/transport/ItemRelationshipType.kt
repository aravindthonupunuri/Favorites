package com.tgt.favorites.transport

enum class ItemRelationshipType(val value: String) {
    SA("Stand Alone"),
    VPC("Variation Parent within a Collection"),
    VAP("Variation Parent"),
    VC("Variation Child"),
    COP("Collection Parent"),
    CC("Collection Child"),
    TAC("Title Authority Child"),
    TAP("Title Authority Parent")
}
