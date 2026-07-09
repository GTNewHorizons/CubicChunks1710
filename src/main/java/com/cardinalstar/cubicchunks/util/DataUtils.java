package com.cardinalstar.cubicchunks.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import cpw.mods.fml.relauncher.ReflectionHelper;

public class DataUtils {

    public static <S, T> List<T> mapToList(Collection<S> in, Function<S, T> mapper) {
        if (in == null) return null;

        List<T> out = new ArrayList<>(in.size());

        for (S s : in) {
            out.add(mapper.apply(s));
        }

        return out;
    }

    public static <S, T> List<T> mapToList(S[] in, Function<S, T> mapper) {
        if (in == null) return null;

        List<T> out = new ArrayList<>(in.length);

        for (S s : in) {
            out.add(mapper.apply(s));
        }

        return out;
    }

    public static <S, T> T[] mapToArray(Collection<S> in, IntFunction<T[]> ctor, Function<S, T> mapper) {
        if (in == null) return null;

        T[] out = ctor.apply(in.size());

        Iterator<S> iter = in.iterator();
        for (int i = 0; i < out.length && iter.hasNext(); i++) {
            out[i] = mapper.apply(iter.next());
        }

        return out;
    }

    public static <S, T> T[] mapToArray(S[] in, IntFunction<T[]> ctor, Function<S, T> mapper) {
        if (in == null) return null;

        T[] out = ctor.apply(in.length);

        for (int i = 0; i < out.length; i++) {
            out[i] = mapper.apply(in[i]);
        }

        return out;
    }

    public static <T> int indexOf(T[] array, T value) {
        int l = array.length;

        for (int i = 0; i < l; i++) {
            if (array[i] == value) {
                return i;
            }
        }

        return -1;
    }

    public static <T> int indexOf(T[] array, Predicate<T> filter) {
        int l = array.length;

        for (int i = 0; i < l; i++) {
            if (filter.test(array[i])) {
                return i;
            }
        }

        return -1;
    }

    public static <T> int indexOf(List<T> array, Predicate<T> filter) {
        for (int i = 0, arraySize = array.size(); i < arraySize; i++) {
            T value = array.get(i);

            if (filter.test(value)) {
                return i;
            }
        }

        return -1;
    }

    public static <T> T find(T[] array, Predicate<T> filter) {
        for (T value : array) {
            if (filter.test(value)) return value;
        }

        return null;
    }

    public static <T> T find(Collection<T> list, Predicate<T> filter) {
        for (T value : list) {
            if (filter.test(value)) return value;
        }

        return null;
    }

    public static <T> boolean contains(T[] array, T object) {
        for (T val : array) {
            if (Objects.equals(val, object)) return true;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    public static <T, R extends T> R findInstance(Collection<T> list, Class<R> clazz) {
        for (T value : list) {
            if (clazz.isInstance(value)) return (R) value;
        }

        return null;
    }

    public static <T> int count(Collection<T> list, Predicate<T> filter) {
        int count = 0;

        for (T value : list) {
            if (filter.test(value)) count++;
        }

        return count;
    }

    public static <T> ArrayList<T> filterList(List<T> input, Predicate<T> filter) {
        ArrayList<T> output = new ArrayList<>(input.size());

        for (int i = 0, inputSize = input.size(); i < inputSize; i++) {
            T t = input.get(i);

            if (filter.test(t)) {
                output.add(t);
            }
        }

        return output;
    }

    public static <T> T getIndexSafe(T[] array, int index) {
        return array == null || index < 0 || index >= array.length ? null : array[index];
    }

    public static <T> T getIndexSafe(List<T> list, int index) {
        return list == null || index < 0 || index >= list.size() ? null : list.get(index);
    }

    public static <T> T choose(List<T> list, Random rng) {
        if (list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);

        return list.get(rng.nextInt(list.size()));
    }

    public static <K, V> boolean areMapsEqual(Map<K, V> left, Map<K, V> right) {
        if (left == null || right == null) {
            return left == right;
        }

        HashSet<K> keys = new HashSet<>(left.size() + right.size());

        keys.addAll(left.keySet());
        keys.addAll(right.keySet());

        for (K key : keys) {
            if (!Objects.equals(left.get(key), right.get(key))) return false;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    public static <A, B> B transmute(A a) {
        return (B) a;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> List<T> transmuteList(List list) {
        return (List<T>) list;
    }

    public static MethodHandle exposeFieldGetter(Class<?> clazz, String... names) {
        try {
            Field field = ReflectionHelper.findField(clazz, names);
            field.setAccessible(true);
            return MethodHandles.lookup()
                .unreflectGetter(field);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not make field getter for " + clazz.getName() + ":" + names[0], e);
        }
    }

    public static MethodHandle exposeFieldSetter(Class<?> clazz, String... names) {
        try {
            Field field = ReflectionHelper.findField(clazz, names);
            field.setAccessible(true);
            return MethodHandles.lookup()
                .unreflectSetter(field);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not make field setter for " + clazz.getName() + ":" + names[0], e);
        }
    }

    public static <T, R> Function<T, R> exposeFieldGetterLambda(Class<? super T> clazz, String... names) {
        final MethodHandle method = exposeFieldGetter(clazz, names);

        return instance -> {
            try {
                // noinspection unchecked
                return (R) method.invokeExact(instance);
            } catch (Throwable e) {
                throw new RuntimeException("Could not get field " + clazz.getName() + ":" + names[0], e);
            }
        };
    }

    public static MethodHandle exposeMethod(Class<?> clazz, MethodType sig, String... names) {
        try {
            Method method = ReflectionHelper.findMethod(clazz, null, names, sig.parameterArray());
            method.setAccessible(true);
            return MethodHandles.lookup()
                .unreflect(method);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not make method handle for " + clazz.getName() + ":" + names[0], e);
        }
    }

    public static <T extends Enum<T>> long enumSetToLong(EnumSet<T> set) {
        long data = 0;

        for (T t : set) {
            data |= 1L << t.ordinal();
        }

        return data;
    }

    public static <T extends Enum<T>> void longToEnumSet(Class<T> clazz, long data, EnumSet<T> set) {
        set.clear();

        for (T t : clazz.getEnumConstants()) {
            if ((data & 1L << t.ordinal()) != 0) {
                set.add(t);
            }
        }
    }

    public static <T> boolean toggleValue(Set<T> set, T value) {
        if (!set.remove(value)) {
            set.add(value);

            return true;
        } else {
            return false;
        }
    }
}
