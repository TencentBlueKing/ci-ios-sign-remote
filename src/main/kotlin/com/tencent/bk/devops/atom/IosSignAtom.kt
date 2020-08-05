package com.tencent.bk.devops.atom

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.tencent.bk.devops.atom.api.SignServiceApi
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.pojo.AppexParam
import com.tencent.bk.devops.atom.pojo.AppexSignInfo
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.atom.pojo.IpaSignInfo
import com.tencent.bk.devops.atom.pojo.SignAtomParam
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.atom.utils.SignInfoEncodeUtils.encodeIpaSignInfo
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.ArrayList

/**
 * @version 1.0
 */
@AtomService(paramClass = SignAtomParam::class)
class IosSignAtom : TaskAtom<SignAtomParam> {

    private val TIMEOUT_MINUTES = 10
    private val SLEEP_SECONDS = 5

    /**
     * 执行主入口
     *
     * @param atomContext 插件上下文
     */
    override fun execute(atomContext: AtomContext<SignAtomParam>) {

        val param = atomContext.param
        val result = atomContext.result
        val data = result.data
        checkParam(param, result)
        if (result.status != Status.success) {
            logger.error("Params error: {}", result.message)
            return
        }

        val workspace: String = param.bkWorkspace
        val ipaFile = File(workspace + File.separator + param.ipaPath)
        val info = getIpaSignInfo(param, ipaFile)

        if (ipaFile.exists() && ipaFile.isFile) {
            try {
                logger.info(info.toString())
                logger.info(encodeIpaSignInfo(info))
                val resignId = SignServiceApi().ipaSign(encodeIpaSignInfo(info), ipaFile)
                var times = 1
                while (true) {
                    Thread.sleep((SLEEP_SECONDS * 1000).toLong())
                    val finished = SignServiceApi().signStatus(resignId)
                    if (finished) {
                        break
                    } else {
                        logger.info("[$resignId] Sign running...")
                        times++
                        if (times > (TIMEOUT_MINUTES * 60)/SLEEP_SECONDS) {
                            result.status = Status.error
                            result.message = "Sign task timeout for $TIMEOUT_MINUTES mins"
                            logger.info("Sign task timeout for $TIMEOUT_MINUTES mins")
                            return
                        }
                    }
                }
            } catch (e: JsonProcessingException) {
                e.printStackTrace()
                result.status = Status.error
                result.message = "Load sign response with error: $e"
                logger.info("Load sign response with error: $e")
                return
            } catch (e: InterruptedException) {
                e.printStackTrace()
                result.status = Status.error
                result.message = "Status request with error: $e"
                logger.info("Status request with error: $e")
                return
            }
        } else {
            throw AtomException("$ipaFile resign failed.")
        }

        result.status = Status.success
        result.message = "Sign isfinished, please check Artifacts"
    }

    /**
     * 检查参数
     *
     * @param param 请求参数
     * @param result 结果
     */
    private fun checkParam(param: SignAtomParam, result: AtomResult) {
        // 参数检查
        try {

            logger.info("Profile type：" + param.profileType)
            Preconditions.checkArgument(param.userAgree ?: false, "必须同意使用协议！")
            val appexStr = param.appexList

            val mapper = ObjectMapper()
            param.appexArrayList = if (appexStr.isNullOrBlank() || param.profileType == "single") null
            else mapper.readValue<ArrayList<AppexParam>>(appexStr, object : TypeReference<ArrayList<AppexParam?>?>() {})

            //扩展应用检查
            val appexList = param.appexArrayList
            if (appexList != null && appexList.isNotEmpty()) {
                for (appex in appexList) {
                    val kvs = appex.values
                    if (kvs == null || kvs.size < 2) {
                        result.status = Status.error
                        result.message = "appex info input with error"
                        return
                    }
                    for (kv in kvs) {
                        logger.info("kv:$kv")
                        Preconditions.checkArgument(!Strings.isNullOrEmpty(kv.value), "appex info cannot be blank")
                    }
                }
            }
        } catch (e: Exception) {
            result.status = Status.failure
            result.message = e.localizedMessage
        }
    }

    //必须在 checkParam 后调用
    private fun getAppexSign(param: SignAtomParam): List<AppexSignInfo>? {
        val pList = param.appexArrayList
        if (pList == null || pList.isEmpty()) {
            return null
        }
        val sList = mutableListOf<AppexSignInfo>()
        pList.forEach { appex ->
            val kvs = appex.values ?: return@forEach
            val signInfo = AppexSignInfo(kvs[0].value, kvs[1].value)
            logger.info("AppexSignInfo: $signInfo")
            sList.add(signInfo)
        }
        return sList
    }

    /**
     * 组装签名请求
     *
     * @param param 请求参数
     */
    @Throws(IOException::class)
    private fun getIpaSignInfo(param: SignAtomParam, ipaFile: File): IpaSignInfo {

        val userId = IosSignAtom::class.java.name
        val fileName = ipaFile.name
        val fileSize = ipaFile.totalSpace
        var md5: String? = null
        md5 = if (!ipaFile.exists()) {
            throw IOException(ipaFile.toString() + "file not found")
        } else {
            DigestUtils.md5Hex(FileInputStream(ipaFile))
        }

        // 使用两种不同证书的判断
        var certId: String? = null
        certId = if (param.scenarioType == "inner") {
            param.bkSensitiveConfInfo[INHOUSE_CERT_ID_KEY]
        } else {
            param.bkSensitiveConfInfo[OUTHOUSE_CERT_ID_KEY]
        }
        var archiveType = "PIPELINE"
        // 选择不同归档方式
        if (param.customize) {
            archiveType = "CUSTOM"
        }
        val archivePath = param.destPath
        val projectId = param.projectName
        val pipelineId = param.pipelineId
        val buildId = param.pipelineBuildId
        val taskId = param.pipelineTaskId

        // 主描述文件ID
        val mobileProvisionId = param.mainProfile

        // 是否使用通配方式
        val wildcard = false

        // TODO 是否替换Bundle，暂时认为通配下不替换，其他情况均替换
        val replaceBundleId = !wildcard

        // 组装ul数组
        var universalLinks: List<String>? = null
        if (!param.universalLinks.isNullOrEmpty()) {
            universalLinks = param.universalLinks?.split(';')
        }

        // 组装appGroup数
        var keychaimAccessGroups: List<String>? = null
        if (!param.keychaimAccessGroups.isNullOrEmpty()) {
            keychaimAccessGroups = param.keychaimAccessGroups?.split(';')
        }

        // 组装拓展Info数组
        val appexSignInfo = getAppexSign(param)

        return IpaSignInfo(
            userId = userId,
            wildcard = wildcard,
            fileName = fileName,
            fileSize = fileSize,
            md5 = md5,
            certId = certId,
            archiveType = archiveType,
            projectId = projectId,
            pipelineId = pipelineId,
            buildId = buildId,
            taskId = taskId,
            archivePath = archivePath,
            mobileProvisionId = mobileProvisionId,
            universalLinks = universalLinks,
            keychaimAccessGroups = keychaimAccessGroups,
            replaceBundleId = replaceBundleId,
            appexSignInfo = appexSignInfo
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IosSignAtom::class.java)
        private const val INHOUSE_CERT_ID_KEY = "INHOUSE_CERT_ID"
        private const val OUTHOUSE_CERT_ID_KEY = "OUTHOUSE_CERT_ID"
    }
}

fun main() {
    println(File.separator)
}