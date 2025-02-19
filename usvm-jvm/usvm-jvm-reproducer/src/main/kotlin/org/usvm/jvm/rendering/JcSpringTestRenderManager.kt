package org.usvm.jvm.rendering

import org.jacodb.api.jvm.JcClasspath


class JcSpringTestRenderManager : UTestRenderManager<JcSpringTestMeta> {
    override fun render(cp: JcClasspath, tests: List<UTestRenderWrapper<JcSpringTestMeta>>) {
        val classes = tests.groupBy { wrapper -> wrapper.meta }
        classes.forEach { cls ->
            val renderer = JcSpringTestClassRenderer.loadFileOrCreateFor(cls.key)
            renderer.renderToFile(cp, cls.value)

        }
    }
}