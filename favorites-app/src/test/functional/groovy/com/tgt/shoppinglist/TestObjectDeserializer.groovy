package com.tgt.shoppinglist

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

class TestObjectDeserializer {

    static ObjectMapper mapper

    static {
        mapper = new ObjectMapper()
        mapper.registerModule(new DefaultScalaModule())
        mapper.registerModule(new KotlinModule())
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    }

    static Object deserialize(Object rawObject, Class returnTypeClazz) {
        def result = mapper.writeValueAsString(rawObject)
        return mapper.readValue(result, returnTypeClazz)
    }
}
