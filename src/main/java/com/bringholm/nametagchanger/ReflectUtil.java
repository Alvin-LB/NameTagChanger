package com.bringholm.nametagchanger;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.ClassUtils;
import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A utility class for performing various reflection operations.
 *
 * Partly inspired by I Al Istannen's ReflectionUtil:
 * https://github.com/PerceiveDev/PerceiveCore/blob/master/Reflection/src/main/java/com/perceivedev/perceivecore/reflection/ReflectionUtil.java
 *
 * @author AlvinB
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
        return setFieldValue(field, MODIFIERS_FIELD, field.getModifiers() & ~Modifier.FINAL);
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
        return getMethodByPredicate(clazz, new MethodPredicate().withReturnType(type), index);
    }

    public static ReflectionResponse<Method> getMethodByParams(Class<?> clazz, int index, Class<?>... params) {
        return getMethodByPredicate(clazz, new MethodPredicate().withParams(params), index);
    }

    public static ReflectionResponse<Method> getMethodByTypeAndParams(Class<?> clazz, Class<?> type, int index, Class<?>... params) {
        return getMethodByPredicate(clazz, new MethodPredicate().withReturnType(type).withParams(params), index);
    }

    public static ReflectionResponse<Method> getMethodByPredicate(Class<?> clazz, Predicate<Method> predicate, int index) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Method method : clazz.getMethods()) {
            if (predicate == null || predicate.test(method)) {
                if (curIndex == index) {
                    return new ReflectionResponse<>(method);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchMethodException("No method matching " + (predicate instanceof MethodPredicate ? predicate : "specified predicate") + " in " + clazz));
    }

    public static ReflectionResponse<Method> getDeclaredMethodByType(Class<?> clazz, Class<?> type, int index) {
        return getDeclaredMethodByType(clazz, type, index, false);
    }

    public static ReflectionResponse<Method> getDeclaredMethodByType(Class<?> clazz, Class<?> type, int index, boolean setAccessible) {
        return getDeclaredMethodByPredicate(clazz, new MethodPredicate().withReturnType(type), 0, setAccessible);
    }

    public static ReflectionResponse<Method> getDeclaredMethodByPredicate(Class<?> clazz, Predicate<Method> predicate, int index, boolean setAccessible) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (predicate == null || predicate.test(method)) {
                if (curIndex == index) {
                    method.setAccessible(setAccessible);
                    return new ReflectionResponse<>(method);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchMethodException("No method matching " + (predicate instanceof MethodPredicate ? predicate : "specified predicate") + " in " + clazz));
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
        Validate.isTrue(object != null || Modifier.isStatic(field.getModifiers()));
        try {
            field.set(object, newValue);
            return new ReflectionResponse<>((Void) null);
        } catch (IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
    }

    public static ReflectionResponse<Object> invokeMethod(Object object, Method method, Object... params) {
        Validate.notNull(method, "method cannot be null");
        Validate.isTrue(object != null || Modifier.isStatic(method.getModifiers()), "object cannot be null");
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

    public static ReflectionResponse<Map<String, String>> getPrintableFields(Object object, Class<?>... toStringExceptions) {
        return getPrintableFields(object, true, toStringExceptions);
    }

    public static ReflectionResponse<Map<String, String>> getPrintableFields(Object object, boolean useToString, Class<?>... toStringExceptions) {
        Validate.notNull(object, "object cannot be null");
        return getPrintableFields(object, object.getClass(), useToString, toStringExceptions);
    }

    public static ReflectionResponse<Map<String, String>> getPrintableFields(Object object, Class<?> clazz, boolean useToString, Class<?>... toStringExceptions) {
        Validate.notNull(clazz, "clazz cannot be null");
        Map<String, String> fields = Maps.newHashMap();
        try {
            for (Field field : clazz.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    ReflectionResponse<String> response = getStringRepresentation(field.get(object), useToString, toStringExceptions);
                    if (!response.hasResult()) {
                        return new ReflectionResponse<>(response.getException());
                    }
                    fields.put(field.getName(), response.getValue());
                }
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    if (clazz.getEnclosingClass() != null && field.getType() == clazz.getEnclosingClass()) {
                        /* Inner classes contain a reference to their outer class instance and not ignoring it would
                           cause the code to recurse infinitely and cause a StackOverflowError.
                           This field is normally named 'this$0' (or with added $'s if a field with that name already exists),
                           but Mojang's obfuscation tool obfuscates this field and renames it 'a'. */
                        if (field.getName().startsWith("this$0") || (clazz.getPackage().getName().equals(NMS_PACKAGE) && field.getName().equals("a"))) {
                            continue;
                        }
                    }
                    field.setAccessible(true);
                    ReflectionResponse<String> response = getStringRepresentation(field.get(object), useToString, toStringExceptions);
                    if (!response.hasResult()) {
                        return new ReflectionResponse<>(response.getException());
                    }
                    fields.put(field.getName(), response.getValue());
                }
            }
        } catch (IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
        return new ReflectionResponse<>(fields);
    }

    public static ReflectionResponse<String> getStringRepresentation(Object object, boolean useToString, Class<?>... toStringExceptions) {
        try {
            if (object == null) {
                return new ReflectionResponse<>("null");
            }
            // Multimaps don't extend Map apparently.
            if (object instanceof Multimap) {
                object = ((Multimap) object).asMap();
            }
            // Using toString() (or Arrays.toString) on Collections/Maps/arrays would use
            // toString() on the contained Objects, which is why we make our own implementation.
            if (object instanceof Map) {
                String str = "{";
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    ReflectionResponse<String> firstResponse = getStringRepresentation(entry.getKey(), useToString, toStringExceptions);
                    ReflectionResponse<String> secondResponse = getStringRepresentation(entry.getValue(), useToString, toStringExceptions);
                    if (!firstResponse.hasResult()) {
                        // getStringRepresentation caught an Exception, so we abort.
                        return firstResponse;
                    }
                    if (!secondResponse.hasResult()) {
                        // getStringRepresentation caught an Exception, so we abort.
                        return secondResponse;
                    }
                    str += firstResponse.getValue() + "=" + secondResponse.getValue() + ",";
                }
                // Remove last comma
                str = str.substring(0, str.length() - 1) + "}";
                return new ReflectionResponse<>(str);
            }
            if (object instanceof Collection) {
                String str = "[";
                for (Object listEntry : (Collection) object) {
                    ReflectionResponse<String> response = getStringRepresentation(listEntry, useToString, toStringExceptions);
                    if (!response.hasResult()) {
                        // getStringRepresentation caught an Exception, so we abort.
                        return response;
                    }
                    str += response.getValue() + ",";
                }
                // Remove last comma
                str = str.substring(0, str.length() - 1) + "]";
                return new ReflectionResponse<>(str);
            }
            if (object.getClass().isArray()) {
                String str = "[";
                for (int i = 0; i < Array.getLength(object); i++) {
                    ReflectionResponse<String> response = getStringRepresentation(Array.get(object, i), useToString, toStringExceptions);
                    if (!response.hasResult()) {
                        // getStringRepresentation caught an Exception, so we abort.
                        return response;
                    }
                    str += response.getValue() + ",";
                }
                // Remove last comma
                str = str.substring(0, str.length() - 1) + "]";
                return new ReflectionResponse<>(str);
            }
            if (useToString) {
                if (object.getClass().getMethod("toString").getDeclaringClass() != Object.class && !ArrayUtils.contains(toStringExceptions, object.getClass())) {
                    return new ReflectionResponse<>(object.toString());
                } else {
                    ReflectionResponse<Map<String, String>> response = getPrintableFields(object, true, toStringExceptions);
                    if (!response.hasResult()) {
                        // getPrintableFields caught an Exception, so we abort.
                        return new ReflectionResponse<>(response.getException());
                    }
                    return new ReflectionResponse<>(object.getClass().getSimpleName() + response.getValue());
                }
            } else {
                if (ClassUtils.isPrimitiveWrapper(object.getClass()) || object instanceof String || object instanceof Enum || ArrayUtils.contains(toStringExceptions, object.getClass())) {
                    // Even though useToString is false, we call toString on primitive wrappers, Strings, Enums and the specified exceptions.
                    return new ReflectionResponse<>(object.toString());
                } else {
                    ReflectionResponse<Map<String, String>> response = getPrintableFields(object, false, toStringExceptions);
                    if (!response.hasResult()) {
                        // getPrintableFields caught an Exception, so we abort.
                        return new ReflectionResponse<>(response.getException());
                    }
                    return new ReflectionResponse<>(object.getClass().getSimpleName() + response.getValue());
                }
            }
        } catch (NoSuchMethodException e) {
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

        @Override
        public String toString() {
            return "ReflectionResponse{value=" + value + ",exception=" + exception + ",hasResult=" + hasResult + "}";
        }
    }

    public static class MethodPredicate implements Predicate<Method> {
        private Class<?> returnType;
        private Class<?>[] params;
        private List<Integer> withModifiers;
        private List<Integer> withoutModifiers;
        private Predicate<Method> predicate;
        private String name;

        public MethodPredicate withReturnType(Class<?> returnType) {
            this.returnType = returnType;
            return this;
        }

        public MethodPredicate withParams(Class<?>... params) {
            this.params = params;
            return this;
        }

        public MethodPredicate withModifiers(int... modifiers) {
            this.withModifiers = Arrays.stream(modifiers).boxed().collect(Collectors.toList());
            return this;
        }

        public MethodPredicate withModifiers(Collection<Integer> modifiers) {
            this.withModifiers = new ArrayList<>(modifiers);
            return this;
        }

        public MethodPredicate withoutModifiers(int... modifiers) {
            this.withoutModifiers = Arrays.stream(modifiers).boxed().collect(Collectors.toList());
            return this;
        }

        public MethodPredicate withoutModifiers(Collection<Integer> modifiers) {
            this.withoutModifiers = new ArrayList<>(modifiers);
            return this;
        }

        public MethodPredicate withPredicate(Predicate<Method> predicate) {
            this.predicate = predicate;
            return this;
        }

        public MethodPredicate withName(String name) {
            this.name = name;
            return this;
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean test(Method method) {
            if (returnType != null && method.getReturnType() != returnType) {
                return false;
            }
            if (params != null && !Arrays.equals(method.getParameterTypes(), params)) {
                return false;
            }
            if (withModifiers != null) {
                int modifiers = method.getModifiers();
                for (int bitMask : withModifiers) {
                    if ((modifiers & bitMask) == 0) {
                        return false;
                    }
                }
            }
            if (withoutModifiers != null) {
                int modifiers = method.getModifiers();
                for (int bitMask : withoutModifiers) {
                    if ((modifiers & bitMask) != 0) {
                        return false;
                    }
                }
            }
            if (predicate != null && !predicate.test(method)) {
                return false;
            }
            if (name != null && !method.getName().equals(name)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            List<String> args = Lists.newArrayList();
            if (returnType != null) {
                args.add("return type " + returnType);
            }
            if (params != null) {
                args.add("params " + Arrays.toString(params));
            }
            if (withModifiers != null) {
                args.add("with modifiers (bitmasks) " + withModifiers);
            }
            if (withoutModifiers != null) {
                args.add("without modifiers (bitmasks) " + withoutModifiers);
            }
            if (predicate != null) {
                args.add("specified predicate");
            }
            if (name != null) {
                args.add("with name " + name);
            }
            return Joiner.on(", ").join(args.subList(0, args.size() - 1)) + ", and " + args.get(args.size() - 1);
        }
    }
}

