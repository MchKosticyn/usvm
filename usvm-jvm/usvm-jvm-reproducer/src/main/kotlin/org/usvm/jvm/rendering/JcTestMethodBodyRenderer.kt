package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.ArrayCreationExpr
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.CharLiteralExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.ConditionalExpr
import com.github.javaparser.ast.expr.DoubleLiteralExpr
import com.github.javaparser.ast.expr.EnclosedExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.LongLiteralExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.type.ClassOrInterfaceType
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.toType
import org.usvm.test.api.ArithmeticOperationType
import org.usvm.test.api.ConditionType
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestArithmeticExpression
import org.usvm.test.api.UTestArrayGetExpression
import org.usvm.test.api.UTestArrayLengthExpression
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestBinaryConditionExpression
import org.usvm.test.api.UTestBinaryConditionStatement
import org.usvm.test.api.UTestBooleanExpression
import org.usvm.test.api.UTestByteExpression
import org.usvm.test.api.UTestCastExpression
import org.usvm.test.api.UTestCharExpression
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestDoubleExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestFloatExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestGetStaticFieldExpression
import org.usvm.test.api.UTestGlobalMock
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestLongExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.UTestNullExpression
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestSetStaticFieldStatement
import org.usvm.test.api.UTestShortExpression
import org.usvm.test.api.UTestStatement
import org.usvm.test.api.UTestStaticMethodCall
import org.usvm.test.api.UTestStringExpression


class JcTestMethodBodyRenderer(
    private val importManager: JcImportManager,
    private val typeTranslator: JcTypeTranslator
) {
    val throwPool = mutableSetOf<ClassOrInterfaceType>()
    var needUnsafe = false

    // TODO maybe create renderer for every utest
    lateinit var varManager: JcTestVarManager

    fun render(test: UTest): BlockStmt {
        varManager = JcTestVarManager(test)
        val initStmts: List<Statement> = test.initStatements.fold(listOf()) { acc, it ->
            acc + varManager.requiredDeclarationsBefore(it).map { it.toDeclarationStatement() } + when (it) {
                is UTestStatement -> it.toStatement()
                is UTestExpression -> ExpressionStmt(it.toExpression())
            }
        }

        val observedCall = ExpressionStmt(test.callMethodExpression.toExpression())

        return BlockStmt(
            NodeList(
                initStmts + varManager.requiredDeclarationsBefore(test.callMethodExpression)
                    .map { it.toDeclarationStatement() } + observedCall))
    }

    private fun UTestExpression.toExpression(allowVarSubst: Boolean = true): Expression {
        if (allowVarSubst) {
            val subst = varManager.asVar(this)
            if (subst != null) return NameExpr(subst.name)
        }
        return when (this) {
            is UTestArithmeticExpression -> BinaryExpr(
                this.lhv.toExpression(), this.rhv.toExpression(), when (this.operationType) {
                    ArithmeticOperationType.AND -> BinaryExpr.Operator.AND
                    ArithmeticOperationType.PLUS -> BinaryExpr.Operator.PLUS
                    ArithmeticOperationType.SUB -> BinaryExpr.Operator.MINUS
                    ArithmeticOperationType.MUL -> BinaryExpr.Operator.MULTIPLY
                    ArithmeticOperationType.DIV -> BinaryExpr.Operator.DIVIDE
                    ArithmeticOperationType.REM -> BinaryExpr.Operator.REMAINDER
                    ArithmeticOperationType.EQ -> BinaryExpr.Operator.EQUALS
                    ArithmeticOperationType.NEQ -> BinaryExpr.Operator.NOT_EQUALS
                    ArithmeticOperationType.GT -> BinaryExpr.Operator.GREATER
                    ArithmeticOperationType.GEQ -> BinaryExpr.Operator.GREATER_EQUALS
                    ArithmeticOperationType.LT -> BinaryExpr.Operator.LESS
                    ArithmeticOperationType.LEQ -> BinaryExpr.Operator.LESS_EQUALS
                    ArithmeticOperationType.OR -> BinaryExpr.Operator.OR
                    ArithmeticOperationType.XOR -> BinaryExpr.Operator.XOR
                }
            )

            is UTestArrayGetExpression -> ArrayAccessExpr(this.arrayInstance.toExpression(), this.index.toExpression())
            is UTestBinaryConditionExpression -> ConditionalExpr(
                BinaryExpr(
                    this.lhv.toExpression(), this.rhv.toExpression(), translateConditionType(this.conditionType)
                ),
                this.trueBranch.toExpression(),
                this.elseBranch.toExpression()
            )


            is UTestConstructorCall -> ObjectCreationExpr(
                null,
                typeTranslator.typeReprOf(
                    this.type
                ) as ClassOrInterfaceType,
                NodeList(this.args.map { it.toExpression() })
            )

            is UTestMethodCall -> MethodCallExpr(
                this.instance.toExpression(),
                this.method.name,
                NodeList(this.args.map { it.toExpression() })
            ).also {
                this.method.exceptions.forEach { exc ->
                    importManager.tryAdd(exc.typeName)
                    StaticJavaParser.parseClassOrInterfaceType(exc.typeName)
                }
            }

            is UTestStaticMethodCall -> MethodCallExpr(
                TypeExpr(typeTranslator.typeReprOf(this.method.enclosingClass.toType())),
                this.method.name,
                NodeList(this.args.map { it.toExpression() })
            ).also {
                this.method.exceptions.forEach { exc ->
                    importManager.tryAdd(exc.typeName)
                    throwPool.add(StaticJavaParser.parseClassOrInterfaceType(exc.typeName))
                }
            }

            is UTestAllocateMemoryCall -> EnclosedExpr(
                CastExpr(
                    typeTranslator.typeReprOf(this.clazz.toType()), MethodCallExpr(
                        NameExpr("UNSAFE"),
                        "allocateInstance",
                        NodeList(listOf(ClassExpr(typeTranslator.typeReprOf(this.clazz.toType(),false))))
                    )
                ).also {
                    needUnsafe = true
                    importManager.tryAdd("sun.misc.Unsafe", "Unsafe")
                    importManager.tryAdd("java.lang.InstantiationException", "InstantiationException")
                    throwPool.add(ClassOrInterfaceType(null, "java.lang.InstantiationException"))
                }
            )

            is UTestCastExpression -> CastExpr(
                typeTranslator.typeReprOf(this.type),
                this.expr.toExpression()
            )

            is UTestClassExpression -> ClassExpr(typeTranslator.typeReprOf(this.type, false))

            is UTestBooleanExpression -> BooleanLiteralExpr(this.value)
            is UTestByteExpression -> IntegerLiteralExpr(this.value.toString())
            is UTestCharExpression -> CharLiteralExpr(this.value)
            is UTestDoubleExpression -> DoubleLiteralExpr(this.value)
            is UTestFloatExpression -> DoubleLiteralExpr(this.value.toDouble())
            is UTestIntExpression -> IntegerLiteralExpr(this.value.toString())
            is UTestLongExpression -> LongLiteralExpr(this.value.toString())
            is UTestNullExpression -> NullLiteralExpr()
            is UTestShortExpression -> IntegerLiteralExpr(this.value.toString())
            is UTestStringExpression -> StringLiteralExpr(this.value)
            is UTestCreateArrayExpression -> ArrayCreationExpr(typeTranslator.typeReprOf(this.elementType))
            is UTestGetFieldExpression -> FieldAccessExpr(
                this.instance.toExpression(),
                this.field.name
            ).also { if (!field.isAccessible()) println("inaccessible field get") } // todo

            is UTestGetStaticFieldExpression -> FieldAccessExpr(
                TypeExpr(typeTranslator.typeReprOf(this.field.enclosingClass.toType())),
                this.field.name
            ).also { if (!field.isAccessible()) println("inaccessible field get") } // todo

            is UTestArrayLengthExpression -> NameExpr("arrLen")
            is UTestGlobalMock -> NameExpr("mockGlobal")
            is UTestMockObject -> NameExpr("mockObj")
        }
    }

    private fun UTestStatement.toStatement(): Statement =
        when (this) {
            is UTestArraySetStatement -> ExpressionStmt(
                AssignExpr(
                    ArrayAccessExpr(this.arrayInstance.toExpression(), this.index.toExpression()),
                    this.setValueExpression.toExpression(),
                    AssignExpr.Operator.ASSIGN
                )
            )

            is UTestSetFieldStatement -> ExpressionStmt(
                AssignExpr(
                    FieldAccessExpr(this.instance.toExpression(), this.field.name),
                    this.value.toExpression(),
                    AssignExpr.Operator.ASSIGN
                )
            )

            is UTestSetStaticFieldStatement -> ExpressionStmt(
                AssignExpr(
                    FieldAccessExpr(
                        TypeExpr(typeTranslator.typeReprOf(this.field.enclosingClass.toType())),
                        this.field.name
                    ),
                    this.value.toExpression(),
                    AssignExpr.Operator.ASSIGN
                )
            )

            is UTestBinaryConditionStatement -> IfStmt(
                BinaryExpr(lhv.toExpression(), rhv.toExpression(), translateConditionType(this.conditionType)),
                BlockStmt(NodeList(trueBranch.map { it.toStatement() })),
                BlockStmt(NodeList(elseBranch.map { it.toStatement() }))
            )
        }

    private fun JcTestVarAssignStmt.toDeclarationStatement(): Statement = ExpressionStmt(
        VariableDeclarationExpr(VariableDeclarator().apply {
            type = typeTranslator.typeReprOf(lhs.type)
            name = SimpleName(lhs.name)
            setInitializer(rhs.toExpression(false))

        })
    )

    private companion object Utils {
        fun translateConditionType(ct: ConditionType) = when (ct) {
            ConditionType.EQ -> BinaryExpr.Operator.EQUALS
            ConditionType.NEQ -> BinaryExpr.Operator.NOT_EQUALS
            ConditionType.GEQ -> BinaryExpr.Operator.GREATER_EQUALS
            ConditionType.GT -> BinaryExpr.Operator.GREATER
        }

        fun JcField.isAccessible() = this.isPublic && !this.isFinal

        fun getArrayElemType(type: JcType): JcType = when (type) {
            is JcArrayType -> getArrayElemType(type.elementType)
            else -> type
        }
    }
}
