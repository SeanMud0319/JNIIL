package top.nontage.jniil.test;

import org.junit.jupiter.api.Test;
import top.nontage.jvmcontext.JvmContext;
import java.lang.instrument.Instrumentation;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JvmContextDiagnosticTest {

    @Test
    public void testJvmContextDirectly() {
        System.out.println("=== Testing JvmContext directly ===");
        System.out.println("ClassLoader: " + JvmContext.class.getClassLoader());
        System.out.println("JvmContext class: " + JvmContext.class.getProtectionDomain().getCodeSource().getLocation());

        try {
            Instrumentation inst = JvmContext.getInstrumentation();
            assertNotNull(inst);
            System.out.println("JvmContext.getInstrumentation() succeeded!");
            System.out.println("Loaded classes: " + inst.getAllLoadedClasses().length);
        } catch (Exception e) {
            System.err.println("JvmContext.getInstrumentation() failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}