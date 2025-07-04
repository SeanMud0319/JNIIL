package top.nontage.jniil.javassist;

import javassist.ClassPath;
import javassist.NotFoundException;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

public class FileClassPath implements ClassPath {
    private final File baseDir;

    public FileClassPath(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public InputStream openClassfile(String classname) throws NotFoundException {
        try {
            String path = classname.replace('.', File.separatorChar) + ".class";
            File classFile = new File(baseDir, path);
            if (classFile.exists()) {
                return new FileInputStream(classFile);
            }
            return null;
        } catch (Exception e) {
            throw new NotFoundException("Could not open class file for " + classname);
        }
    }

    @Override
    public URL find(String classname) {
        try {
            String path = classname.replace('.', File.separatorChar) + ".class";
            File classFile = new File(baseDir, path);
            if (classFile.exists()) {
                return classFile.toURI().toURL();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "FileClassPath(" + baseDir.getAbsolutePath() + ")";
    }
}
