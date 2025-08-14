package machine.interpreter.transformers.springjpa.query.table

import org.usvm.spring.query.table.EntityName

fun EntityName.name() = names.joinToString(separator = ".")
