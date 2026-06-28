package top.nontage.jniil.agent;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * Locates the JNIIL JAR file from various sources:
 * - Current classpath (CodeSource)
 * - Maven local repository (.m2)
 * - Gradle cache
 */
public class LibraryJarFinder {
    static File getLibraryJar() {
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
}