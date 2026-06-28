package top.nontage.jniil.agent;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.injector.functional.MethodInfo;
import top.nontage.jniil.injector.insn.InsnContext;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.utils.UnsafeUtil;
import top.nontage.jvmcontext.JvmContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.objectweb.asm.Opcodes.*;

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
    private static final Set<Class<?>> STRIP_CLASSES = new HashSet<>();

    static {
        Collections.addAll(STRIP_CLASSES,
                MethodInfo.class,
                InsnContext.class
        );
    }

    public enum MODE {
        ATTACH_API,
        /**
         * I recommend use native mode, see at "<a href="https://github.com/SeanMud0319/JvmContext"></a>"
         */
        NATIVE
    }

    private static volatile Instrumentation instrumentation;

    public static void install(MODE mode) {
        install(mode, false);
    }

    public static void install(MODE mode, boolean useBootLoader) {
        if (instrumentation != null) return;

        if (useBootLoader) {
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
                    byte[] a = InjectionUtil.getClassBytes("top.nontage.jniil.JNIIL");
                    byte[] b = InjectionUtil.getClassBytes("top.nontage.jniil.JNIIL$InjectionOutputConfig");
                    UnsafeUtil.defineClass("top.nontage.jniil.JNIIL", null, a);
                    UnsafeUtil.defineClass("top.nontage.jniil.JNIIL$InjectionOutputConfig", null, b);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
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
            JNIIL.setInstrumentation(instrumentation);

            if (useBootLoader) {
                try {
                    File filteredJar = createFilteredBootstrapJar(getLibraryJar());
                    instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(filteredJar));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static File createFilteredBootstrapJar(File originalJar) throws IOException {
        File filteredJar = File.createTempFile("jniil-bootstrap-filtered", ".jar");
        filteredJar.deleteOnExit();

        try (JarFile jarFile = new JarFile(originalJar);
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(filteredJar.toPath()))) {

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class")) {
                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    boolean shouldKeep = STRIP_CLASSES.stream()
                            .anyMatch(c -> className.equals(c.getName()) || className.startsWith(c.getName() + "$"))
                            || className.startsWith("top.nontage.jniil.injector.functional.internal")
                            || className.startsWith("top.nontage.jniil.annotations")
                            || className.startsWith("top.nontage.jniil.interfaces")
                            || className.startsWith("top.nontage.relocated")
                            || className.startsWith("javassist")
                            || className.startsWith("org.objectweb");

                    if (!shouldKeep) continue;
                }

                zos.putNextEntry(new ZipEntry(name));
                try (InputStream is = jarFile.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }
        return filteredJar;
    }

    private static File getLibraryJar() {
        try {
            URL url = getUrl();
            String protocol = url.getProtocol();

            if ("file".equals(protocol)) {
                try {
                    File file = new File(url.toURI());
                    if (file.exists() && file.isFile()) {
                        return file;
                    }
                } catch (URISyntaxException e) {
                    File file = new File(url.getPath());
                    if (file.exists() && file.isFile()) {
                        return file;
                    }
                }
            }

            if ("jar".equals(protocol)) {
                return extractFileFromJarUrl(url);
            }

        } catch (Exception ignored) {
        }

        return findJarInCache();
    }

    private static File findJarInCache() {
        String userHome = System.getProperty("user.home");
        String version = getVersion();

        File mavenJar = new File(
                userHome + "/.m2/repository/top/nontage/jniil/" + version + "/jniil-" + version + ".jar"
        );
        if (mavenJar.exists() && mavenJar.isFile()) {
            return mavenJar;
        }

        File gradleBase = new File(
                userHome + "/.gradle/caches/modules-2/files-2.1/top.nontage/jniil/" + version + "/"
        );
        if (gradleBase.exists() && gradleBase.isDirectory()) {
            File[] hashDirs = gradleBase.listFiles(File::isDirectory);
            if (hashDirs != null) {
                for (File hashDir : hashDirs) {
                    File[] jars = hashDir.listFiles((dir, name) -> name.endsWith(".jar") && name.startsWith("jniil-"));
                    if (jars != null && jars.length > 0) {
                        System.out.println("[JNIIL] Found JAR: " + jars[0].getAbsolutePath());
                        return jars[0];
                    }
                }
            }
        }

        throw new IllegalStateException(
                "Cannot find JNIIL JAR. Run 'mvn install' or './gradlew publishToMavenLocal' first.\n" +
                        "Searched:\n" +
                        "  - " + mavenJar.getAbsolutePath() + "\n" +
                        "  - " + gradleBase.getAbsolutePath()
        );
    }

    private static String getVersion() {
        try {
            String v = JNIILBootstrap.class.getPackage().getImplementationVersion();
            if (v != null && !v.isEmpty()) return v;
        } catch (Exception ignored) {
        }
        return "1.0-SNAPSHOT";
    }

    private static File extractFileFromJarUrl(URL url) {
        String path = url.getPath();
        int exclamation = path.indexOf("!");
        if (exclamation == -1) {
            throw new IllegalStateException(
                    "Invalid jar URL: " + url
            );
        }
        String jarPath = path.substring(0, exclamation);
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring(5);
        }
        File file = new File(jarPath);
        if (!file.exists()) {
            throw new IllegalStateException(
                    "Jar file does not exist: " + file.getAbsolutePath()
            );
        }
        return file;
    }

    private static URL getUrl() {
        Class<?> clazz = JNIILBootstrap.class;
        ProtectionDomain domain = clazz.getProtectionDomain();
        if (domain == null) {
            throw new IllegalStateException(
                    "ProtectionDomain is null (class loaded by Bootstrap?)"
            );
        }
        CodeSource source = domain.getCodeSource();
        if (source == null) {
            throw new IllegalStateException(
                    "CodeSource is null for " + clazz.getName()
            );
        }
        URL url = source.getLocation();
        if (url == null) {
            throw new IllegalStateException(
                    "URL is null from CodeSource"
            );
        }
        return url;
    }

    // This is for agent
    public static void setInstrumentation(Instrumentation instrumentation) {
        JNIILBootstrap.instrumentation = instrumentation;
    }

    // Its work on my machine java 8 ~ 25 :)
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
