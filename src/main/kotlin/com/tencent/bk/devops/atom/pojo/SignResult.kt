package com.tencent.bk.devops.atom.pojo

import java.time.LocalDateTime

//@ApiModel("签名状态查询结果")
data class SignResult(
//    @ApiModelProperty("签名ID", required = true)
    val resignId: String,
//    @ApiModelProperty("是否完成", required = true)
    val finished: Boolean
)