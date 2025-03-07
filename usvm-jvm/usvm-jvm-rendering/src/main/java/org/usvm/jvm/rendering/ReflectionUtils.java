package org.usvm.jvm.rendering;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ReflectionUtils {
    private static final Unsafe UNSAFE;

    static {
        try {
            Field uns = Unsafe.class.getDeclaredField("theUnsafe");
            uns.setAccessible(true);
            UNSAFE = (Unsafe) uns.get(null);
        } catch (Throwable e) {
            throw new RuntimeException();
        }
    }

    private static Field getField(Class<?> type, String fieldName) {
        Class<?> currentClass = type;
        while (currentClass != Object.class && currentClass != null) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().equals(fieldName))
                    return field;
            }
            currentClass = currentClass.getSuperclass();
        }

        throw new IllegalArgumentException("Could not find field " + fieldName + " in " + type);
    }

    public static Object getFieldValue(Object instance, String fieldName) throws NoSuchFieldException {
        Field field = getField(instance.getClass(), fieldName);
        Object fixedInstance = getInstanceOf(field, instance);
        long fieldOffset = getOffsetOf(field);

        if (!field.getType().isPrimitive()) {
            return UNSAFE.getObject(fixedInstance, fieldOffset);
        }

        if (field.getType() == Boolean.class) {
            return UNSAFE.getBoolean(fixedInstance, fieldOffset);
        } else if (field.getType() == Byte.class) {
            return UNSAFE.getByte(fixedInstance, fieldOffset);
        } else if (field.getType() == Character.class) {
            return UNSAFE.getChar(fixedInstance, fieldOffset);
        } else if (field.getType() == Short.class) {
            return UNSAFE.getShort(fixedInstance, fieldOffset);
        } else if (field.getType() == Integer.class) {
            return UNSAFE.getInt(fixedInstance, fieldOffset);
        } else if (field.getType() == Long.class) {
            return UNSAFE.getLong(fixedInstance, fieldOffset);
        } else if (field.getType() == Float.class) {
            return UNSAFE.getFloat(fixedInstance, fieldOffset);
        } else if (field.getType() == Double.class) {
            return UNSAFE.getDouble(fixedInstance, fieldOffset);
        }

        throw new IllegalStateException("unexpected primitive type");
    }

    public static void setFieldValue(Object instance, String fieldName, Object value) throws NoSuchFieldException {
        Field field = getField(instance.getClass(), fieldName);
        Object fixedInstance = getInstanceOf(field, instance);
        long fieldOffset = getOffsetOf(field);

        if (!field.getType().isPrimitive()) {
            UNSAFE.putObject(fixedInstance, fieldOffset, value);
            return;
        }

        if (field.getType() == Boolean.class) {
            UNSAFE.putBoolean(fixedInstance, fieldOffset, value != null ? (Boolean) value : false);
        } else if (field.getType() == Byte.class) {
            UNSAFE.putByte(fixedInstance, fieldOffset, value != null ? (Byte) value : 0);
        } else if (field.getType() == Character.class) {
            UNSAFE.putChar(fixedInstance, fieldOffset, value != null ? (Character) value : '\u0000');
        } else if (field.getType() == Short.class) {
            UNSAFE.putShort(fixedInstance, fieldOffset, value != null ? (Short) value : 0);
        } else if (field.getType() == Integer.class) {
            UNSAFE.putInt(fixedInstance, fieldOffset, value != null ? (Integer) value : 0);
        } else if (field.getType() == Long.class) {
            UNSAFE.putLong(fixedInstance, fieldOffset, value != null ? (Long) value : 0);
        } else if (field.getType() == Float.class) {
            UNSAFE.putFloat(fixedInstance, fieldOffset, value != null ? (Float) value : 0.0f);
        } else if (field.getType() == Double.class) {
            UNSAFE.putDouble(fixedInstance, fieldOffset, value != null ? (Double) value : 0.0);
        }
    }

    public static Object allocateInstance(Class<?> clazz) throws InstantiationException {
        return UNSAFE.allocateInstance(clazz);
    }

    private static Object getInstanceOf(Field field, Object instance) {
        return isStatic(field) ? UNSAFE.staticFieldBase(field) : instance;
    }

    private static long getOffsetOf(Field field) {
        return isStatic(field) ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
    }

    private static boolean isStatic(Field field) {
        return (field.getModifiers() & Modifier.STATIC) > 0;
    }
}
