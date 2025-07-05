package top.nontage.jniil.javassist;

import javassist.ClassPath;
import javassist.NotFoundException;

import java.io.*;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarFileClassPath implements ClassPath {

    private final JarFile jarFile;
    private final File sourceFile;

    public JarFileClassPath(File jar) throws IOException {
        this.jarFile = new JarFile(jar);
        this.sourceFile = jar;
    }

    @Override
    public InputStream openClassfile(String classname) throws NotFoundException {
        try {
            String path = classname.replace('.', '/') + ".class";
            JarEntry entry = jarFile.getJarEntry(path);
            if (entry == null) return null;
            return jarFile.getInputStream(entry);
        } catch (IOException e) {
            throw new NotFoundException("Could not read class: " + classname);
        }
    }

    @Override
    public URL find(String classname) {
        try {
            String path = classname.replace('.', '/') + ".class";
            JarEntry entry = jarFile.getJarEntry(path);
            if (entry == null) return null;
            return new URL("jar:file:" + sourceFile.getAbsolutePath().replace("\\", "/") + "!/" + path);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "JarFileClassPath(" + sourceFile.getName() + ")";
    }
}
