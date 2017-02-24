package com.bringholm.nametagchanger;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;

import java.lang.reflect.*;

/**
 * A utility class for performing various reflection operations.
 * @author AlvinB
 *
 * Inspired by I Al Istannen's ReflectionUtil:
 * https://github.com/PerceiveDev/PerceiveCore/blob/master/Reflection/src/main/java/com/perceivedev/perceivecore/reflection/ReflectionUtil.java
 */

@SuppressWarnings({"SameParameterValue", "WeakerAccess", "unused"})
public class ReflectUtil {
    public static final String NMS_PACKAGE = "net.minecraft.server" + Bukkit.getServer().getClass().getPackage().getName().substring(Bukkit.getServer().getClass().getPackage().getName().lastIndexOf("."));
    public static final String CB_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();

    private static final Field MODIFIERS_FIELD = getDeclaredField(Field.class, "modifiers", true).getOrThrow();

    public static ReflectionResponse<Class<?>> getNMSClass(String clazz) {
        Validate.notNull(clazz, "clazz cannot be null");
        return getClass(NMS_PACKAGE + "." + clazz);
    }

    public static ReflectionResponse<Class<?>> getCBClass(String clazz) {
        Validate.notNull(clazz, "clazz cannot be null");
        return getClass(CB_PACKAGE + "." + clazz);
    }

    public static ReflectionResponse<Class<?>> getClass(String clazz) {
        Validate.notNull(clazz, "clazz cannot be null");
        try {
            return new ReflectionResponse<>(Class.forName(clazz));
        } catch (ClassNotFoundException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Constructor<?>> getConstructor(Class<?> clazz, Class<?>... params) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(params, "params cannot be null");
        try {
            return new ReflectionResponse<>(clazz.getConstructor(params));
        } catch (NoSuchMethodException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Field> getField(Class<?> clazz, String fieldName) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(fieldName, "fieldName cannot be null");
        try {
            return new ReflectionResponse<>(clazz.getField(fieldName));
        } catch (NoSuchFieldException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Field> getDeclaredField(Class<?> clazz, String fieldName) {
        return getDeclaredField(clazz, fieldName, false);
    }

    public static ReflectionResponse<Field> getDeclaredField(Class<?> clazz, String fieldName, boolean setAccessible) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(fieldName, "fieldName cannot be null");
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(setAccessible);
            return new ReflectionResponse<>(field);
        } catch (NoSuchFieldException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Field> getFieldByType(Class<?> clazz, Class<?> type, int index) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(type, "type cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Field field : clazz.getFields()) {
            if (field.getType() == type) {
                if (curIndex == index) {
                    return new ReflectionResponse<>(field);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchFieldException("No field with type " + type + " and index" + index + " in " + clazz));
    }

    public static ReflectionResponse<Field> getModifiableFinalStaticField(Class<?> clazz, String fieldName) {
        ReflectionResponse<Field> response = getField(clazz, fieldName);
        if (!response.hasResult()) {
            return response;
        }
        Field field = response.getValue();
        ReflectionResponse<Void> voidResponse = makeFinalStaticFieldModifiable(field);
        if (!voidResponse.hasResult()) {
            return new ReflectionResponse<>(voidResponse.getException());
        }
        return new ReflectionResponse<>(field);
    }

    public static ReflectionResponse<Field> getModifiableDeclaredFinalStaticField(Class<?> clazz, String fieldName, boolean setAccessible) {
        ReflectionResponse<Field> response = getDeclaredField(clazz, fieldName, setAccessible);
        if (!response.hasResult()) {
            return response;
        }
        Field field = response.getValue();
        ReflectionResponse<Void> voidResponse = makeFinalStaticFieldModifiable(field);
        if (!voidResponse.hasResult()) {
            return new ReflectionResponse<>(voidResponse.getException());
        }
        return new ReflectionResponse<>(field);
    }

    public static ReflectionResponse<Void> makeFinalStaticFieldModifiable(Field field) {
        Validate.notNull(field, "field cannot be null");
        Validate.isTrue(Modifier.isStatic(field.getModifiers()), "field is not static");
        Validate.isTrue(Modifier.isFinal(field.getModifiers()), "field is not final");
        return setFieldValue(field, MODIFIERS_FIELD, field.getModifiers() &~ Modifier.FINAL);
    }

    public static ReflectionResponse<Field> getDeclaredFieldByType(Class<?> clazz, Class<?> type, int index) {
        return getDeclaredFieldByType(clazz, type, index, false);
    }

    public static ReflectionResponse<Field> getDeclaredFieldByType(Class<?> clazz, Class<?> type, int index, boolean setAccessible) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(type, "type cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType() == type) {
                if (curIndex == index) {
                    field.setAccessible(setAccessible);
                    return new ReflectionResponse<>(field);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchFieldException("No declared field with type " + type + " and index " + index + " in " + clazz));
    }

    public static ReflectionResponse<Method> getMethod(Class<?> clazz, String methodName, Class<?>... params) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(methodName, "methodName cannot be null");
        Validate.notNull(params, "params cannot be null");
        try {
            return new ReflectionResponse<>(clazz.getMethod(methodName, params));
        } catch (NoSuchMethodException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Method> getMethodByType(Class<?> clazz, Class<?> type, int index) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(type, "type cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Method method : clazz.getMethods()) {
            if (method.getReturnType() == type) {
                if (curIndex == index) {
                    return new ReflectionResponse<>(method);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchMethodException("No method with type " + type + " and index " + index + " in " + clazz));
    }

    public static ReflectionResponse<Method> getDeclaredMethodByType(Class<?> clazz, Class<?> type, int index) {
        return getDeclaredMethodByType(clazz, type, index, false);
    }

    public static ReflectionResponse<Method> getDeclaredMethodByType(Class<?> clazz, Class<?> type, int index, boolean setAccessible) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(type, "type cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getReturnType() == type) {
                if (curIndex == index) {
                    method.setAccessible(setAccessible);
                    return new ReflectionResponse<>(method);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchMethodException("No method with type " + type + " and index " + index + " in " + clazz));
    }

    public static ReflectionResponse<Object> getFieldValue(Object object, Field field) {
        Validate.notNull(field, "field cannot be null");
        Validate.isTrue(object != null || Modifier.isStatic(field.getModifiers()), "object cannot be null");
        try {
            return new ReflectionResponse<>(field.get(object));
        } catch (IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Object> getEnumConstant(Class<?> clazz, String constant) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.isTrue(clazz.isEnum(), "clazz is not an Enum");
        Validate.notNull(constant, "constant cannot be null");
        try {
            Field field = clazz.getField(constant);
            return new ReflectionResponse<>(field.get(null));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Void> setFieldValue(Object object, Field field, Object newValue) {
        Validate.notNull(field, "field cannot be null");
        Validate.isTrue(object != null  || Modifier.isStatic(field.getModifiers()));
        try {
            field.set(object, newValue);
            return new ReflectionResponse<>((Void) null);
        } catch (IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Object> invokeMethod(Object object, Method method, Object... params) {
        Validate.notNull(method, "method cannot be null");
        Validate.isTrue(object != null || Modifier.isStatic(method.getModifiers()));
        Validate.notNull(params, "params cannot be null");
        try {
            return new ReflectionResponse<>(method.invoke(object, params));
        } catch (IllegalAccessException | InvocationTargetException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Object> invokeConstructor(Constructor<?> constructor, Object... params) {
        Validate.notNull(constructor, "constructor cannot be null");
        Validate.notNull(params, "params cannot be null");
        try {
            return new ReflectionResponse<>(constructor.newInstance(params));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            return new ReflectionResponse<>(e);
        }
    }

    @SuppressWarnings("unused")
    public static class ReflectionResponse<T> {
        private final T value;
        private final Exception exception;
        private final boolean hasResult;

        private ReflectionResponse(T value, boolean hasResult, Exception exception) {
            this.value = value;
            this.hasResult = hasResult;
            this.exception = exception;
        }

        private ReflectionResponse(T value) {
            this(value, true, null);
        }

        private ReflectionResponse(Exception exception) {
            this(null, false, exception);
        }

        public T getValue() {
            return value;
        }

        public boolean hasResult() {
            return hasResult;
        }

        public Exception getException() {
            return exception;
        }

        public T getOrThrow() {
            if (hasResult) {
                return value;
            } else {
                throw new RuntimeException(exception);
            }
        }
    }
}

