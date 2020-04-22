package com.tgt.favorites.client.redsky

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RedskyResponseTO<T>(
    val errors: List<Any>? = null,
    val data: T? = null
)
