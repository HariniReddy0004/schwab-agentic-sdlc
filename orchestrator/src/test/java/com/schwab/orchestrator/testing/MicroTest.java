package com.schwab.orchestrator.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A ~60-line, zero-dependency stand-in for JUnit. This build environment cannot reach Maven
 * Central to pull in a real test framework, so we roll the minimal reflection-based runner the
 * test suite needs: an @Test annotation, a runner that instantiates each class with a no-arg
 * constructor and invokes every annotated method, and a small set of assertions.
 */
public final class MicroTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Test {
    }

    public static final class AssertionFailure extends RuntimeException {
        public AssertionFailure(String message) {
            super(message);
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionFailure(message);
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionFailure(message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        boolean equal = expected == null ? actual == null : expected.equals(actual);
        if (!equal) {
            throw new AssertionFailure(message + " (expected <" + expected + "> but was <" + actual + ">)");
        }
    }

    public static void assertNotNull(Object value, String message) {
        if (value == null) throw new AssertionFailure(message);
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void assertThrows(Class<? extends Throwable> expectedType, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) return;
            throw new AssertionFailure(message + " (expected " + expectedType.getSimpleName() + " but got " + t.getClass().getSimpleName() + ")");
        }
        throw new AssertionFailure(message + " (expected " + expectedType.getSimpleName() + " but nothing was thrown)");
    }

    public record Result(String testClass, String method, boolean passed, String detail, long tookMs) {
    }

    public static List<Result> run(Class<?>... classes) {
        List<Result> results = new ArrayList<>();
        for (Class<?> clazz : classes) {
            Object instance;
            try {
                instance = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                results.add(new Result(clazz.getSimpleName(), "<init>", false, "Failed to instantiate: " + e, 0));
                continue;
            }
            for (Method m : clazz.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Test.class)) continue;
                m.setAccessible(true);
                long start = System.nanoTime();
                try {
                    m.invoke(instance);
                    results.add(new Result(clazz.getSimpleName(), m.getName(), true, "ok", (System.nanoTime() - start) / 1_000_000));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    results.add(new Result(clazz.getSimpleName(), m.getName(), false, cause.toString(), (System.nanoTime() - start) / 1_000_000));
                }
            }
        }
        return results;
    }

    public static int report(List<Result> results) {
        int failed = 0;
        for (Result r : results) {
            if (r.passed()) {
                System.out.printf("  PASS  %s.%s (%dms)%n", r.testClass(), r.method(), r.tookMs());
            } else {
                failed++;
                System.out.printf("  FAIL  %s.%s (%dms) -> %s%n", r.testClass(), r.method(), r.tookMs(), r.detail());
            }
        }
        System.out.println();
        System.out.printf("%d tests, %d passed, %d failed%n", results.size(), results.size() - failed, failed);
        return failed;
    }
}
