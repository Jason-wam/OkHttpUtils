package com.jason.network.converter

import com.jason.network.request.BoxedRequest
import okhttp3.Response
import kotlin.reflect.KClass

class NoConverter : ResponseConverter<Response>() {
    override fun supportType(): KClass<*> {
        return Response::class
    }

    override fun convert(request: BoxedRequest<Response>, response: Response): Response {
        return response
    }
}