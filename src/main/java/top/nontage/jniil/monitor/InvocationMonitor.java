package top.nontage.jniil.monitor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;
import top.nontage.jniil.utils.InjectionUtil;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * <h2>JNIIL Invocation Monitor</h2>
 * * <p>A high-performance, bytecode-instrumentation-based monitoring utility that allows
 * real-time tracking, interception, and modification of method invocations.</p>
 *
 * <h3>Core Features:</h3>
 * <ul>
 * <li><b>Dynamic Hooking:</b> Uses ASM 9 to inject interception logic into compiled classes at runtime.</li>
 * <li><b>Context Awareness:</b> Accurately identifies the caller class across different versions of Java (8-17+).</li>
 * <li><b>Invocation Control:</b> Supports cancelling method execution or overriding return values on the fly.</li>
 * <li><b>Cross-Version Support:</b> Automatically switches between {@code sun.reflect.Reflection} and {@code StackWalker}.</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * Method target = MyService.class.getDeclaredMethod("doSomething", String.class);
 * InvocationMonitor.register(target, (caller, targetObj, args, control) -> {
 * System.out.println("Method called by: " + caller.getName());
 * if (args[0].equals("block-me")) {
 * control.setCancelled(true);
 * }
 * });
 * }</pre>
 *
 * <h3>Performance Warning:</h3>
 * <p><b>ATTENTION:</b> This utility introduces non-negligible overhead per invocation due to
 * stack depth calculation and thread-local lookups. While a fingerprint-based caching mechanism
 * is implemented, the cost remains significant in hot paths.</p>
 * <ul>
 * <li><b>DO NOT</b> use this in high-frequency loops or performance-critical paths (e.g., render loops, packet processing).</li>
 * <li>The overhead is approximately ~500ns to 3μs per call depending on stack depth and cache hits.</li>
 * <li>Reflection-based caller identification is expensive; consider filtering logic inside the dispatcher to minimize impact.</li>
 * </ul>
 */
public final class InvocationMonitor {

    private static final Instrumentation inst = JNIIL.getInstrumentation();
    private static final Map<Executable, List<InvocationListener>> LISTENERS = new ConcurrentHashMap<>();
    private static final Map<String, Executable> KEY_TO_EXECUTABLE = new ConcurrentHashMap<>();
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
    private static boolean isJava8 = false;

    static {
        try {
            initCallerMechanism();
        } catch (Exception e) {
            e.printStackTrace();
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
                listener.onInvoke(callerDetail, target, args, control);
            }
            return control;
        } catch (Throwable e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            if (e instanceof Error) throw (Error) e;
            e.printStackTrace();
            return new InvocationControl();
        } finally {
            IN_HOOK.set(false);
        }
    }

    private static void hookExecutable(Executable executable) throws Exception {
        Class<?> targetClass = executable.getDeclaringClass();
        String className = targetClass.getName();

        byte[] classBytes;
        if (InjectionCacheProxy.contains(targetClass)) {
            classBytes = InjectionCacheProxy.get(targetClass);
        } else {
            classBytes = InjectionUtil.getOriginalClassBytes(className);
        }

        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

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
            isJava8 = true;
            Class<?> refClass = Class.forName("sun.reflect.Reflection");
            getCallerMethod8 = refClass.getMethod("getCallerClass", int.class);
            getCallerMethod8.setAccessible(true);
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

        if (isJava8) {
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