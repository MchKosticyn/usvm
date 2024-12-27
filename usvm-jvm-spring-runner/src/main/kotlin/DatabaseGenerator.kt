package bench

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcMutableInstList
import org.jacodb.api.jvm.cfg.JcRawArrayAccess
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawClassConstant
import org.jacodb.api.jvm.cfg.JcRawFieldRef
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawNewArrayExpr
import org.jacodb.api.jvm.cfg.JcRawSpecialCallExpr
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.cfg.JcRawInt
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.usvm.jvm.util.isSameSignature
import org.usvm.jvm.util.replace
import org.usvm.jvm.util.write
import org.usvm.machine.interpreter.transformers.springjpa.APPROX_NAME
import org.usvm.machine.interpreter.transformers.springjpa.BASE_TABLE_MANAGER
import org.usvm.machine.interpreter.transformers.springjpa.NO_ID_TABLE_MANAGER
import org.usvm.util.JcTableInfoCollector
import org.usvm.util.TableInfo
import org.usvm.util.genericTypes
import org.usvm.util.jvmDescriptor
import org.usvm.util.typeName
import java.nio.file.Path

class DatabaseGenerator(
    private val cp: JcClasspath,
    private val dir: Path,
    private val repositories: List<JcClassOrInterface>
) {

    val databasesClass = cp.findClass("generated.org.springframework.boot.databases.samples.SpringDatabases")
    val baseTableManagerClass = cp.findClass(BASE_TABLE_MANAGER)
    val noIdTableManagerClass = cp.findClass(NO_ID_TABLE_MANAGER)
    val clinitMethod = databasesClass.declaredMethods.find { it.name == "<clinit>" }!!
    val rawInstList = clinitMethod.rawInstList.toMutableList()
    val tableInfoCollector = JcTableInfoCollector(cp)
    val localVars = LocalVarsManager(0)

    val tableAssign = rawInstList[2] as JcRawAssignInst // %0 = new BaseTable
    val classTypesAssign = rawInstList[3] as JcRawAssignInst // %1 = new kotlin.Unit
    val argAssign = rawInstList[4] as JcRawAssignInst // %2 = java.lang.String.class
    val updateClassTypes = rawInstList[5] as JcRawAssignInst // %1[0] = %2
    val callExpr = rawInstList[8] as JcRawCallInst // %0.<init>(1, %1)
    val updateRef = rawInstList[9] as JcRawAssignInst // SpringDatabases._blanck = %0

    val altTableAssign = rawInstList[12] as JcRawAssignInst // %4 = new NoIdTable
    val altCallExpr = rawInstList[18] as JcRawCallInst // %4.<init>(%5)

    val ret = rawInstList[20]

    fun generateJPADatabase(): JcTableInfoCollector {

        repositories.forEach { repo ->
            val genericTypes = repo.signature!!.genericTypes
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
            rawInstList.removeAll(listOf(2..19).flatten().map { rawInstList[it] })

            tableInfoCollector.allTables().forEach {
                it.generate(this, localVars, classNode, rawInstList)
            }

            clinitMethod.withAsmNode { clinitAsmNode ->
                val newNode = MethodNodeBuilder(clinitMethod, rawInstList).build()
                val asmMethods = classNode.methods
                val asmMethod = asmMethods.find { clinitAsmNode.isSameSignature(it) }!!
                check(asmMethods.replace(asmMethod, newNode))
            }

            classNode.write(cp, dir.resolve("$className.class"), checkClass = true)
        }

        return tableInfoCollector
    }
}

class LocalVarsManager {

    private var lastIndex: Int

    constructor(startIndex: Int) {
        lastIndex = if (startIndex < 0) 0 else startIndex
    }

    private fun newName(): String {
        return "%${lastIndex}"
    }

    fun newLocalVar(type: TypeName): JcRawLocalVar {
        val v = JcRawLocalVar(lastIndex, newName(), type)
        lastIndex++

        return v
    }
}

fun TableInfo.generate(
    generator: DatabaseGenerator,
    localVars: LocalVarsManager,
    classNode: ClassNode,
    rawInstList: JcMutableInstList<JcRawInst>
) {
    val idColumn = if (this is TableInfo.TableWithIdInfo) idColumn else null
    val hasId = idColumn != null
    idColumn?.let { columns.toMutableList().add(it) }
    val allColumns = columns.sortedBy { it.name }

    // %0 = new BaseTable || %0 = new NoIdTable
    val tableAssign = if (hasId) generator.tableAssign else generator.altTableAssign
    val classTypesAssign = generator.classTypesAssign // %1 = new kotlin.Unit
    val argAssign = generator.argAssign // %2 = java.lang.String.class
    val updateClassTypes = generator.updateClassTypes // %1[0] = %2
    // %0.<init>(1, %1) || %0.<init>(%1)
    val callExpr = if (hasId) generator.callExpr else generator.altCallExpr
    val updateRef = generator.updateRef // SpringDatabases._blanck = %0
    val ret = generator.ret

    val taLhv = localVars.newLocalVar(tableAssign.lhv.typeName)
    val newTableAssign = JcRawAssignInst(tableAssign.owner, taLhv, tableAssign.rhv) as JcRawInst

    val ctaLhv = localVars.newLocalVar(classTypesAssign.lhv.typeName)
    val ctaRhv = JcRawNewArrayExpr(classTypesAssign.rhv.typeName, listOf(JcRawInt(allColumns.count())))
    val newClassTypesAssign = JcRawAssignInst(classTypesAssign.owner, ctaLhv, ctaRhv) as JcRawInst

    val fieldName = name
    val desc =
        if (this is TableInfo.TableWithIdInfo) generator.baseTableManagerClass
        else generator.noIdTableManagerClass
    val tableField = FieldNode(
        Opcodes.ACC_STATIC,
        fieldName,
        desc.jvmDescriptor,
        null,
        null
    )
    classNode.fields.add(tableField)

    fun generateArgAssign(
        colType: TypeName,
        index: Int,
        argArray:
        JcRawLocalVar
    ): List<JcRawInst> {
        val aaLhv = localVars.newLocalVar(argAssign.lhv.typeName)
        val aaRhv = JcRawClassConstant(colType, "java.lang.Class".typeName)
        val newArgAssign = JcRawAssignInst(argAssign.owner, aaLhv, aaRhv) as JcRawInst

        val uctLhv = JcRawArrayAccess(argArray, JcRawInt(index), "java.lang.Class".typeName)
        val newUpdateClassTypes = JcRawAssignInst(updateClassTypes.owner, uctLhv, aaLhv) as JcRawInst

        return listOf(newArgAssign, newUpdateClassTypes)
    }

    val argAssigns =
        allColumns.mapIndexed { index, col -> generateArgAssign(col.type, index, ctaLhv) }.flatten()

    val oldCallExpr = callExpr.callExpr as JcRawSpecialCallExpr
    val args = idColumn?.let { listOf(JcRawInt(allColumns.indexOf(idColumn)), ctaLhv) } ?: listOf(ctaLhv)
    val cExpr = JcRawSpecialCallExpr(
        oldCallExpr.declaringClass,
        oldCallExpr.methodName,
        oldCallExpr.argumentTypes,
        oldCallExpr.returnType,
        taLhv,
        args
    )
    val newCallExpr = JcRawCallInst(callExpr.owner, cExpr) as JcRawInst

    val oldFieldRef = updateRef.lhv as JcRawFieldRef
    val fieldRef =
        JcRawFieldRef(oldFieldRef.instance, classNode.name.typeName, fieldName, oldFieldRef.typeName)
    val newUpdateRef = JcRawAssignInst(updateRef.owner, fieldRef, taLhv) as JcRawInst

    val newInst =
        listOf(newTableAssign, newClassTypesAssign) + argAssigns + listOf(
            newCallExpr,
            newUpdateRef
        )

    rawInstList.insertBefore(ret, newInst)
}
