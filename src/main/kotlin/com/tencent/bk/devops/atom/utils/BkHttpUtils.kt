package com.tencent.bk.devops.atom.utils

import com.tencent.bk.devops.atom.api.BaseApi
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap

object BkHttpUtils : BaseApi() {
    private val logger: Logger = LoggerFactory.getLogger(BkHttpUtils::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val headers: Map<String, String> = mutableMapOf()

    @Throws(IOException::class)
    private fun getHttpResponse(request: Request): Response {
        val call = client.newCall(request)
        val response = call.execute()
        return if (response.isSuccessful) {
            response
        } else {
            if (response.code() == 500) {
                logger.error("Please check if tag name exists")
            }
            response
        }
    }

    fun doGet(path: String?): String? {
        val request = super.buildGet(path)
        logger.info("the group request is :$request")
        val call = client.newCall(request)
        var response: Response? = null
        response = try {
            call.execute()
        } catch (e: IOException) {
            logger.error("Http Exception:$e")
            return null
        }
        if (response.isSuccessful) {
            if (response.body() != null) {
                return try {
                    response.body()!!.string()
                } catch (e: IOException) {
                    logger.error("Http Exception:$e")
                    null
                }
            }
        }
        logger.error("Fail to do get request {url = {}} with response {}|{}|{}", request.url().toString().split("\\?".toRegex()).toTypedArray()[0], response.code(), response.message(), response.body())
        return null
    }

    fun doPost(path: String, jsonString: String?): Response? {
        val requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonString)
        Request.Builder().url(path).headers(Headers.of(headers)).post(requestBody).build()
        val request = Request.Builder().url(path).headers(Headers.of(headers)).post(requestBody).build()
        var response: Response? = null
        response = try {
            getHttpResponse(request)
        } catch (e: IOException) {
            logger.error("IOException $e")
            return null
        }
        return response
    }

    fun doDelete(path: String?): Boolean? {
        val request = Request.Builder().url(path).delete().build()
        val call = client.newCall(request)
        var response: Response? = null
        response = try {
            call.execute()
        } catch (e: IOException) {
            logger.error("Http Exception:$e")
            return null
        }
        return response.isSuccessful
    }

    fun doPostFile(url: String, file: File, headers: Map<String, String>? = null): Response? {
        return try {
            val fileName = file.name
            val fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file)
            val requestBody: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(fileBody)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
            headers?.forEach {
                request.addHeader(it.key, it.value)
            }
            var response: Response? = null
            response = getHttpResponse(request.build())
            logger.info("doPostFile response: {}", response)
            return response
        } catch (e: IOException) {
            logger.error("IOException $e")
            null
        }
    }

    //由于okhttp header 中的 value 不支持 null, \n 和 中文这样的特殊字符,所以这里
    //会首先替换 \n ,然后使用 okhttp 的校验方式,校验不通过的话,就返回 encode 后的字符串
    private fun getValueEncoded(value: String?): String? {
        return try {
            if (value == null) return "null"
            val newValue = value.replace("\n", "")
            var i = 0
            val length = newValue.length
            while (i < length) {
                val c = newValue[i]
                if (c <= '\u001f' || c >= '\u007f') {
                    return URLEncoder.encode(newValue, "UTF-8")
                }
                i++
            }
            newValue
        } catch (e: Exception) {
            logger.error("encode value with Exception:$e")
            null
        }
    }
}