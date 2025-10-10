package jpa

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcField
import org.jacodb.impl.types.AnnotationInfo

fun nameEquals(annotation: JcAnnotation, name: String) = annotation.jcClass?.simpleName.equals(name)

fun contains(annotations: List<JcAnnotation>, name: String) = annotations.any { nameEquals(it, name) }

fun contains(annotation: List<JcAnnotation>, names: List<String>) = names.any { contains(annotation, it) }

fun containsAll(annotations: List<JcAnnotation>, names: List<String>) = names.all { contains(annotations, it) }

fun find(annotations: List<JcAnnotation>, name: String) = annotations.find { nameEquals(it, name) }

fun blancAnnotation(name: String) = AnnotationInfo(name, false, listOf(), null, null)

const val ONE_TO_ONE = "OneToOne"
const val ONE_TO_MANY = "OneToMany"
const val MANY_TO_ONE = "ManyToOne"
const val MANY_TO_MANY = "ManyToMany"

const val ID_ANNOTATION = "Id"

val relationAnnotations = listOf(
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY
)

fun JcField.isRelation() = contains(annotations, relationAnnotations)

fun JcField.isId() = contains(annotations, ID_ANNOTATION)
