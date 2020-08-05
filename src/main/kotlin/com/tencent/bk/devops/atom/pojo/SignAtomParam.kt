package com.tencent.bk.devops.atom.pojo

import lombok.Data
import lombok.EqualsAndHashCode

@Data
@EqualsAndHashCode(callSuper = true)
class SignAtomParam : AtomBaseParam() {
    var ipaPath: String? = null
    var scenarioType: String? = null
    var profileType: String? = null
    var appId: String? = null
    var mainProfile: String? = null
    var customize = false
    var destPath: String? = null
    var appexList: String? = null
    var appexArrayList: List<AppexParam>? = null
    var universalLinks: String? = null
    var keychaimAccessGroups: String? = null
    var userAgree: Boolean? = null
}
