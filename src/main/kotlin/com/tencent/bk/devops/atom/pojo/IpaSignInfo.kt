package com.tencent.bk.devops.atom.pojo

data class IpaSignInfo(

    var userId: String = "",

    var wildcard: Boolean = true,

    var fileName: String? = null,

    var fileSize: Long? = null,

    var md5: String? = null,

    var certId: String? = null,

    var archiveType: String? = "PIPELINE",

    var projectId: String? = null,

    var pipelineId: String? = null,

    var buildId: String? = null,

    var taskId: String? = null,

    var archivePath: String? = "/",

    var mobileProvisionId: String? = null,

    var universalLinks: List<String>? = null,

    var keychaimAccessGroups: List<String>? = null,

    var replaceBundleId: Boolean? = false,

    var appexSignInfo: List<AppexSignInfo>? = null
)