package org.usvm.spring.query.expression;

import org.usvm.spring.query.expression.functions.BinOperator;
import org.usvm.spring.query.expression.functions.FromDuration;
import org.usvm.spring.query.expression.functions.Id;
import org.usvm.spring.query.expression.functions.Minus;
import org.usvm.spring.query.expression.functions.NaturalId;
import org.usvm.spring.query.expression.functions.ToDuration;
import org.usvm.spring.query.expression.functions.TypeOfParameter;
import org.usvm.spring.query.expression.functions.TypeOfPath;
import org.usvm.spring.query.expression.functions.Version;
import org.usvm.spring.query.expression.literals.LBigDecimal;
import org.usvm.spring.query.expression.literals.LBigInt;
import org.usvm.spring.query.expression.literals.LBinary;
import org.usvm.spring.query.expression.literals.LBool;
import org.usvm.spring.query.expression.literals.LDouble;
import org.usvm.spring.query.expression.literals.LFloat;
import org.usvm.spring.query.expression.literals.LInt;
import org.usvm.spring.query.expression.literals.LLong;
import org.usvm.spring.query.expression.literals.LNull;
import org.usvm.spring.query.expression.literals.LString;
import org.usvm.spring.query.expression.literals.LTime;
import org.usvm.spring.query.expression.paths.ExprPath;
import org.usvm.spring.query.expression.paths.SyntacticPath;
import org.usvm.spring.query.expression.special.Parameter;
import org.usvm.spring.query.expression.special.Subquery;
import org.usvm.spring.query.expression.special.Tuple;
import org.usvm.spring.query.expression.special.cases.CaseList;
import org.usvm.spring.query.expression.special.cases.SimpleCaseList;
import org.usvm.spring.query.function.Instance;
import org.usvm.spring.query.function.SqlFunction;
import org.usvm.spring.query.parameter.Colon;
import org.usvm.spring.query.parameter.Positional;
import org.usvm.spring.query.predicate.And;
import org.usvm.spring.query.predicate.Not;
import org.usvm.spring.query.predicate.Or;
import org.usvm.spring.query.predicate.functions.Between;
import org.usvm.spring.query.predicate.functions.Exist;
import org.usvm.spring.query.predicate.functions.InFunction.InFunction;
import org.usvm.spring.query.predicate.functions.InFunction.ListIn;
import org.usvm.spring.query.predicate.functions.InFunction.ParameterIn;
import org.usvm.spring.query.predicate.functions.IsDistinct;
import org.usvm.spring.query.predicate.functions.IsEmpty;
import org.usvm.spring.query.predicate.functions.IsNull;
import org.usvm.spring.query.predicate.functions.IsTrue;
import org.usvm.spring.query.predicate.functions.Like;
import org.usvm.spring.query.predicate.functions.Member;
import org.usvm.spring.query.predicate.functions.compare.Compare;
import org.usvm.spring.query.predicate.functions.existcollection.ExistCollection;

public interface IExpressionVisitor<R, C> {

    R visit(BinOperator child, C ctx);
    R visit(FromDuration child, C ctx);
    R visit(Id child, C ctx);
    R visit(Minus child, C ctx);
    R visit(NaturalId child, C ctx);
    R visit(ToDuration child, C ctx);
    R visit(TypeOfParameter child, C ctx);
    R visit(TypeOfPath child, C ctx);
    R visit(Version child, C ctx);

    R visit(LBigDecimal child, C ctx);
    R visit(LBigInt child, C ctx);
    R visit(LBinary child, C ctx);
    R visit(LBool child, C ctx);
    R visit(LDouble child, C ctx);
    R visit(LFloat child, C ctx);
    R visit(LInt child, C ctx);
    R visit(LLong child, C ctx);
    R visit(LNull child, C ctx);
    R visit(LString child, C ctx);
    R visit(LTime child, C ctx);

    R visit(ExprPath child, C ctx);
    R visit(SyntacticPath child, C ctx);

    R visit(CaseList child, C ctx);
    R visit(SimpleCaseList child, C ctx);
    R visit(Parameter child, C ctx);
    R visit(Subquery child, C ctx);
    R visit(Tuple child, C ctx);

    R visit(Instance child, C ctx);
    R visit(SqlFunction child, C ctx);

    R visit(Positional child, C ctx);
    R visit(Colon child, C ctx);

    R visit(Compare child, C ctx);
    R visit(ExistCollection child, C ctx);
    R visit(InFunction child, C ctx);
    R visit(Between child, C ctx);
    R visit(Exist child, C ctx);
    R visit(IsDistinct child, C ctx);
    R visit(IsEmpty child, C ctx);
    R visit(IsNull child, C ctx);
    R visit(IsTrue child, C ctx);
    R visit(Like child, C ctx);
    R visit(Member child, C ctx);
    R visit(And child, C ctx);
    R visit(Not child, C ctx);
    R visit(Or child, C ctx);

    R visit(ListIn child, C ctx);
    R visit(ParameterIn child, C ctx);
}
