package org.usvm.test.api.spring

import org.usvm.test.api.UTestExpression

data class JcTableEntities(
    val tableName: String,
    val entities: List<UTestExpression>
)
