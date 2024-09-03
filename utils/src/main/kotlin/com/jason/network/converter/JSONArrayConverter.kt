package com.jason.network.converter

import com.jason.network.error.ConvertException
import com.jason.network.extension.readString
import com.jason.network.request.BaseRequest
import com.jason.network.request.BoxedRequest
import okhttp3.Response
import org.json.JSONArray
import kotlin.reflect.KClass

class JSONArrayConverter : ResponseConverter<JSONArray>() {
    override fun supportType(): KClass<*> {
        return JSONArray::class
    }

    override fun convert(request: BaseRequest<JSONArray>, response: Response): JSONArray {
        return response.use {
            it.readString(request.charset)?.let {
                JSONArray(it)
            } ?: throw ConvertException("Response body is null!")
        }
    }
}