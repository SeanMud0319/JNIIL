package top.nontage.jniil.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BootstrapJarBuilder {

    private static boolean hasRelocated = false;

    public static File createFilteredBootstrapJar(File originalJar) throws IOException {
        hasRelocated = false;

        try (JarFile jarFile = new JarFile(originalJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("top/nontage/relocated")) {
                    hasRelocated = true;
                    break;
                }
            }
        }

        File filteredJar = File.createTempFile("jniil-bootstrap-filtered", ".jar");
        filteredJar.deleteOnExit();

        try (JarFile jarFile = new JarFile(originalJar);
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(filteredJar.toPath()))) {

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class")) {
                    continue;
                }

                String className = name.replace('/', '.').substring(0, name.length() - 6);
                if (!shouldKeepClass(className)) continue;

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
        if (className.equals("top.nontage.jniil.injector.insn.InsnContext")) return true;
        if (className.equals("top.nontage.jniil.injector.functional.MethodInfo")) return true;
        if (className.startsWith("top.nontage.jniil.injector.functional.internal")) return true;
        if (className.startsWith("top.nontage.jniil.annotations")) return true;
        if (className.startsWith("top.nontage.jniil.interfaces")) return true;
        if (className.startsWith("top.nontage.relocated")) return true;

        if (!hasRelocated) {
            if (className.startsWith("javassist")) return true;
            return className.startsWith("org.objectweb");
        }

        return false;
    }
}