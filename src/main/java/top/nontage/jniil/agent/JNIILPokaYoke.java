package top.nontage.jniil.agent;

import top.nontage.jniil.accessor.internal.AccessorInitializer;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JNIILPokaYoke {
    private static final Set<String> CORES = new HashSet<>(Arrays.asList(
            "top.nontage.jniil.injector.insn.InsnContext",
            "top.nontage.jniil.injector.functional.MethodInfo",
            "top.nontage.jniil.JNIIL",
            "top.nontage.jniil.JNIIL$InjectionOutputConfig",
            "top.nontage.jniil.injector.functional.internal",
            "top.nontage.jniil.annotations",
            "top.nontage.jniil.interfaces",
            "top.nontage.jniil.monitor",
            "top.nontage.jniil.utils.AccessorUtil",
            "top.nontage.relocated"
    ));

    public static void verifyAndInitialize(Instrumentation instrumentation) {
        if (instrumentation == null) {
            throw new IllegalStateException("[JNIIL] Instrumentation cannot be null during verification.");
        }
        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            String name = loadedClass.getName();
            // if first char is not 't' "top.nontage.jniil" then continue.
            if (name.isEmpty() || name.charAt(0) != 't') continue;
            boolean isCore = CORES.contains(name);
            if (!isCore) {
                for (String core : CORES) {
                    if (name.startsWith(core + ".")) {
                        isCore = true;
                        break;
                    }
                }
            }
            if (isCore && loadedClass.getClassLoader() != null) {
                throw new IllegalStateException(
                        "\n[JNIIL-POKA-YOKE] FATAL: Core class '" + name + "' was loaded by " + loadedClass.getClassLoader().getClass().getSimpleName() + ", but it must be in Bootstrap ClassLoader.\n" +
                                "  Current loader: " + loadedClass.getClassLoader().getClass().getName() + " @" +
                                Integer.toHexString(System.identityHashCode(loadedClass.getClassLoader())) + "\n" +
                                "CAUSE: A JNIIL core class was referenced or loaded before JNIILBootstrap.install() was completed.\n" +
                                "SOLUTION:\n" +
                                "  1. Move JNIILBootstrap.install() to the FIRST line of your program start method.\n" +
                                "  2. Do NOT use JNIIL core components in the same class that calls install().\n" +
                                "     (Restricted Core Components & Prefixes:\n" +
                                "      - " + String.join("\n      - ", CORES) + "\n)\n"
                );
            }
        }
        AccessorInitializer.init();
        checkInvocationMonitor();
    }

    // Make sure no one use InvocationMonitor in the same class that calls install().
    // This check cannot be placed inside the Invocation module because doing so would
    // cause the module to reference its own classes during initialization, triggering
    // a circular dependency and resulting in LinkageError.
    private static void checkInvocationMonitor() {
        try {
            Class<?> clazz = Class.forName("top.nontage.jniil.monitor.InvocationListener");
            if (clazz.getClassLoader() != null) {
                throw new IllegalStateException(
                        "\n[JNIIL] FATAL: InvocationMonitor must be loaded by Bootstrap ClassLoader (null)\n" +
                                "  Current loader: " + clazz.getClassLoader().getClass().getName() + " @" +
                                Integer.toHexString(System.identityHashCode(clazz.getClassLoader())) + "\n" +
                                "\n" +
                                "CAUSE: JNIIL class was referenced before JNIILBootstrap.install() was called,\n" +
                                "       or JNIILBootstrap.install() is in the same class as InvocationMonitor.\n" +
                                "\n" +
                                "SOLUTION:\n" +
                                "  1. Move JNIILBootstrap.install() to the FIRST line of your program start method.\n" +
                                "  2. Do NOT use InvocationMonitor in the same class that calls install().\n" +
                                "  3. Separate InvocationMonitor usage into a different class.\n" +
                                "\n" +
                                "EXAMPLE:\n" +
                                "  public static void main(String[] args) throws Exception {\n" +
                                "      JNIILBootstrap.install(MODE);  // ← FIRST!\n" +
                                "      // ... your code ...\n" +
                                "  }\n" +
                                "  // Use InvocationMonitor in a DIFFERENT class\n" +
                                "\n" +
                                "Current loader: " + clazz.getClassLoader() + "\n" +
                                "Expected loader: null (Bootstrap ClassLoader)"
                );
            }
        } catch (ClassNotFoundException ignored) {
            //
        }
    }
}