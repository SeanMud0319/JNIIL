package top.nontage.jniil.utils;

import java.io.*;
import java.util.jar.*;
import java.nio.file.Files;

public class JarUtil {

    public static File createTempJar(Class<?>... classes) throws IOException {
        File tempJar = Files.createTempFile("jniil-boot-", ".jar").toFile();
        tempJar.deleteOnExit();

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(tempJar.toPath()))) {
            for (Class<?> clazz : classes) {
                String entryName = clazz.getName().replace('.', '/') + ".class";
                byte[] classBytes = getClassBytes(clazz);

                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                jos.write(classBytes);
                jos.closeEntry();
            }
            jos.finish();
        }
        return tempJar;
    }

    private static byte[] getClassBytes(Class<?> clazz) throws IOException {
        String className = clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(className)) {
            if (is == null) {
                throw new IOException("Class byte not found: " + clazz.getName());
            }
            return readAllBytes(is);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = is.read(buffer)) != -1) {
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }
}
