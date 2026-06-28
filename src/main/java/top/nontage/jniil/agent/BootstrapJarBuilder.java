package top.nontage.jniil.agent;

import top.nontage.jniil.injector.functional.MethodInfo;
import top.nontage.jniil.injector.insn.InsnContext;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a filtered JAR for Bootstrap ClassLoader deployment.
 * Only keeps essential classes needed for cross-loader injection.
 */
public class BootstrapJarBuilder {

    private static final Set<Class<?>> KEEP_CLASSES = new HashSet<>();

    static {
        Collections.addAll(KEEP_CLASSES,
                MethodInfo.class,
                InsnContext.class
        );
    }

    public static File createFilteredBootstrapJar(File originalJar) throws IOException {
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
                    boolean shouldKeep = shouldKeepClass(className);
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

    private static boolean shouldKeepClass(String className) {
        return KEEP_CLASSES.stream()
                .anyMatch(c -> className.equals(c.getName()) || className.startsWith(c.getName() + "$"))
                || className.startsWith("top.nontage.jniil.injector.functional.internal")
                || className.startsWith("top.nontage.jniil.annotations")
                || className.startsWith("top.nontage.jniil.interfaces")
                || className.startsWith("top.nontage.relocated")
                || className.startsWith("javassist")
                || className.startsWith("org.objectweb");
    }
}