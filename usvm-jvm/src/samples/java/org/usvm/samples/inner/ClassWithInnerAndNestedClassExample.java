package org.usvm.samples.inner;

public class ClassWithInnerAndNestedClassExample {
    int x;

    public static class InnerClassExample {
        int y;
        public class InnerInner {
            public InnerInner() {}
        }
        public InnerClassExample() {
            y = /*x +*/ 42;
        }

        int accessOuterClassField() {
            return y;//x;
        }
    }

    public static class NestedClassExample {
        int accessOuterClassFieldWithParameter(ClassWithInnerAndNestedClassExample c) {
            return c.x;
        }

        static int staticAccessOuterClassFieldWithParameter(ClassWithInnerAndNestedClassExample c) {
            return c.x;
        }

//        int createInnerClassOutside() {
//            ClassWithInnerAndNestedClassExample c = new ClassWithInnerAndNestedClassExample();
//            InnerClassExample inner = c.new InnerClassExample();
//
//            return inner.accessOuterClassField();
//        }

        int useInnerClassAsParameter(InnerClassExample e) {
            return e.accessOuterClassField();
        }

//        int useInheritorAndInnerClass() {
//            Inheritor inheritor = new Inheritor();
//            return (inheritor.new InnerClassExample()).accessOuterClassField();
//        }
    }
}

class Inheritor extends ClassWithInnerAndNestedClassExample {
}
