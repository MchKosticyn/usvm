package features

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult
import utils.getLambdaCanonicalTypeName

internal object JcLambdaFeature: JcClasspathExtFeature {

    private val lambdaJcClassesByName: MutableMap<String, JcClassOrInterface> = mutableMapOf()
    private val lambdaClassesByName: MutableMap<String, Class<*>> = mutableMapOf()

    fun addLambdaClass(lambdaClass: Class<*>, jcClass: JcClassOrInterface) {
        val realName = jcClass.name
        val canonicalName = getLambdaCanonicalTypeName(realName)
        lambdaJcClassesByName[canonicalName] = jcClass
        lambdaClassesByName[realName] = lambdaClass
    }

    fun lambdaClassByName(name: String): Class<*>? {
        return lambdaClassesByName[name]
    }

    override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
        return lambdaJcClassesByName[name]?.let { AbstractJcResolvedResult.JcResolvedClassResultImpl(name, it) }
    }
}
