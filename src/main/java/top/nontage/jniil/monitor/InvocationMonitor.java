package top.nontage.jniil.monitor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;
import top.nontage.jniil.monitor.internal.InvocationClassVisitor;
import top.nontage.jniil.utils.InjectionUtil;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * JNIIL Invocation Monitor with AOP support
 */
public final class InvocationMonitor {

    private static final Instrumentation inst = JNIIL.getInstrumentation();
    private static final Map<Executable, List<InvocationListener>> LISTENERS = new ConcurrentHashMap<>();
    private static final Map<String, Executable> KEY_TO_EXECUTABLE = new ConcurrentHashMap<>();
    private static final List<ClassMatcher> CLASS_MATCHERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<CallerSnapshot> SNAPSHOT = ThreadLocal.withInitial(CallerSnapshot::new);
    private static final ThreadLocal<Class<?>> SKIP_TARGET_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IN_HOOK = ThreadLocal.withInitial(() -> false);
    private static Method getCallerMethod8;
    private static Object walkerInstance;
    private static Method walkMethod;
    private static Method iteratorMethod;
    private static Method getDeclaringClassMethod;
    private static Method getMethodNameMethod;
    private static Object cachedFunctionProxy;
    private static boolean isLegacy = false;
    private static boolean init = false;

    static {
        try {
            initCallerMechanism();
        } catch (Exception e) {
            throw new RuntimeException(
                    "[JNIIL] Failed to initialize InvocationMonitor caller mechanism.\n" +
                            "Make sure JNIILBootstrap.install() is called before using JNIIL.",
                    e
            );
        }
    }

    private InvocationMonitor() {
    }

    public static InvocationListener register(Method target, InvocationListener listener) {
        return register((Executable) target, listener);
    }

    public static InvocationListener register(Constructor<?> target, InvocationListener listener) {
        return register((Executable) target, listener);
    }

    private static InvocationListener register(Executable target, InvocationListener listener) {
        String key = getExecutableKey(target);
        KEY_TO_EXECUTABLE.putIfAbsent(key, target);

        List<InvocationListener> listeners = LISTENERS.computeIfAbsent(target, k -> {
            try {
                hookExecutable(k);
            } catch (Exception e) {
                throw new RuntimeException("Failed to hook executable: " + target, e);
            }
            return new CopyOnWriteArrayList<>();
        });
        listeners.add(listener);
        return listener;
    }

    public static ClassMatcher matchClass(Class<?> clazz) {
        return matchClass(clazz.getName());
    }

    public static ClassMatcher matchClass(String className) {
        ClassMatcher matcher = new ClassMatcher(className);
        CLASS_MATCHERS.add(matcher);
        if (!className.contains("*") && !className.contains("/**") && !className.contains("/*")) {
            try {
                Class<?> clazz = Class.forName(className);
                applyMatchersToClass(clazz);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return matcher;
    }

    public static void applyMatchersToClass(Class<?> targetClass) {
        if (CLASS_MATCHERS.isEmpty()) return;

        String className = targetClass.getName();

        for (ClassMatcher matcher : CLASS_MATCHERS) {
            if (matcher.matches(className)) {
                List<InvocationListener> matcherListeners = matcher.getListeners();
                if (matcherListeners.isEmpty()) continue;

                for (Method method : targetClass.getDeclaredMethods()) {
                    if (matcher.matchesMethod(method)) {
                        String key = getExecutableKey(method);
                        KEY_TO_EXECUTABLE.putIfAbsent(key, method);
                        if (!LISTENERS.containsKey(method)) {
                            try {
                                hookExecutable(method);
                                LISTENERS.put(method, new CopyOnWriteArrayList<>());
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to hook executable: ", e);
                            }
                        }
                        LISTENERS.get(method).addAll(matcherListeners);
                    }
                }

                for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
                    if (matcher.matchesConstructor(constructor)) {
                        String key = getExecutableKey(constructor);
                        KEY_TO_EXECUTABLE.putIfAbsent(key, constructor);
                        if (!LISTENERS.containsKey(constructor)) {
                            try {
                                hookExecutable(constructor);
                                LISTENERS.put(constructor, new CopyOnWriteArrayList<>());
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to hook executable: ", e);
                            }
                        }
                        LISTENERS.get(constructor).addAll(matcherListeners);
                    }
                }
            }
        }
    }

    public static void applyMatchersToAllLoadedClasses() {
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            applyMatchersToClass(clazz);
        }
    }

    public static void registerAllMethods(Class<?> targetClass, InvocationListener listener) {
        for (Method method : targetClass.getDeclaredMethods()) {
            register(method, (callerDetail, target, exec, args, control) ->
                    listener.onInvoke(callerDetail, target, method, args, control));
        }
    }

    public static void registerAllConstructors(Class<?> targetClass, InvocationListener listener) {
        for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
            register(constructor, (callerDetail, target, exec, args, control) ->
                    listener.onInvoke(callerDetail, target, constructor, args, control));
        }
    }

    public static void unregister(Method target, InvocationListener listener) {
        unregister((Executable) target, listener);
    }

    public static void unregister(Constructor<?> target, InvocationListener listener) {
        unregister((Executable) target, listener);
    }

    private static void unregister(Executable target, InvocationListener listener) {
        List<InvocationListener> listeners = LISTENERS.get(target);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public static InvocationControl dispatch(String key, Object target, Object[] args) {
        if (IN_HOOK.get()) {
            return new InvocationControl();
        }
        try {
            IN_HOOK.set(true);

            Executable exec = findExecutableByKey(key);
            if (exec == null) return new InvocationControl();

            List<InvocationListener> listeners = LISTENERS.get(exec);
            if (listeners == null || listeners.isEmpty()) return new InvocationControl();

            boolean anyNeedsCaller = false;
            for (InvocationListener l : listeners) {
                if (l.needsCaller()) {
                    anyNeedsCaller = true;
                    break;
                }
            }

            CallerDetail callerDetail = anyNeedsCaller ? getCaller(exec.getDeclaringClass()) : null;

            InvocationControl control = new InvocationControl();
            for (InvocationListener listener : listeners) {
                if (control.isCancelled()) {
                    break;
                }
                listener.onInvoke(callerDetail, target, exec, args, control);
            }
            return control;
        } catch (Throwable e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            if (e instanceof Error) throw (Error) e;
            e.printStackTrace();
            return new InvocationControl();
        } finally {
            IN_HOOK.set(false);
        }
    }

    private static void hookExecutable(Executable executable) throws Exception {
        if (!init) {
            throw new IllegalStateException("Caller mechanism is not initialized.");
        }
        Class<?> targetClass = executable.getDeclaringClass();
        String className = targetClass.getName();

        byte[] classBytes;
        if (InjectionCacheProxy.contains(targetClass)) {
            classBytes = InjectionCacheProxy.get(targetClass);
        } else {
            classBytes = InjectionUtil.getOriginalClassBytes(className);
        }

        ClassReader cr = new ClassReader(classBytes);

        /*
         * Since ASM uses the relocated version and is currently defined into the Bootstrap ClassLoader,
         * the default getCommonSuperClass() looks up types only from Bootstrap's classpath.
         * This means it will fail for any target classes not visible to Bootstrap.
         * Therefore, on failure, this fallback first tries SystemClassLoader, then the target class's own ClassLoader.
         * If both fail, it falls back to cross-loader search via findClassAcrossClassLoaders.
         * If still not found, it returns "java/lang/Object" as a safe fallback.
         * 2026 / 08 / 26
         */
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable ignored) {
                }

                String internalName1 = type1.replace('/', '.');
                String internalName2 = type2.replace('/', '.');

                try {
                    return loadAndFindCommonSuper(internalName1, internalName2, ClassLoader.getSystemClassLoader());
                } catch (Throwable ignored) {
                }

                try {
                    ClassLoader loader = targetClass.getClassLoader();
                    if (loader != null) {
                        return loadAndFindCommonSuper(internalName1, internalName2, loader);
                    }
                } catch (Throwable ignored) {
                }

                try {
                    Class<?> c1 = InjectionUtil.findClassAcrossClassLoaders(internalName1);
                    Class<?> c2 = InjectionUtil.findClassAcrossClassLoaders(internalName2);
                    return getCommonSuperClass(c1, c2);
                } catch (Throwable ignored) {
                }
                return "java/lang/Object";
            }

            private String loadAndFindCommonSuper(String name1, String name2, ClassLoader loader) throws ClassNotFoundException {
                Class<?> c1 = Class.forName(name1, false, loader);
                Class<?> c2 = Class.forName(name2, false, loader);
                return getCommonSuperClass(c1, c2);
            }

            private String getCommonSuperClass(Class<?> c1, Class<?> c2) {
                if (c1.isAssignableFrom(c2)) return c1.getName().replace('.', '/');
                if (c2.isAssignableFrom(c1)) return c2.getName().replace('.', '/');
                Class<?> parent = c1.getSuperclass();
                while (parent != null && !parent.isAssignableFrom(c2)) {
                    parent = parent.getSuperclass();
                }
                return parent == null ? "java/lang/Object" : parent.getName().replace('.', '/');
            }
        };

        String key = getExecutableKey(executable);
        InvocationClassVisitor cv = new InvocationClassVisitor(cw, executable, key);
        cr.accept(cv, ClassReader.EXPAND_FRAMES);

        byte[] newBytes = cw.toByteArray();
        redefineClass(targetClass, newBytes);
        InjectionCacheProxy.put(targetClass, newBytes);
    }


    @SuppressWarnings({"SuspiciousInvocationHandlerImplementation", "unchecked", "rawtypes"})
    private static void initCallerMechanism() throws Exception {
        try {
            Class<?> walkerClass = Class.forName("java.lang.StackWalker");
            Class<?> optionClass = Class.forName("java.lang.StackWalker$Option");
            Object retainOption = Enum.valueOf((Class<Enum>) optionClass, "RETAIN_CLASS_REFERENCE");

            Method getInstance = walkerClass.getMethod("getInstance", Set.class);
            walkerInstance = getInstance.invoke(null, Collections.singleton(retainOption));

            Class<?> functionClass = Class.forName("java.util.function.Function");
            Class<?> streamClass = Class.forName("java.util.stream.Stream");
            Class<?> frameClass = Class.forName("java.lang.StackWalker$StackFrame");

            walkMethod = walkerClass.getMethod("walk", functionClass);
            iteratorMethod = streamClass.getMethod("iterator");
            getDeclaringClassMethod = frameClass.getMethod("getDeclaringClass");
            getMethodNameMethod = frameClass.getMethod("getMethodName");

            cachedFunctionProxy = Proxy.newProxyInstance(
                    InvocationMonitor.class.getClassLoader(),
                    new Class[]{functionClass},
                    (proxy, method, args) -> {
                        Iterator<?> it = (Iterator<?>) iteratorMethod.invoke(args[0]);
                        Class<?> skipTarget = SKIP_TARGET_HOLDER.get();

                        while (it.hasNext()) {
                            Object frame = it.next();
                            Class<?> currentClass = (Class<?>) getDeclaringClassMethod.invoke(frame);
                            String methodName = (String) getMethodNameMethod.invoke(frame);
                            String cn = currentClass.getName();

                            if (isFramework(cn)) continue;
                            if (currentClass.equals(skipTarget)) continue;

                            return new CallerDetail(currentClass, methodName);
                        }
                        return new CallerDetail();
                    }
            );
        } catch (ClassNotFoundException e) {
            isLegacy = true;
            Class<?> refClass = Class.forName("sun.reflect.Reflection");
            getCallerMethod8 = refClass.getMethod("getCallerClass", int.class);
            getCallerMethod8.setAccessible(true);
        } finally {
            init = true;
        }
    }

    private static CallerDetail getCaller(Class<?> skipClass) throws Exception {
        CallerSnapshot snap = SNAPSHOT.get();
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int currentDepth = stack.length;
        String fingerprint = (currentDepth > 4) ? stack[4].getClassName() : "";

        if (snap.lastCaller != null && currentDepth == snap.lastStackDepth
                && snap.lastTargetClass == skipClass && fingerprint.equals(snap.lastFingerprint)) {
            return snap.lastCaller;
        }

        CallerDetail result = null;

        if (isLegacy) {
            for (int i = 3; i < stack.length; i++) {
                StackTraceElement frame = stack[i];
                String cn = frame.getClassName();

                if (isFramework(cn) || cn.equals(skipClass.getName())) continue;
                try {
                    Class<?> clazz = (Class<?>) getCallerMethod8.invoke(null, i);
                    if (clazz != null && clazz.getName().equals(cn)) {
                        result = new CallerDetail(clazz, frame.getMethodName());
                        break;
                    }
                } catch (Exception ignored) {
                    result = new CallerDetail(Class.forName(cn), frame.getMethodName());
                    break;
                }
            }
        } else {
            SKIP_TARGET_HOLDER.set(skipClass);
            try {
                result = (CallerDetail) walkMethod.invoke(walkerInstance, cachedFunctionProxy);
            } finally {
                SKIP_TARGET_HOLDER.remove();
            }
        }

        if (result == null) {
            result = new CallerDetail(skipClass, "unknown");
        }

        snap.lastCaller = result;
        snap.lastStackDepth = currentDepth;
        snap.lastTargetClass = skipClass;
        snap.lastFingerprint = fingerprint;

        return result;
    }

    private static boolean isFramework(String cn) {
        return cn.equals("top.nontage.jniil.monitor.InvocationMonitor") ||
                cn.startsWith("java.lang.StackWalker") ||
                cn.startsWith("java.lang.reflect.") ||
                cn.startsWith("jdk.internal.reflect.") ||
                cn.startsWith("sun.reflect.") ||
                cn.startsWith("java.lang.Thread") ||
                cn.contains("Proxy");
    }

    private static void redefineClass(Class<?> clazz, byte[] bytecode) throws UnmodifiableClassException, ClassNotFoundException {
        inst.redefineClasses(new ClassDefinition(clazz, bytecode));
    }

    private static String getExecutableKey(Executable executable) {
        String name = (executable instanceof Method) ? executable.getName() : "<init>";
        return executable.getDeclaringClass().getName() + "#" + name + "(" +
                Arrays.stream(executable.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")) + ")";
    }

    private static Executable findExecutableByKey(String key) {
        return KEY_TO_EXECUTABLE.get(key);
    }

    private static class CallerSnapshot {
        CallerDetail lastCaller;
        Class<?> lastTargetClass;
        int lastStackDepth;
        String lastFingerprint;
    }
}