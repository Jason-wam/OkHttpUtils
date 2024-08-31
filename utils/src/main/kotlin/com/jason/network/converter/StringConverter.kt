package com.jason.network.converter

import com.jason.network.request.BoxedRequest
import okhttp3.Response
import java.nio.charset.Charset
import kotlin.reflect.KClass

class StringConverter : ResponseConverter<String>() {
    override fun supportType(): KClass<*> {
        return String::class
    }

    override fun convert(request: BoxedRequest<String>, response: Response): String {
        return response.body?.source()?.readString(Charset.forName(request.charset))
            ?: throw ConvertException("Response body is null!")
    }
}