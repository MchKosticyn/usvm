package org.usvm.test.internal

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.usvm.test.api.HeaderAttr
import org.usvm.test.api.ParamAttr
import org.usvm.test.api.SpringReqAttr
import org.usvm.test.api.SpringReqKind
import org.usvm.test.api.SpringReqPath
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestStaticMethodCall
import org.usvm.test.api.UTestStringExpression
import org.usvm.test.api.stringType

class SpringRequestBuilder private constructor(
    private val initStatements: MutableList<UTestInst>,
    private var reqDSL: UTestExpression,
    private val cp: JcClasspath
) {
    companion object {
        fun createReq(cp: JcClasspath, kind: SpringReqKind, path: SpringReqPath): SpringRequestBuilder =
            commonReqDSLBuilder(kind.toString(), cp, path.path, path.pathVariables)

        private const val MOCK_MVC_REQUEST_BUILDERS_CLASS =
            "org.springframework.test.web.servlet.request.MockMvcRequestBuilders"

        private fun commonReqDSLBuilder(
            type: String,
            cp: JcClasspath,
            path: String,
            pathVariables: List<Any>
        ): SpringRequestBuilder {
            val staticMethod = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CLASS, type).method
            val initDSL = mutableListOf<UTestInst>()
            val argsDSL = mutableListOf<UTestExpression>()
            argsDSL.add(UTestStringExpression(path, cp.stringType))
            argsDSL.addAll(pathVariables.map { UTestStringExpression(it.toString(), cp.stringType) })

            return SpringRequestBuilder(
                initStatements = initDSL,
                reqDSL = UTestStaticMethodCall(staticMethod, argsDSL),
                cp = cp
            )
        }
    }

    fun getInitDSL(): List<UTestInst> = initStatements

    fun getDSL() = reqDSL

    fun addParam(attr: ParamAttr): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CLASS, "param").method
        addStrArrOfStrCallDSL(method, attr.name, attr.values)
        return this
    }

    fun addHeader(attr: HeaderAttr): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CLASS, "header").method
        addStrArrOfStrCallDSL(method, attr.name, attr.values)
        return this
    }

    private fun addStrArrOfStrCallDSL(mName: JcMethod, str: String, arrOfStr: List<Any>) {
        val argsDSL = mutableListOf<UTestExpression>()
        argsDSL.add(UTestStringExpression(str, cp.stringType))
        argsDSL.addAll(arrOfStr.map { UTestStringExpression(it.toString(), cp.stringType) })
        UTestMethodCall(
            instance = reqDSL,
            method = mName,
            args = argsDSL,
        ).also { reqDSL = it }
    }

    fun addAttrs(attrs: List<SpringReqAttr>): SpringRequestBuilder {
        attrs.forEach { attr ->
            when (attr) {
                is ParamAttr -> addParam(attr)
                is HeaderAttr -> addHeader(attr)
            }
        }
        return this
    }
}