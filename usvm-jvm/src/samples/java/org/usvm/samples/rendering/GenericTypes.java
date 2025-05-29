package org.usvm.samples.rendering;

import java.util.HashSet;
import java.util.Set;
@SuppressWarnings("ALL")
class AttributeType<OwningType extends Customizable> { }
@SuppressWarnings("ALL")
class AttributeTypeImpl<T extends Customizable> extends AttributeType<T> { }
@SuppressWarnings("ALL")
class Customizable<A extends Attribute> { }
@SuppressWarnings("ALL")
class CustomizableImpl<A extends Attribute> extends Customizable<A> { }
@SuppressWarnings("ALL")
class Attribute<AT extends AttributeType, OT extends Customizable<?>> { }

@SuppressWarnings("ALL")
class AttrImpl<AT extends AttributeType, OT extends Customizable<?>> extends Attribute<AT, OT> { }
@SuppressWarnings("ALL")
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
