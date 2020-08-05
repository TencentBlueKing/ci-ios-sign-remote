package com.tencent.bk.devops.atom.api

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.pojo.IpaSignInfo
import com.tencent.bk.devops.atom.pojo.Result
import com.tencent.bk.devops.atom.utils.json.JsonUtil
import okhttp3.MediaType
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

class SignServiceApi: BaseApi() {

    private val logger = LoggerFactory.getLogger(SignServiceApi::class.java)

    fun getBase64Encode(info: IpaSignInfo): String {
        val path = "/ms/sign/api/build/ipaSignInfo/base64Encode"
        val request = buildPost(path)

        var responseContent: String? = null
        try {
            responseContent = request(request, "get sub-pipeline startUpInfo error")
        } catch (e: IOException) {
            throw RuntimeException("encode request failed: path=$path")
        }
        val resultData = JsonUtil.fromJson(responseContent, object : TypeReference<Result<String>>() {})
        if (resultData.status == 0) {
            throw RuntimeException("encode request failed, message: ${resultData.message}")
        }
        return resultData.data!!
    }

    fun ipaSign(encodedInfo: String, file: File): String {
        val path = "/ms/sign/api/build/ipa/sign"
        val headers = mutableMapOf("X-DEVOPS-SIGN-INFO" to encodedInfo)
        val requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), file)
        logger.info(headers.toString())
        val request = buildPost(path, requestBody, headers)
        logger.info(request.toString())
        var responseContent: String? = null
        try {
            responseContent = request(request, "sign request failed：path=$path, info=${file.canonicalPath}, headers=$headers")
        } catch (e: IOException) {
            throw RuntimeException("sign request failed: path=$path, info=${file.canonicalPath}, headers=$headers")
        }
        return if (responseContent != null) {
            val result: Result<String> = JsonUtil.fromJson(responseContent, object : TypeReference<Result<String>>() {})
            result.data!!
        } else {
            throw RuntimeException("sign request failed: path=$path, info=${file.canonicalPath}, headers=$headers")
        }
    }

    fun signStatus(resignId: String): Boolean {
        val path = "/ms/sign/api/build/ipa/sign/$resignId/status"

        val request = buildGet(path)
        var responseContent: String? = null
        try {
            responseContent = request(request, "请求签名状态失败：path=$path")
        } catch (e: IOException) {
            throw RuntimeException("status request failed: path=$path")
        }
        return if (null != responseContent) {
            val result: Result<Boolean> = JsonUtil.fromJson(responseContent, object : TypeReference<Result<Boolean>>() {})
            result.data!!
        } else {
            throw RuntimeException("statis request failed: path=$path")
        }
    }
}