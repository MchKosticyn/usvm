package org.usvm.util

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.impl.types.AnnotationInfo

fun nameEquals(annotation: JcAnnotation, name : String) : Boolean {
    return annotation.jcClass?.simpleName.equals(name)
}

fun contains(annotations : List<JcAnnotation>, name : String) : Boolean {
    return annotations.any { nameEquals(it, name) }
}

fun contains(annotation: List<JcAnnotation>, names : List<String>) : Boolean {
    return names.any { contains(annotation, it) }
}

fun find(annotations: List<JcAnnotation>, name : String) : JcAnnotation? {
    return annotations.find { nameEquals(it, name) }
}

fun blancAnnotation(name : String) : AnnotationInfo {
    return AnnotationInfo(name, false, listOf(), null, null)
}
