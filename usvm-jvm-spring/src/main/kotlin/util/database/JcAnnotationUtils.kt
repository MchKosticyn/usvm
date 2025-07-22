package util.database

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.impl.types.AnnotationInfo

fun nameEquals(annotation: JcAnnotation, name: String) = annotation.jcClass?.simpleName.equals(name)

fun contains(annotations: List<JcAnnotation>, name: String) = annotations.any { nameEquals(it, name) }

fun contains(annotation: List<JcAnnotation>, names: List<String>) = names.any { contains(annotation, it) }

fun containsAll(annotations: List<JcAnnotation>, names: List<String>) = names.all { contains(annotations, it) }

fun find(annotations: List<JcAnnotation>, name: String) = annotations.find { nameEquals(it, name) }

fun blancAnnotation(name: String) = AnnotationInfo(name, false, listOf(), null, null)
