package top.nontage.jniil.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InjectionUtil Test Suite")
class InjectionUtilTest {

    private static Instrumentation inst;

    @BeforeAll
    static void setUp() {
        JNIILBootstrap.install(JNIILBootstrap.MODE.NATIVE, true);
        inst = JNIIL.getInstrumentation();
        assertNotNull(inst, "Instrumentation should be available");
    }

    @Test
    @DisplayName("findClassAcrossClassLoaders - should find existing class")
    void testFindClassAcrossClassLoaders() throws Exception {
        Class<?> clazz = InjectionUtil.findClassAcrossClassLoaders("java.lang.String");
        assertNotNull(clazz);
        assertEquals("java.lang.String", clazz.getName());
    }

    @Test
    @DisplayName("findClassAcrossClassLoaders - should throw when class not found")
    void testFindClassAcrossClassLoadersNotFound() {
        assertThrows(ClassNotFoundException.class, () ->
                InjectionUtil.findClassAcrossClassLoaders("com.nonexistent.ClassThatDoesNotExist")
        );
    }

    @Test
    @DisplayName("findClassLoaderByThread - should find main thread loader")
    void testFindClassLoaderByThread() {
        Thread currentThread = Thread.currentThread();
        String threadName = currentThread.getName();
        ClassLoader loader = InjectionUtil.findClassLoaderByThread(threadName);
        assertNotNull(loader);
    }

    @Test
    @DisplayName("findClassLoaderByThread - should throw when thread not found")
    void testFindClassLoaderByThreadNotFound() {
        assertThrows(RuntimeException.class, () ->
                InjectionUtil.findClassLoaderByThread("non-existent-thread-12345")
        );
    }

    @Test
    @DisplayName("printAllClassLoader - should not throw")
    void testPrintAllClassLoader() {
        assertDoesNotThrow(InjectionUtil::printAllClassLoader);
    }

    @Test
    @DisplayName("getMethodDescriptor - primitive types")
    void testGetMethodDescriptorPrimitives() {
        String[] params = {"int", "long", "boolean"};
        String desc = InjectionUtil.getMethodDescriptor(params, "void");
        assertEquals("(IJZ)V", desc);
    }

    @Test
    @DisplayName("getMethodDescriptor - object types")
    void testGetMethodDescriptorObjects() {
        String[] params = {"java.lang.String", "java.util.List"};
        String desc = InjectionUtil.getMethodDescriptor(params, "java.lang.Object");
        assertEquals("(Ljava/lang/String;Ljava/util/List;)Ljava/lang/Object;", desc);
    }

    @Test
    @DisplayName("getMethodDescriptor - no params")
    void testGetMethodDescriptorNoParams() {
        String desc = InjectionUtil.getMethodDescriptor(null, "int");
        assertEquals("()I", desc);
    }

    @Test
    @DisplayName("getDescriptor - all types")
    void testGetDescriptor() {
        assertEquals("I", InjectionUtil.getDescriptor("int"));
        assertEquals("J", InjectionUtil.getDescriptor("long"));
        assertEquals("Z", InjectionUtil.getDescriptor("boolean"));
        assertEquals("C", InjectionUtil.getDescriptor("char"));
        assertEquals("B", InjectionUtil.getDescriptor("byte"));
        assertEquals("S", InjectionUtil.getDescriptor("short"));
        assertEquals("F", InjectionUtil.getDescriptor("float"));
        assertEquals("D", InjectionUtil.getDescriptor("double"));
        assertEquals("V", InjectionUtil.getDescriptor("void"));
        assertEquals("V", InjectionUtil.getDescriptor("V"));
        assertEquals("Ljava/lang/String;", InjectionUtil.getDescriptor("java.lang.String"));
    }

    @Test
    @DisplayName("forceLoadClass - should load class with given ClassLoader")
    void testForceLoadClass() throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> clazz = InjectionUtil.forceLoadClass("java.lang.String", loader);
        assertNotNull(clazz);
        assertEquals("java.lang.String", clazz.getName());
    }

    @Test
    @DisplayName("getOriginalClassBytes - should return bytecode for existing class")
    void testGetOriginalClassBytes() throws Exception {
        byte[] bytes = InjectionUtil.getOriginalClassBytes("java.lang.String");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("unsafeInjectClass - should inject class into ClassLoader")
    void testUnsafeInjectClass() throws Throwable {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        String className = "top.nontage.jniil.test.GeneratedTestClass";

        // Generate a simple class with ASM
        byte[] bytes = generateTestClass(className);

        // Inject the class
        Class<?> injectedClass = InjectionUtil.unsafeInjectClass(loader, className, bytes);
        assertNotNull(injectedClass);

        // Verify the class was loaded
        assertEquals(className, injectedClass.getName());

        // Call the static method via reflection and verify output
        Method helloMethod = injectedClass.getDeclaredMethod("hello");
        assertNotNull(helloMethod);

        // Capture System.out to verify the print
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(baos));

        try {
            helloMethod.invoke(null);
            String output = baos.toString().trim();
            assertEquals("Hello from injected class!", output);
        } finally {
            System.setOut(originalOut);
        }
    }

    private byte[] generateTestClass(String className) {
        String internalName = className.replace('.', '/');

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);

        // Default constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // public static void hello()
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "hello", "()V", null, null);
        mv.visitCode();
        // System.out.println("Hello from injected class!");
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("Hello from injected class!");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("JNIIL instrumentation should be available")
    void testInstrumentationAvailable() {
        assertNotNull(inst);
        assertTrue(inst.getAllLoadedClasses().length > 0);
    }
}