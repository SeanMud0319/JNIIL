package top.nontage.jniil.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.injector.ClassInjector;
import top.nontage.jniil.test.target.ClassTarget;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ClassInjector Test Suite")
public class ClassInjectorTest {
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        JNIILBootstrap.install(JNIILBootstrap.MODE.NATIVE);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Test 1: Inject class with annotation")
    void testInjectClassInfo() throws Throwable {
        ClassInjector.injectClass(ClassTarget.class);
        Class<?> clazz = Class.forName("top.nontage.jniil.test.target.ClassTarget", true, null);
        ClassLoader loader = clazz.getClassLoader();
        Method method = clazz.getDeclaredMethod("testMethod");
        String result = (String) method.invoke(null);
        assertEquals("Injected Successfully", result);
        assertNull(loader, "Class should be loaded by Bootstrap ClassLoader");
        System.out.println("[Test 1] Annotation injection successful");
    }
}
