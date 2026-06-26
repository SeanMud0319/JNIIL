package top.nontage.jniil.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import top.nontage.jniil.utils.UnsafeUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnsafeUtil Test Suite")
class UnsafeUtilTest {

    private static class TestClass {
        private String privateField = "privateValue";
        private static String staticField = "staticValue";
        private int intField = 42;
        private boolean booleanField = true;

        public TestClass() {}

        public TestClass(String value) {
            this.privateField = value;
        }

        private String privateMethod(String input) {
            return "Private: " + input;
        }

        private static String staticMethod(String input) {
            return "Static: " + input;
        }

        public String getPrivateField() {
            return privateField;
        }
    }

    @Test
    @DisplayName("Unsafe instance should be available")
    void testUnsafeAvailable() {
        assertNotNull(UnsafeUtil.unsafe);
    }

    @Test
    @DisplayName("IMPL_LOOKUP should be available")
    void testImplLookupAvailable() {
        assertNotNull(UnsafeUtil.IMPL_LOOKUP);
    }

    @Test
    @DisplayName("forceGet - should read private instance field")
    void testForceGetInstanceField() throws Exception {
        TestClass obj = new TestClass();
        Field field = TestClass.class.getDeclaredField("privateField");

        Object value = UnsafeUtil.forceGet(field, obj);
        assertEquals("privateValue", value);
    }

    @Test
    @DisplayName("forceGet - should read private static field")
    void testForceGetStaticField() throws Exception {
        Field field = TestClass.class.getDeclaredField("staticField");

        Object value = UnsafeUtil.forceGet(field, null);
        assertEquals("staticValue", value);
    }

    @Test
    @DisplayName("forceSet - should write private instance field")
    void testForceSetInstanceField() throws Exception {
        TestClass obj = new TestClass();
        Field field = TestClass.class.getDeclaredField("privateField");

        UnsafeUtil.forceSet(field, obj, "newValue");
        assertEquals("newValue", obj.getPrivateField());
    }

    @Test
    @DisplayName("forceSet - should write private static field")
    void testForceSetStaticField() throws Exception {
        Field field = TestClass.class.getDeclaredField("staticField");

        String original = (String) UnsafeUtil.forceGet(field, null);
        UnsafeUtil.forceSet(field, null, "newStaticValue");
        assertEquals("newStaticValue", UnsafeUtil.forceGet(field, null));

        // Restore
        UnsafeUtil.forceSet(field, null, original);
    }

    @Test
    @DisplayName("forceGet - should read int field")
    void testForceGetIntField() throws Exception {
        TestClass obj = new TestClass();
        Field field = TestClass.class.getDeclaredField("intField");

        Object value = UnsafeUtil.forceGet(field, obj);
        assertEquals(42, value);
    }

    @Test
    @DisplayName("forceGet - should read boolean field")
    void testForceGetBooleanField() throws Exception {
        TestClass obj = new TestClass();
        Field field = TestClass.class.getDeclaredField("booleanField");

        Object value = UnsafeUtil.forceGet(field, obj);
        assertTrue((boolean) value);
    }

    @Test
    @DisplayName("forceInvoke - should invoke private instance method")
    void testForceInvokeInstanceMethod() throws Exception {
        TestClass obj = new TestClass();
        Method method = TestClass.class.getDeclaredMethod("privateMethod", String.class);

        Object result = UnsafeUtil.forceInvoke(method, obj, "test");
        assertEquals("Private: test", result);
    }

    @Test
    @DisplayName("forceInvoke - should invoke private static method")
    void testForceInvokeStaticMethod() throws Exception {
        Method method = TestClass.class.getDeclaredMethod("staticMethod", String.class);

        Object result = UnsafeUtil.forceInvoke(method, null, "test");
        assertEquals("Static: test", result);
    }

    @Test
    @DisplayName("forceNewInstance - should create instance via constructor")
    void testForceNewInstanceWithConstructor() {
        TestClass obj = (TestClass) UnsafeUtil.forceNewInstance(
                TestClass.class,
                new Class[]{String.class},
                "constructorValue"
        );

        assertNotNull(obj);
        assertEquals("constructorValue", obj.getPrivateField());
    }

    @Test
    @DisplayName("forceNewInstance - should call default constructor when available")
    void testForceNewInstanceNoArgs() {
        TestClass obj = (TestClass) UnsafeUtil.forceNewInstance(
                TestClass.class,
                new Class[]{},
                new Object[]{}
        );

        assertNotNull(obj);
        assertEquals("privateValue", obj.getPrivateField());
    }

    @Test
    @DisplayName("forceNewInstance - fallback with mismatched args uses allocateInstance")
    void testForceNewInstanceFallbackWithMismatchedArgs() throws Exception {
        TestClass obj = (TestClass) UnsafeUtil.forceNewInstance(
                TestClass.class,
                new Class[]{Integer.class},
                new Object[]{123}
        );

        assertNotNull(obj);
        assertNull(obj.getPrivateField());
    }

    @Test
    @DisplayName("forceNewInstance - fallback with matching args sets fields via forceSet")
    void testForceNewInstanceFallbackWithMatchingArgs() throws Exception {
        TestClass obj = (TestClass) UnsafeUtil.forceNewInstance(
                TestClass.class,
                new Class[]{Integer.class},
                new Object[]{"overriddenValue"}
        );

        assertNotNull(obj);
        assertEquals("overriddenValue", obj.getPrivateField());
    }

    @Test
    @DisplayName("forceAllocateInstance - allocates instance without constructor or field initializers")
    void testForceAllocateInstance() {
        TestClass obj = (TestClass) UnsafeUtil.forceAllocateInstance(TestClass.class);

        assertNotNull(obj);
        assertNull(obj.getPrivateField());
    }

    @Test
    @DisplayName("defineClass - should define class with given ClassLoader")
    void testDefineClass() throws Exception {
        byte[] bytes = generateSimpleClassBytecode();

        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> clazz = UnsafeUtil.defineClass(
                "top.nontage.jniil.test.GeneratedClass",
                loader,
                bytes
        );

        assertNotNull(clazz);
        assertEquals("top.nontage.jniil.test.GeneratedClass", clazz.getName());

        Method method = clazz.getDeclaredMethod("getMessage");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        String result = (String) method.invoke(instance);
        assertEquals("Hello from generated class!", result);
    }

    private byte[] generateSimpleClassBytecode() {
        ClassWriter cw = new ClassWriter(
                ClassWriter.COMPUTE_MAXS
        );
        String internalName = "top/nontage/jniil/test/GeneratedClass";

        cw.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                internalName,
                null,
                "java/lang/Object",
                null);
        
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null
        );
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false
        );
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "getMessage",
                "()Ljava/lang/String;",
                null,
                null
        );
        mv.visitCode();
        mv.visitLdcInsn("Hello from generated class!");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}