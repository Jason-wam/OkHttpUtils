package com.jason.network.converter

import com.jason.network.error.ConvertException
import com.jason.network.request.BoxedRequest
import okhttp3.Response
import org.json.JSONArray
import java.nio.charset.Charset
import kotlin.reflect.KClass

class JSONArrayConverter : ResponseConverter<JSONArray>() {
    override fun supportType(): KClass<*> {
        return JSONArray::class
    }

    override fun convert(
        request: BoxedRequest<JSONArray>,
        response: Response
    ): JSONArray {
        return response.body?.source()?.readString(Charset.forName(request.charset))?.let {
            JSONArray(it)
        } ?: throw ConvertException("Response body is null!")
    }
}