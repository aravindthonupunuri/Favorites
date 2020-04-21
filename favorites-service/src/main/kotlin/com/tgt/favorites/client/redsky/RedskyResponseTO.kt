package com.tgt.favorites.client.redsky

data class RedskyResponseTO<T>(
    val errors: List<Any>? = null,
    val data: T? = null
)
