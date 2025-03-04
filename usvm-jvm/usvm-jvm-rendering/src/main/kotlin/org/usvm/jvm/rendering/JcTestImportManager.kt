package org.usvm.jvm.rendering

import org.jacodb.api.jvm.JcClassOrInterface
import org.usvm.test.api.UTestInst

interface JcTestImportManager {
    fun tryAdd(name: String)
    fun tryAdd(jcType: JcClassOrInterface)
    fun on(inst: UTestInst)
}
