package org.usvm.types

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypeVariable
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.isAssignable
import org.jacodb.api.jvm.ext.objectClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.InMemoryHierarchyCache
import org.usvm.algorithms.cached
import org.usvm.util.ApproximationPaths
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.time.Duration

class JcTypeSystem(
    private val cp: JcClasspath,
    persistence: JcDatabasePersistence,
    override val typeOperationsTimeout: Duration,
    approximationPaths: ApproximationPaths = ApproximationPaths()
) : UTypeSystem<JcType> {
    private val scorer = ScorerExtension(cp, persistence, approximationPaths)

    val objectClass: JcClassOrInterface by lazy { cp.objectClass }

    val classClass: JcClassOrInterface by lazy {
        cp.findClassOrNull("java.lang.Class") ?: error("No class type in classpath")
    }

    val stringClass: JcClassOrInterface by lazy {
        cp.findClassOrNull("java.lang.String") ?: error("No string type in classpath")
    }

    val enumClass: JcClassOrInterface by lazy {
        cp.findClassOrNull("java.lang.Enum") ?: error("No enum type in classpath")
    }

    val objectType: JcClassType by lazy { classTypeOf(objectClass) }
    val classType: JcClassType by lazy { classTypeOf(classClass) }
    val stringType: JcClassType by lazy { classTypeOf(stringClass) }
    val enumType: JcClassType by lazy { classTypeOf(enumClass) }

    override fun isSupertype(supertype: JcType, type: JcType): Boolean =
        when {
            supertype == type -> true
            supertype is JcTypeVariable ->
                isSupertype(objectType, type) && supertype.bounds.all { isSupertype(it, type) }

            type is JcTypeVariable -> supertype == objectType || type.bounds.any { isSupertype(supertype, it) }
            else -> type.isAssignable(supertype)
        }


    private fun isInterface(type: JcType): Boolean =
        (type as? JcClassType)?.jcClass?.isInterface ?: false

    override fun hasCommonSubtype(type: JcType, types: Collection<JcType>): Boolean {
        when {
            type is JcPrimitiveType -> {
                return types.isEmpty()
            }

            isInterface(type) -> {
                return types.none { it is JcArrayType || it is JcPrimitiveType }
            }

            type is JcClassType -> {
                return types.all {
                    // It is guaranteed that it </: [type]
                    isInterface(it) || isSupertype(it, type)
                }
            }

            type is JcArrayType -> {
                val elementTypes = types.mapNotNull {
                    when {
                        it is JcArrayType -> it.elementType
                        it == objectType -> null
                        it is JcTypeVariable -> it.bounds.first()
                        else -> return false
                    }
                }
                return hasCommonSubtype(type.elementType, elementTypes)
            }

            type is JcTypeVariable -> {
                val bounds = type.bounds
                return if (bounds.isEmpty()) {
                    types.none { it is JcPrimitiveType }
                } else {
                    bounds.all { hasCommonSubtype(it, types) }
                }
            }

            else -> error("Unexpected type: $type")
        }
    }

    override fun isFinal(type: JcType): Boolean = when (type) {
        is JcPrimitiveType -> true
        is JcClassType -> type.isFinal
        is JcArrayType -> isFinal(type.elementType)
        else -> false
    }

    override fun isInstantiable(type: JcType): Boolean =
        when (type) {
            is JcPrimitiveType -> true

            is JcRefType -> when (type) {
                is JcArrayType -> isInstantiable(type.elementType)
                is JcClassType -> !type.jcClass.isInterface && !type.jcClass.isAbstract
                else -> false
            }

            else -> error("Unknown type $type")
        }

    // TODO: deal with generics
    // TODO: handle object type, serializable and cloneable
    override fun findSubtypes(type: JcType): Sequence<JcType> = when (type) {
        is JcPrimitiveType -> emptySequence() // TODO: should not be called here
        objectType -> objectInheritors
        is JcArrayType -> findSubtypes(type.elementType).map { arrayTypeOf(it) }
        is JcClassType -> cache.findSubClasses(type.jcClass).map { classTypeOf(it) } // TODO: filter bad classes
        is JcTypeVariable -> findSubtypes(classTypeOf(type.jcClass))
        else -> error("Unknown type $type")
    }

    private val classTypeCache = hashMapOf<JcClassOrInterface, JcClassType>()
    private val arrayTypeCache = hashMapOf<JcType, JcArrayType>()

    fun classTypeOf(cls: JcClassOrInterface): JcClassType = classTypeCache.getOrPut(cls) {
        cp.typeOf(cls) as JcClassType
    }

    fun arrayTypeOf(elementType: JcType): JcArrayType = arrayTypeCache.getOrPut(elementType) {
        cp.arrayTypeOf(elementType)
    }

    fun eraseTypeVariables(cls: JcClassType): JcClassType = classTypeOf(cls.jcClass)

    fun findTypeOrNull(typeName: TypeName): JcType? = findTypeOrNull(typeName.typeName)

    fun findType(typeName: String): JcType = findTypeOrNull(typeName)
        ?: error("No type in class path: $typeName")

    fun findTypeOrNull(typeName: String): JcType? = cp.findTypeOrNull(typeName)

    private val allClassesSorted by lazy { scorer.allClassesSorted.cached() }

    fun findSubClasses(cls: JcClassOrInterface): Sequence<JcClassOrInterface> =
        if (cls == objectClass) {
            allClassesSorted
        } else {
            cache.findSubClasses(cls)
        }

    private val objectInheritors by lazy {
        allClassesSorted
            .flatMap { jcClass ->
                val type = classTypeOf(jcClass)
                sequenceOf(type, arrayTypeOf(type))
            } + arrayTypeOf(objectType)
    }

    private fun Sequence<JcClassOrInterface>.sortByScore(): Pair<Sequence<JcClassOrInterface>, Int> =
        mapTo(mutableListOf()) { it to scorer.getScore(it) }
            .run {
                sortByDescending { it.second }
                asSequence().map { it.first } to size
            }

    // TODO: refactor this constant
    private val cache = SubClassesCache(200)

    private inner class SubClassesCache(
        private val sizeThreshold: Int,
    ) {
        private val cache = ConcurrentHashMap<JcClassOrInterface, Sequence<JcClassOrInterface>>()
        fun findSubClasses(jcClass: JcClassOrInterface): Sequence<JcClassOrInterface> =
            cache.getOrElse(jcClass) {
                val (sequence, size) = subclasses(cp, jcClass)
                    .sortByScore()
                if (size >= sizeThreshold) {
                    cache[jcClass] = sequence
                }
                sequence
            }
    }

    private val registeredLocationIds: Set<Long> by lazy { cp.registeredLocations.mapTo(hashSetOf()) { it.id } }
    private val hierarchy by lazy {
        check(cp.db.isInstalled(InMemoryHierarchy)) { "No in memory hierarchy installed" }
        val hierarchies = InMemoryHierarchyAccess.accessHierarchiesField(InMemoryHierarchy)
        checkNotNull(hierarchies[cp.db]) { "No hierarchy" }
    }

    private fun subclasses(cp: JcClasspath, jcClass: JcClassOrInterface): Sequence<JcClassOrInterface> {
        if (jcClass.isFinal) {
            return sequenceOf(jcClass)
        }

        val classSymbolId = cp.db.persistence.findSymbolId(jcClass.name)
        val directSubclasses = hierarchy[classSymbolId] ?: return emptySequence()

        return directSubclasses.filterKeys { it in registeredLocationIds }.values.asSequence()
            .flatMap { classIds ->
                classIds.asSequence().map {
                    val className = cp.db.persistence.findSymbolName(it)
                    cp.findClass(className)
                }
            }
    }

    private val topTypeStream by lazy { USupportTypeStream.from(this, objectType) }

    override fun topTypeStream(): UTypeStream<JcType> =
        topTypeStream

    private object InMemoryHierarchyAccess {
        fun accessHierarchiesField(hierarchy: InMemoryHierarchy): Map<JcDatabase, InMemoryHierarchyCache> {
            val allProperties = hierarchy::class.declaredMemberProperties
            val hierarchiesProperty = allProperties.single { it.name == "hierarchies" }
            hierarchiesProperty.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return hierarchiesProperty.call() as Map<JcDatabase, InMemoryHierarchyCache>
        }
    }
}
