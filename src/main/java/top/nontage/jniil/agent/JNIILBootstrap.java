package top.nontage.jniil.agent;

import sun.misc.Unsafe;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.transformer.UnsafeTransformer;
import top.nontage.jniil.utils.DebugUtil;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.utils.UnsafeUtil;
import top.nontage.jvmcontext.JvmContext;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Self-attach bootstrap for JNIIL.
 *
 * <p>This class attempts to obtain an {@link java.lang.instrument.Instrumentation} instance by
 * dynamically attaching the current JVM to itself. The mechanism is based on the official
 * Attach API when available, and falls back to alternative "risky" attach methods when required.
 *
 * <h3>Requirements &amp; JVM Compatibility</h3>
 * <ul>
 *   <li><b>Java 8 (JDK only)</b> — Requires <code>tools.jar</code> to be present.
 *       If running under a JRE, this class will attempt to load <code>../lib/tools.jar</code>
 *       manually. Actual availability depends on whether the runtime distribution includes it.
 *   </li>
 *
 *   <li><b>Java 9+</b> — Requires the JVM to provide the <code>jdk.attach</code> module.
 *       Most full JDK distributions include it; minimal or stripped-down runtimes do not.
 *   </li>
 *
 *   <li><b>Java 9+</b> — Self-attach requires the following system property:
 *       <pre>-Djdk.attach.allowAttachSelf=true</pre>
 *   </li>
 *
 *   <li><b>Java 9+</b> — Some JVMs also require opening internal modules:
 *       <pre>
 *       --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
 *       --add-opens java.base/sun.management=ALL-UNNAMED
 *       </pre>
 *   </li>
 *   <li><b>Full commands and ignore JVM warning</b>
 *    <pre>
 *        -XX:+EnableDynamicAgentLoading
 *        -Djdk.attach.allowAttachSelf=true
 *        --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
 *        --add-exports java.management/sun.management=ALL-UNNAMED
 *   </pre>
 * </ul>
 *
 * <h3>Platform Notes</h3>
 * <ul>
 *   <li><b>Linux</b> — Attaching to the current process may be blocked by kernel security
 *       restrictions. If attachment fails with <code>IOException</code>, ensure that
 *       <code>/proc/sys/kernel/yama/ptrace_scope</code> is set to <code>0</code>.
 *   </li>
 *
 *   <li><b>macOS</b> — SIP (System Integrity Protection) may interfere with attaching in
 *       restricted environments such as system Java installations or sandboxed processes.
 *   </li>
 * </ul>
 *
 * <h3>Stability Notice</h3>
 * <ul>
 *   <li>This mechanism relies on JVM internals and platform-specific behaviors.
 *       Future JVM versions may change implementation details of the Attach API.</li>
 *
 *   <li>Some minimal JRE distributions (e.g., Alpine, custom Docker images, musl-based builds)
 *       may not support any form of attachment.</li>
 * </ul>
 *
 * <p>Use at your own risk. Not guaranteed to work on all JVM implementations.</p>
 */
public class JNIILBootstrap {

    public enum MODE {
        ATTACH_API,
        /**
         * I recommend use native mode, see at "<a href="https://github.com/SeanMud0319/JvmContext"></a>"
         */
        NATIVE
    }

    private static volatile Instrumentation instrumentation;

    // Default install method.
    public static void install(MODE mode) {
        install(mode, false);
    }

    // Install without unsafe warning (Only for Java23+)
    public static void install(MODE mode, boolean hiddenWarning) {
        install(mode, hiddenWarning, false);
    }

    // Install and bypass Unsafe restriction (Only for Java23+)
    public static void install(MODE mode, boolean hiddenWarning, boolean forceEnableUnsafe) {
        if (instrumentation != null) return;

        // Hide agent warning is flag is true. This warning message is from InstrumentationImpl constructor, so I just
        // override System.err.println(), and set it back.
        // NOTE: This warning message was added in JDK21
        int version = DebugUtil.getJavaVersion();
        PrintStream origErr = null;
        if (hiddenWarning && version >= 21) {
            origErr = System.err;
            System.setErr(new PrintStream(origErr) {
                @Override
                public void println(String x) {
                    if (x.startsWith("WARNING: A Java agent has been loaded dynamically")) {
                        return;
                    }
                    super.println(x);
                }
            });
        }

        synchronized (JNIILBootstrap.class) {
            if (instrumentation != null) return;
            switch (mode) {
                case ATTACH_API:
                    attachSelf();
                    break;
                case NATIVE:
                    instrumentation = JvmContext.getInstrumentation();
                    break;
            }
        }

        try {
            Class<?> clazz = Class.forName("top.nontage.jniil.JNIIL", false, null);
            if (clazz.getClassLoader() != null) {
                throw new IllegalStateException(
                        "\n[JNIIL] FATAL: JNIIL class was loaded by AppClassLoader, but it must be in Bootstrap ClassLoader.\n" +
                                "Cause: A JNIIL class was referenced before JNIILBootstrap.install() was called.\n" +
                                "Solution: Move JNIILBootstrap.install() to the very first line of your main() method.\n" +
                                "Example:\n" +
                                "  public static void main(String[] args) throws Exception {\n" +
                                "      JNIILBootstrap.install(JNIILBootstrap.MODE.NATIVE);  // ← FIRST!\n" +
                                "      // ... your code ...\n" +
                                "  }\n"
                );
            }
            return;
        } catch (ClassNotFoundException ignored) {
            try {

                /*
                 * CRITICAL: Before appendToBootstrapClassLoaderSearch completes, no classes
                 * that belong in bootstrap classloader (including ASM) may be loaded.
                 * Keep this block minimal and avoid referencing external libraries.
                 */
                File jar = LibraryJarFinder.getLibraryJar();
                BootstrapJarBuilder.deleteTempJar("jniil-bootstrap-filtered");
                File filtered = BootstrapJarBuilder.createFilteredBootstrapJar(jar);
                instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(filtered));

                /*
                 * OpenJDK Unsafe memory-access restrictions (JEP 471 / JEP 498):
                 * - JDK 23: default ALLOW, no warnings
                 * - JDK 24-28: default WARN, warns once then allows
                 * - Future: may become DENY (breaks all Unsafe-dependent tools)
                 *
                 * If hiddenWarning is true, patch Unsafe.beforeMemoryAccessSlow() to bypass.
                 * forceEnableUnsafe controls the patch behavior:
                 *   true  -> unconditional RETURN (bypass ALL checks)
                 *   false -> return unless OPTION == DENY (only bypass WARN/DEBUG)
                 *
                 * @see <a href="https://openjdk.org/jeps/471">JEP 471</a>
                 * @see <a href="https://openjdk.org/jeps/498">JEP 498</a>
                 * 2026/8/18
                 */
                if (hiddenWarning || forceEnableUnsafe  && version >= 23) {
                    try {
                        instrumentation.addTransformer(new UnsafeTransformer(hiddenWarning, forceEnableUnsafe));
                        instrumentation.retransformClasses(Unsafe.class);
                    } catch (UnmodifiableClassException e) {
                        throw new RuntimeException(e);
                    }
                }

                byte[] a = InjectionUtil.getClassBytes("top.nontage.jniil.JNIIL");
                byte[] b = InjectionUtil.getClassBytes("top.nontage.jniil.JNIIL$InjectionOutputConfig");
                byte[] c = InjectionUtil.getClassBytes("top.nontage.jniil.injector.functional.MethodInfo");
                byte[] d = InjectionUtil.getClassBytes("top.nontage.jniil.injector.insn.InsnContext");
                UnsafeUtil.defineClass("top.nontage.jniil.JNIIL", null, a);
                UnsafeUtil.defineClass("top.nontage.jniil.JNIIL$InjectionOutputConfig", null, b);
                UnsafeUtil.defineClass("top.nontage.jniil.injector.functional.MethodInfo", null, c);
                UnsafeUtil.defineClass("top.nontage.jniil.injector.insn.InsnContext", null, d);


                // verifyAndInitialize will call Accessor#init so we need setInstrumentation after finish append bootstrap
                // loader before call verify.
                JNIIL.setInstrumentation(instrumentation);
                JNIILPokaYoke.verifyAndInitialize(instrumentation);

                // Set back System.err
                if (origErr != null) {
                    System.setErr(origErr);
                }
            } catch (Throwable throwable) {
                if (DebugUtil.getJavaVersion() >= 23 && !forceEnableUnsafe) {
                    throw new IllegalStateException(
                            "Unsafe may be disabled by JVM flag --sun-misc-unsafe-memory-access=deny " +
                                    "or JDK default restrictions. Try: JNIILBootstrap.install(MODE.NATIVE, true, true)",
                            throwable
                    );
                }
                throw new IllegalStateException("Unexpected error during Unsafe initialization", throwable);
            }
        }
    }

    // This is for agent
    public static void setInstrumentation(Instrumentation instrumentation) {
        JNIILBootstrap.instrumentation = instrumentation;
    }

    // Its work on my machine java 8 ~ 26 :)
    private static Class<?> getVirtualMachineClass() {

        // First try: already available
        try {
            return Class.forName("com.sun.tools.attach.VirtualMachine");
        } catch (Throwable ignored) {
        }

        // Java 8: force load tools.jar
        try {
            String javaHome = System.getProperty("java.home");
            File toolsJar = new File(javaHome + "/../lib/tools.jar");

            if (toolsJar.exists()) {
                URLClassLoader loader = new URLClassLoader(
                        new URL[]{toolsJar.toURI().toURL()},
                        ClassLoader.getSystemClassLoader()
                );
                return Class.forName("com.sun.tools.attach.VirtualMachine", true, loader);
            }
        } catch (Throwable ignored) {
        }

        // Java 9+: Module System (fuck you)
        try {
            Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");
            Object bootLayer = moduleLayerClass.getMethod("boot").invoke(null);
            Object optionalModule = moduleLayerClass
                    .getMethod("findModule", String.class)
                    .invoke(bootLayer, "jdk.attach");

            boolean present = (boolean) Class
                    .forName("java.util.Optional")
                    .getMethod("isPresent")
                    .invoke(optionalModule);

            if (present) {
                return Class.forName("com.sun.tools.attach.VirtualMachine");
            }
        } catch (Throwable ignored) {
        }

        throw new IllegalStateException("VirtualMachine not available");
    }

    // attach api is gay
    private static void attachSelf() {
        try {
            byte[] agentBytes = generateTempAgent();
            Path jarPath = createAgentJar(agentBytes);
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            Class<?> vmClass = getVirtualMachineClass();
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
            vmClass.getMethod("loadAgent", String.class).invoke(vm, jarPath.toAbsolutePath().toString());
            vmClass.getMethod("detach").invoke(vm);
            int wait = 0;
            while (instrumentation == null && wait < 2000) {
                Thread.sleep(1);
                wait++;
            }

            if (instrumentation == null) {
                throw new IllegalStateException("agentmain did not provide instrumentation");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // This byte array is generated by the commented ASM method below, then encoded to Base64.
    // Storing it as a Base64 string in the constant pool reduces class file size and speeds up loading.
    private static byte[] generateTempAgent() {
        String bytes = "yv66vgAAADQAEgEAIXRvcC9ub250YWdlL2puaWlsL2FnZW50L1RlbXBBZ2VudAcAAQEAEGphdmEvbGFuZy9PYmplY3QHAAMBAAY8aW5pdD4BAAMoKVYMAAUABgoABAAHAQAJYWdlbnRtYWluAQA7KExqYXZhL2xhbmcvU3RyaW5nO0xqYXZhL2xhbmcvaW5zdHJ1bWVudC9JbnN0cnVtZW50YXRpb247KVYBACZ0b3Avbm9udGFnZS9qbmlpbC9hZ2VudC9KTklJTEJvb3RzdHJhcAcACwEAEnNldEluc3RydW1lbnRhdGlvbgEAKShMamF2YS9sYW5nL2luc3RydW1lbnQvSW5zdHJ1bWVudGF0aW9uOylWDAANAA4KAAwADwEABENvZGUAAQACAAQAAAAAAAIAAQAFAAYAAQARAAAAEQABAAEAAAAFKrcACLEAAAAAAAkACQAKAAEAEQAAABEAAQACAAAABSu4ABCxAAAAAAAA";
        return Base64.getDecoder().decode(bytes);
    }

// Source of the byte array above
//    private static byte[] generateTempAgent() {
//        ClassWriter cw = new ClassWriter(0);
//        String cls = "top/nontage/jniil/agent/TempAgent";
//
//        cw.visit(V1_8,
//                ACC_PUBLIC,
//                cls,
//                null,
//                "java/lang/Object",
//                null);
//
//        MethodVisitor init = cw.visitMethod(ACC_PUBLIC,
//                "<init>",
//                "()V",
//                null,
//                null);
//        init.visitCode();
//        init.visitVarInsn(ALOAD, 0);
//        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object",
//                "<init>", "()V", false);
//        init.visitInsn(RETURN);
//        init.visitMaxs(1, 1);
//        init.visitEnd();
//
//        // public static void agentmain(String args, Instrumentation inst)
//        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
//                "agentmain",
//                "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V",
//                null,
//                null);
//
//        mv.visitCode();
//
//        // JNIILBootstrap.setInstrumentation(inst);
//        mv.visitVarInsn(ALOAD, 1);
//        mv.visitMethodInsn(
//                INVOKESTATIC,
//                "top/nontage/jniil/agent/JNIILBootstrap",
//                "setInstrumentation",
//                "(Ljava/lang/instrument/Instrumentation;)V",
//                false
//        );
//
//        mv.visitInsn(RETURN);
//        mv.visitMaxs(1, 2);
//        mv.visitEnd();
//
//        cw.visitEnd();
//        return cw.toByteArray();
//    }

    private static Path createAgentJar(byte[] agentBytes) throws IOException {
        Path tempJar = Files.createTempFile("tempAgent", ".jar");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempJar))) {
            zos.putNextEntry(new ZipEntry("META-INF/"));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            String manifest =
                    "Manifest-Version: 1.0\n" +
                            "Agent-Class: top.nontage.jniil.agent.TempAgent\n" +
                            "Can-Redefine-Classes: true\n" +
                            "Can-Retransform-Classes: true\n";
            zos.write(manifest.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("top/nontage/jniil/agent/TempAgent.class"));
            zos.write(agentBytes);
            zos.closeEntry();
        }

        return tempJar;
    }
}
