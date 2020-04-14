package com.tgt.favorites.api.auth

import com.tgt.lists.cart.CartClient
import com.tgt.lists.common.components.filters.auth.DefaultListPermissionManager
import com.tgt.lists.common.components.filters.auth.ListPermissionManager
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Singleton

@Singleton
class FavoritesPermissionManager(private val cartClient: CartClient) : ListPermissionManager {

    val defaultListPermissionManager: DefaultListPermissionManager
    init {
        defaultListPermissionManager = DefaultListPermissionManager(cartClient)
    }

    override fun authorize(userId: String, listId: UUID): Mono<Boolean> {
        return defaultListPermissionManager.authorize(userId, listId)
    }
}
