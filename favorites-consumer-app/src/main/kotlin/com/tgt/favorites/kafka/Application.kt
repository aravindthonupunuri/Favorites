package com.tgt.shoppinglist.kafka

import com.target.platform.connector.micronaut.PlatformPropertySource
import com.tgt.lists.lib.api.util.TAPEnvironmentLoader
import io.micronaut.runtime.Micronaut

object Application {
    @JvmStatic
    fun main(args: Array<String>) {

        // TAP deployment specific
        TAPEnvironmentLoader().setupTAPSpecificEnvironment()

        Micronaut.build()
            .propertySources(PlatformPropertySource.connect())
            .packages("com.tgt.shoppinglist.kafka")
            .mainClass(Application.javaClass)
            .start()
    }
}
