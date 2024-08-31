package com.jason.network.converter

import com.jason.network.request.BoxedRequest
import okhttp3.Response
import org.json.JSONObject
import java.nio.charset.Charset
import kotlin.reflect.KClass

class JSONObjectConverter : ResponseConverter<JSONObject>() {
    override fun supportType(): KClass<*> {
        return JSONObject::class
    }

    override fun convert(
        request: BoxedRequest<JSONObject>,
        response: Response
    ): JSONObject {
        return response.body?.source()?.readString(Charset.forName(request.charset))?.let {
            JSONObject(it)
        } ?: throw ConvertException("Response body is null!")
    }
}