package org.usvm.jvm.rendering

class JcTestBlockRenderer(
    importManager: JcImportManager
): JcBlockRenderer(importManager) {


    override fun newInnerBlock(): JcTestBlockRenderer {
        return JcTestBlockRenderer(importManager)
    }
}
