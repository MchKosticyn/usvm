package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall

class SpringTablesBuilder(
    private val cp: JcClasspath,
    private val testClass: UTestExpression
) {

    private val statements: MutableList<UTestInst> = mutableListOf()

    private val entityManager: UTestExpression by lazy {
        val testClassType = (testClass.type as JcClassType).jcClass
        val entityManagerField = testClassType.declaredFields.find { it.type.typeName == "jakarta.persistence.EntityManager" }
            ?: getSpringTestClassesFeatureIn(cp).addEntityManagerField(testClassType)
        UTestGetFieldExpression(
            testClass,
            entityManagerField
        )
    }

    fun addTable(table : JcTableEntities) {
        for (entity in table.entities) {
            val entityManagerClass = (entityManager.type as JcClassType).jcClass
            val persistMethod = entityManagerClass.declaredMethods.find { it.name == "merge" }!!
            statements.add(UTestMethodCall(entityManager, persistMethod, listOf(entity)))
        }
    }

    fun getStatements() = statements
}
