package org.usvm.jvm.rendering

import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTest


// TODO possible refactoring: extract interface for test meta so it will be possible to
//  do smth like testMeta.testClassName or testMeta.resultingMethodName

data class UTestRenderWrapper<TestMeta>(val test: UTest, val meta: TestMeta)

interface UTestRenderManager<TestMeta> {
    fun render(cp: JcClasspath, tests: List<UTestRenderWrapper<TestMeta>>)
}
