/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.instantexecution.serialization.codecs

import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultDirectoryVar
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultRegularFileVar
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.DefaultListProperty
import org.gradle.api.internal.provider.DefaultMapProperty
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.provider.TransformBackedProvider
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy


private
suspend fun WriteContext.writeProvider(value: ProviderInternal<*>) {
    if (value.isValueProducedByTask && value is TransformBackedProvider<*, *>) {
        // Need to serialize the transformation and its source, as the value is not available until execution time
        writeBoolean(true)
        writeTransformer(value.transformer)
        writeProvider(value.provider)
    } else {
        // Can serialize the value and discard the provider
        writeBoolean(false)
        write(unpack(value))
    }
}


private
suspend fun ReadContext.readProvider(): ProviderInternal<Any> {
    return if (readBoolean()) {
        val transformer = readTransformer()
        val provider = readProvider()
        TransformBackedProvider(transformer, provider)
    } else {
        val value = read()
        when (value) {
            is BrokenValue -> DefaultProvider<Any> { value.rethrow() }
            else -> Providers.ofNullable(value)
        }
    }
}


private
suspend fun WriteContext.writeTransformer(value: Transformer<*, *>) {
    // TODO - should just have another codec (or codec supplier) that knows how to serialize proxies
    if (Proxy.isProxyClass(value.javaClass)) {
        writeBoolean(true)
        write(Proxy.getInvocationHandler(value))
    } else {
        writeBoolean(false)
        write(value)
    }
}


private
suspend fun ReadContext.readTransformer(): Transformer<Any, Any> {
    return if (readBoolean()) {
        val invocationHandler = read() as InvocationHandler
        Proxy.newProxyInstance(classLoader, arrayOf(Transformer::class.java), invocationHandler) as Transformer<Any, Any>
    } else {
        read() as Transformer<Any, Any>
    }
}


private
fun unpack(value: Provider<*>): Any? {
    try {
        return value.orNull
    } catch (e: Exception) {
        return BrokenValue(e)
    }
}


object
ProviderCodec : Codec<ProviderInternal<*>> {
    override suspend fun WriteContext.encode(value: ProviderInternal<*>) {
        // TODO - should write the provider value type
        writeProvider(value)
    }

    override suspend fun ReadContext.decode() = readProvider()
}


object
PropertyCodec : Codec<DefaultProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultProperty<*>) {
        // TODO - should write the property type
        writeProvider(value.provider)
    }

    override suspend fun ReadContext.decode(): DefaultProperty<*> {
        val value = readProvider()
        return DefaultProperty(Any::class.java).provider(value)
    }
}


class
DirectoryPropertyCodec(private val filePropertyFactory: FilePropertyFactory) : Codec<DefaultDirectoryVar> {
    override suspend fun WriteContext.encode(value: DefaultDirectoryVar) {
        writeProvider(value.provider)
    }

    override suspend fun ReadContext.decode(): DefaultDirectoryVar {
        val value = readProvider() as Provider<Directory>
        return filePropertyFactory.newDirectoryProperty().value(value) as DefaultDirectoryVar
    }
}


class
RegularFilePropertyCodec(private val filePropertyFactory: FilePropertyFactory) : Codec<DefaultRegularFileVar> {
    override suspend fun WriteContext.encode(value: DefaultRegularFileVar) {
        writeProvider(value.provider)
    }

    override suspend fun ReadContext.decode(): DefaultRegularFileVar {
        val value = readProvider() as Provider<RegularFile>
        return filePropertyFactory.newFileProperty().value(value) as DefaultRegularFileVar
    }
}


object
ListPropertyCodec : Codec<ListProperty<*>> {
    override suspend fun WriteContext.encode(value: ListProperty<*>) {
        // TODO - should write the element type
        writeProvider(value as ProviderInternal<*>)
    }

    override suspend fun ReadContext.decode(): ListProperty<*>? {
        val value = readProvider() as Provider<List<Any>>
        return DefaultListProperty(Any::class.java).value(value)
    }
}


object
MapPropertyCodec : Codec<MapProperty<*, *>> {
    override suspend fun WriteContext.encode(value: MapProperty<*, *>) {
        // TODO - should write the key and value types
        writeProvider(value as ProviderInternal<*>)
    }

    override suspend fun ReadContext.decode(): MapProperty<*, *>? {
        val value = readProvider() as Provider<Map<Any, Any>>
        return DefaultMapProperty(Any::class.java, Any::class.java).value(value)
    }
}