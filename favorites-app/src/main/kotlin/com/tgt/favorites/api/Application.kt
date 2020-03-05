package com.tgt.favorites.api

import com.target.platform.connector.micronaut.PlatformPropertySource
import com.tgt.lists.lib.api.util.TAPEnvironmentLoader
import io.micronaut.runtime.Micronaut
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info

@OpenAPIDefinition(info = Info(title = "favorites", version = "v4"))
object Application {

    @JvmStatic
    fun main(args: Array<String>) {

        // TAP deployment specific
        TAPEnvironmentLoader().setupTAPSpecificEnvironment()

        Micronaut.build()
            .propertySources(PlatformPropertySource.connect())
            .packages("com.tgt.favorites.api.controller")
            .mainClass(Application.javaClass)
            .start()
    }
}
