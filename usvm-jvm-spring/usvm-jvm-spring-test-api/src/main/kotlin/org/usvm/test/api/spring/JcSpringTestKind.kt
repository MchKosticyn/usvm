package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClassOrInterface

sealed interface JcSpringTestKind

data class WebMvcTest(
    // `null` means all controllers
    private var _controllerType: JcClassOrInterface? = null,
) : JcSpringTestKind {

    val controllerType get() = _controllerType

    fun ensureInitialized(controllerType: JcClassOrInterface) {
        if (this._controllerType == null)
            this._controllerType = controllerType
        else
            check(this._controllerType == controllerType)
    }
}

data class SpringBootTest(
    val springApplicationType: JcClassOrInterface
) : JcSpringTestKind

// TODO: support
class SpringJpaTest : JcSpringTestKind
