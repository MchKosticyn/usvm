package bench

import machine.interpreter.transformers.springjpa.APPROX_NAME
import machine.interpreter.transformers.springjpa.JAVA_CLASS
import machine.interpreter.transformers.springjpa.JAVA_INIT
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.cfg.JcRawArrayAccess
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawClassConstant
import org.jacodb.api.jvm.cfg.JcRawFieldRef
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawNewArrayExpr
import org.jacodb.api.jvm.cfg.JcRawNewExpr
import org.jacodb.api.jvm.cfg.JcRawSpecialCallExpr
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.cfg.JcRawInt
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.isSameSignature
import org.usvm.jvm.util.jvmDescriptor
import org.usvm.jvm.util.replace
import org.usvm.jvm.util.typeName
import org.usvm.jvm.util.write
import util.database.JcMethodNewBodyContext
import util.database.JcTableInfoCollector
import util.database.TableInfo
import java.io.File

class DatabaseGenerator(
    private val cp: JcClasspath,
    private val dir: File,
    private val repositories: List<JcClassOrInterface>
) {

    val databasesClass = cp.findClass("generated.org.springframework.boot.databases.samples.SpringDatabases")
    val clinitMethod = databasesClass.declaredMethods.single { it.name == "<clinit>" }
    val tableInfoCollector = JcTableInfoCollector(cp)
    val newBodyContext = JcMethodNewBodyContext(clinitMethod)

    fun generateJPADatabase(): JcTableInfoCollector {

        repositories.filter { it.signature != null }.forEach { repo ->
            val genericTypes = repo.signature!!.genericTypesFromSignature
            val dataClass = cp.findClass(genericTypes[0])

            tableInfoCollector.collectTable(dataClass)
        }

        val className = "SpringDatabases"
        databasesClass.withAsmNode { classNode ->

            val annot = AnnotationNode(cp.findClass(APPROX_NAME).jvmDescriptor)
            annot.values = listOf("value", Type.getType("Lstub/spring/SpringDatabases;"))

            classNode.visibleAnnotations = listOf(annot)

            classNode.name = className
            classNode.fields.removeAll { true }

            tableInfoCollector.allTables().forEach { table ->
                table.addNewField(cp, classNode)
                newBodyContext.generateFieldInitialize(cp, table, classNode)
            }

            clinitMethod.withAsmNode { clinitAsmNode ->
                val newInst = newBodyContext.buildNewBody()
                val newNode = MethodNodeBuilder(clinitMethod, newInst).build()
                val asmMethods = classNode.methods
                val asmMethod = asmMethods.find { clinitAsmNode.isSameSignature(it) }!!
                check(asmMethods.replace(asmMethod, newNode))
            }

            classNode.write(cp, dir.resolve("$className.class").toPath(), checkClass = true)
        }

        return tableInfoCollector
    }
}

private fun TableInfo.addNewField(
    cp: JcClasspath,
    classNode: ClassNode
) {
    val desc = cp.findClass(approximateManagerClassName)
    val tableField = FieldNode(
        Opcodes.ACC_STATIC,
        name,
        desc.jvmDescriptor,
        null,
        null
    )
    classNode.fields.add(tableField)
}

private fun JcMethodNewBodyContext.generateFieldInitialize(
    cp: JcClasspath,
    table: TableInfo,
    classNode: ClassNode
) {
    val idColumn = if (table is TableInfo.TableWithIdInfo) table.idColumn else null
    idColumn?.let { table.columns.toMutableList().add(it) }
    val allColumns = table.columns.sortedBy { it.name }

    val tblType = table.approximateManagerClassName.typeName

    val newTblVar = newVar(tblType)
    val newTbl = JcRawNewExpr(tblType)
    addInstruction { owner -> JcRawAssignInst(owner, newTblVar, newTbl) }

    val typeArrVar = newVar("java.lang.Class[]".typeName)
    val newTypeArr = JcRawNewArrayExpr(JAVA_CLASS.typeName, listOf(JcRawInt(allColumns.count())))
    addInstruction { owner -> JcRawAssignInst(owner, typeArrVar, newTypeArr) }

    allColumns.forEachIndexed { index, col ->
        val typeVar = newVar(JAVA_CLASS.typeName)
        val typ = JcRawClassConstant(col.type, JAVA_CLASS.typeName)
        addInstruction { owner -> JcRawAssignInst(owner, typeVar, typ) }

        val arrAcc = JcRawArrayAccess(typeArrVar, JcRawInt(index), JAVA_CLASS.typeName)
        addInstruction { owner -> JcRawAssignInst(owner, arrAcc, typ) }
    }

    val initMethod = cp.findClass(table.approximateManagerClassName)
        .declaredMethods.single { it.name == JAVA_INIT && it.parameters.isNotEmpty() }
    val args = idColumn?.let {
        val idColIndex = JcRawInt((table as TableInfo.TableWithIdInfo).idColIndex())
        val validators = generateValidators(table)
        listOf(idColIndex, typeArrVar, validators)
    } ?: listOf(typeArrVar)
    val initCall = JcRawSpecialCallExpr(
        tblType,
        JAVA_INIT,
        initMethod.parameters.map { it.type },
        initMethod.returnType,
        newTblVar,
        args
    )

    addInstruction { owner -> JcRawCallInst(owner, initCall) }

    val fieldRef = JcRawFieldRef(null, classNode.name.typeName, table.name, tblType)
    addInstruction { owner -> JcRawAssignInst(owner, fieldRef, newTblVar) }
}

private fun JcMethodNewBodyContext.generateValidators(
    table: TableInfo
): JcRawLocalVar {
    val contValidatorName = "jakarta.validation.ConstraintValidator"

    val tableValidators = newVar("${contValidatorName}[][]".typeName)
    val tblVInit = JcRawNewArrayExpr("${contValidatorName}[][]".typeName, listOf(JcRawInt(table.columns.size)))
    addInstruction { owner -> JcRawAssignInst(owner, tableValidators, tblVInit) }

    table.columnsInOrder().forEachIndexed { ix, col ->
        val validators = col.validators

        val validatorsArr = newVar("${contValidatorName}[]".typeName)
        val vsInit = JcRawNewArrayExpr(contValidatorName.typeName, listOf(JcRawInt(validators.size)))
        addInstruction { owner -> JcRawAssignInst(owner, validatorsArr, vsInit) }

        validators.forEachIndexed { valIx, (annot, validator) ->
            val valid = validator.initializeValidator(this, annot)
            val vsAccess = JcRawArrayAccess(validatorsArr, JcRawInt(valIx), contValidatorName.typeName)
            addInstruction { owner -> JcRawAssignInst(owner, vsAccess, valid) }
        }

        val vsAccess = JcRawArrayAccess(tableValidators, JcRawInt(ix), "${contValidatorName}[]".typeName)
        addInstruction { owner -> JcRawAssignInst(owner, vsAccess, validatorsArr) }
    }

    return tableValidators
}
