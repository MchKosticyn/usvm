package org.usvm.spring.query.type;

import org.usvm.spring.query.type.primitive.TBigDecimal;
import org.usvm.spring.query.type.primitive.TBigInt;
import org.usvm.spring.query.type.primitive.TBinary;
import org.usvm.spring.query.type.primitive.TBool;
import org.usvm.spring.query.type.primitive.TDouble;
import org.usvm.spring.query.type.primitive.TFloat;
import org.usvm.spring.query.type.primitive.TInt;
import org.usvm.spring.query.type.primitive.TList;
import org.usvm.spring.query.type.primitive.TLocalDate;
import org.usvm.spring.query.type.primitive.TLong;
import org.usvm.spring.query.type.primitive.TString;

public interface ITypeVisitor<R, C> {

    R visit(TBigDecimal child, C ctx);
    R visit(TBigInt child, C ctx);
    R visit(TBinary child, C ctx);
    R visit(TBool child, C ctx);
    R visit(TDouble child, C ctx);
    R visit(TFloat child, C ctx);
    R visit(TInt child, C ctx);
    R visit(TList child, C ctx);
    R visit(TLocalDate child, C ctx);
    R visit(TLong child, C ctx);
    R visit(TString child, C ctx);
    R visit(TNull child, C ctx);
    R visit(TParam child, C ctx);
    R visit(TPath child, C ctx);
    R visit(TTuple child, C ctx);
}
