package machine.interpreter.transformers.springjpa.query.specification

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.expression.toLambda
import org.jacodb.api.jvm.JcMethod
import org.usvm.spring.query.specification.ByExpression

fun ByExpression.getTranslateMethod(info: CommonInfo): JcMethod {
    translateMethod?.also { return it as JcMethod }
    translateMethod = expr.toLambda(info)
    return translateMethod!! as JcMethod
}
