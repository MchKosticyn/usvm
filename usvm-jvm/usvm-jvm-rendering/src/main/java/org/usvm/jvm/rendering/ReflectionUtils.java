package org.usvm.jvm.rendering;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

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

    private static List<Field> getInstanceFields(Class<?> type) {
        ArrayList<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()))
                fields.add(field);
        }

        return fields;
    }

    private static List<Field> getStaticFields(Class<?> type) {
        ArrayList<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()))
                fields.add(field);
        }

        return fields;
    }

    private static Field getField(Object instance, String fieldName) {
        Class<?> type = instance.getClass();
        Class<?> currentClass = type;
        while (currentClass != Object.class && currentClass != null) {
            for (Field field : getInstanceFields(currentClass)) {
                if (field.getName().equals(fieldName))
                    return field;
            }
            currentClass = currentClass.getSuperclass();
        }

        throw new IllegalArgumentException("Could not find field " + fieldName + " in " + type);
    }

    private static Field getStaticField(Class<?> type, String fieldName) {
        for (Field field : getStaticFields(type)) {
            if (field.getName().equals(fieldName))
                return field;
        }

        throw new IllegalArgumentException("Could not find static field " + fieldName + " in " + type);
    }

    private static Object getFieldValue(Object fixedInstance, Field field) {
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

    public static Object getFieldValue(Object instance, String fieldName) {
        Field field = getField(instance, fieldName);
        return getFieldValue(instance, field);
    }

    public static Object getStaticFieldValue(Class<?> type, String fieldName) {
        Field field = getStaticField(type, fieldName);
        return getFieldValue(UNSAFE.staticFieldBase(field), field);
    }

    private static void setFieldValue(Object fixedInstance, Field field, Object value) {
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

    public static void setFieldValue(Object instance, String fieldName, Object value) {
        Field field = getField(instance, fieldName);
        setFieldValue(instance, field, value);
    }

    public static void setStaticFieldValue(Class<?> type, String fieldName, Object value) {
        Field field = getStaticField(type, fieldName);
        setFieldValue(UNSAFE.staticFieldBase(field), field, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T allocateInstance(Class<T> clazz) throws InstantiationException {
        return (T) UNSAFE.allocateInstance(clazz);
    }

    private static long getOffsetOf(Field field) {
        return isStatic(field) ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
    }

    private static boolean isStatic(Field field) {
        return (field.getModifiers() & Modifier.STATIC) > 0;
    }
}
