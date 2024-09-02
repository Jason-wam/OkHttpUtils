package com.jason.network.converter

import com.jason.network.error.ConvertException
import com.jason.network.readString
import com.jason.network.request.BoxedRequest
import okhttp3.Response
import org.json.JSONArray
import kotlin.reflect.KClass

class StringConverter : ResponseConverter<String>() {
    override fun supportType(): KClass<*> {
        return String::class
    }

    override fun convert(request: BoxedRequest<String>, response: Response): String {
        return response.use {
            it.readString(request.charset) ?: throw ConvertException("Response body is null!")
        }
    }
}