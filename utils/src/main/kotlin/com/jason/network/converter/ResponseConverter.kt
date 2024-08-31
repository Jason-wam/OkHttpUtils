package com.jason.network.converter

import com.jason.network.request.BoxedRequest
import okhttp3.Response
import kotlin.reflect.KClass

abstract class ResponseConverter<R> {
    abstract fun supportType(): KClass<*>

    abstract fun convert(request: BoxedRequest<R>, response: Response): R
}