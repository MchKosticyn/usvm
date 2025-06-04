package org.usvm.samples.rendering;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("rawtypes")
class AttributeType<OwningType extends Customizable> { }
@SuppressWarnings("rawtypes")
class AttributeTypeImpl<T extends Customizable> extends AttributeType<T> { }
@SuppressWarnings("rawtypes")
class Customizable<A extends Attribute> { }
@SuppressWarnings("rawtypes")
class CustomizableImpl<A extends Attribute> extends Customizable<A> { }
@SuppressWarnings("rawtypes")
class Attribute<AT extends AttributeType, OT extends Customizable<?>> { }

@SuppressWarnings("rawtypes")
class AttrImpl<AT extends AttributeType, OT extends Customizable<?>> extends Attribute<AT, OT> { }
@SuppressWarnings({"rawtypes", "ClassEscapesDefinedScope", "ConstantValue"})
public class GenericTypes {

    public static int unboundedWildcardInUsage(Attribute<AttributeTypeImpl<Customizable>, CustomizableImpl<Attribute<?,?>>> a) {
        if (a == null) return 1;
        return -1;
    }
    public static int wildcardsMisc(HashSet<? extends Comparable<?>> s) {
        if (s.isEmpty()) throw new IllegalStateException();
        if (s == null) throw new IllegalArgumentException();
        return s.size();
    }
    public static int noGenericArgument(AttributeTypeImpl<CustomizableImpl> a) {
        if (a == null) return 1;
        return -1;
    }

    public static int noGenericArgumentInner(HashSet<? extends Set> s) {
        return s.size();
    }

    public static <T> T methodWithGenericParameterAndReturnType(T t) {
        if (t == null) throw new IllegalStateException();
        return t;
    }

    public static int notParametrizedCollection(HashSet h) {
        if (h.isEmpty()) throw new IllegalStateException();
        return h.size();
    }
}
