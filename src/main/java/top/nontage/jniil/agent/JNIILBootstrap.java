package top.nontage.jniil.agent;

import me.fan87.nativeinstrumentation.NativeInstrumentation;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class JNIILBootstrap implements Opcodes {
    public enum MODE {
        ATTACH_API,
        /**
         * May not support on your platform / JVM
         */
        NATIVE
    }

    private static volatile Instrumentation instrumentation;

    public static void install(MODE mode) {
        if (instrumentation != null) return;
        switch (mode) {
            case ATTACH_API:
                attachSelf();
                break;
            case NATIVE:
                instrumentation = new NativeInstrumentation();
                break;
        }
        JNIIL.setInstrumentation(instrumentation);
    }

    public static void setInstrumentation(Instrumentation instrumentation) {
        JNIILBootstrap.instrumentation = instrumentation;
    }

    //Its work on my machine java 8 and 17, 21 :)
    private static Class<?> getVirtualMachineClass() throws Exception {

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

    //fuck you asm
    private static byte[] generateTempAgent() {
        ClassWriter cw = new ClassWriter(0);
        String cls = "top/nontage/jniil/agent/TempAgent";

        cw.visit(V1_8,
                ACC_PUBLIC,
                cls,
                null,
                "java/lang/Object",
                null);

        MethodVisitor init = cw.visitMethod(ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        // public static void agentmain(String args, Instrumentation inst)
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "agentmain",
                "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V",
                null,
                null);

        mv.visitCode();

        // JNIILBootstrap.setInstrumentation(inst);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(
                INVOKESTATIC,
                "top/nontage/jniil/agent/JNIILBootstrap",
                "setInstrumentation",
                "(Ljava/lang/instrument/Instrumentation;)V",
                false
        );

        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

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
