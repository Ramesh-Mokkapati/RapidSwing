package com.rapidswing.data;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads and writes JavaBean properties by name ({@code getName}/{@code isActive}/{@code setName}), e.g. to
 * evaluate a {@link Query} in memory or to build screens from a bean class. Descriptors are cached per class.
 */
public final class BeanProperties {

    private static final Map<Class<?>, Map<String, PropertyDescriptor>> CACHE = new ConcurrentHashMap<>();

    private BeanProperties() {
    }

    /** The readable properties of the class, in declaration order where the JVM reports it, without "class". */
    public static List<PropertyDescriptor> of(Class<?> type) {
        return Collections.unmodifiableList(new ArrayList<>(descriptors(type).values()));
    }

    public static PropertyDescriptor find(Class<?> type, String property) {
        return descriptors(type).get(property);
    }

    public static Object get(Object bean, String property) {
        if (bean == null) {
            return null;
        }
        PropertyDescriptor descriptor = find(bean.getClass(), property);
        if (descriptor == null || descriptor.getReadMethod() == null) {
            throw new IllegalArgumentException("No readable property '" + property + "' in " + bean.getClass().getName());
        }
        return invoke(descriptor.getReadMethod(), bean);
    }

    public static void set(Object bean, String property, Object value) {
        PropertyDescriptor descriptor = find(bean.getClass(), property);
        if (descriptor == null || descriptor.getWriteMethod() == null) {
            throw new IllegalArgumentException("No writable property '" + property + "' in " + bean.getClass().getName());
        }
        invoke(descriptor.getWriteMethod(), bean, value);
    }

    private static Map<String, PropertyDescriptor> descriptors(Class<?> type) {
        return CACHE.computeIfAbsent(type, beanType -> {
            try {
                Map<String, PropertyDescriptor> found = new LinkedHashMap<>();
                Map<String, Integer> order = declarationOrder(beanType);
                List<PropertyDescriptor> all = new ArrayList<>();
                for (PropertyDescriptor descriptor : Introspector.getBeanInfo(beanType, Object.class).getPropertyDescriptors()) {
                    if (descriptor.getReadMethod() != null) {
                        all.add(descriptor);
                    }
                }
                all.sort((a, b) -> Integer.compare(order.getOrDefault(a.getName(), Integer.MAX_VALUE),
                        order.getOrDefault(b.getName(), Integer.MAX_VALUE)));
                for (PropertyDescriptor descriptor : all) {
                    found.put(descriptor.getName(), descriptor);
                }
                return found;
            } catch (IntrospectionException e) {
                throw new IllegalArgumentException("Cannot inspect " + beanType.getName(), e);
            }
        });
    }

    /** Field order of the class and its superclasses (superclass fields first), which usually matches the getters. */
    private static Map<String, Integer> declarationOrder(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(0, current);
        }
        Map<String, Integer> order = new LinkedHashMap<>();
        for (Class<?> current : hierarchy) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                order.putIfAbsent(field.getName(), order.size());
            }
        }
        return order;
    }

    private static Object invoke(Method method, Object bean, Object... args) {
        try {
            return method.invoke(bean, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        }
    }
}
