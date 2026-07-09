package com.cardinalstar.cubicchunks.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import lombok.SneakyThrows;

public class JavaUtils {

    public static final int JVM_VERSION = Integer.parseInt(System.getProperty("java.specification.version"));

    @SneakyThrows
    public static void onSpinWait() {
        if (JVM_VERSION >= 9) {
            Java9.ON_SPIN_WAIT.invokeExact();
        }
    }

    /// Inner class to avoid class loading
    private static class Java9 {

        public static final MethodHandle ON_SPIN_WAIT = DataUtils
            .exposeMethod(Thread.class, MethodType.methodType(void.class), "onSpinWait");
    }

}
