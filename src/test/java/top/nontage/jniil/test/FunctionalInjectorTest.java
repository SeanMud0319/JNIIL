package top.nontage.jniil.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.injector.functional.FunctionalInjector;
import top.nontage.jniil.test.injector.functional.FunctionalAtHooks;
import top.nontage.jniil.test.injector.functional.FunctionalCancelHooks;
import top.nontage.jniil.test.injector.functional.FunctionalLifecycleHooks;
import top.nontage.jniil.test.target.FunctionalTarget;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FunctionalInjector Test Suite")
class FunctionalInjectorTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        JNIILBootstrap.install(JNIILBootstrap.MODE.NATIVE);
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Test 1: @Before hook - login() entry")
    void testBeforeHook() throws Exception {
        FunctionalInjector injector = new FunctionalInjector();
        injector.inject(new FunctionalLifecycleHooks());

        FunctionalTarget target = new FunctionalTarget("TestUser");
        target.login("secret123");

        String output = outputStream.toString();

        assertTrue(output.contains("[JNIIL-Hook] @Before Login invoked. Password attempted: secret123"));
    }

    @Test
    @DisplayName("Test 2: @After hook - login() exit")
    void testAfterHook() throws Exception {
        FunctionalInjector injector = new FunctionalInjector();
        injector.inject(new FunctionalLifecycleHooks());

        FunctionalTarget target = new FunctionalTarget("TestUser");
        target.login("wrong1");
        target.login("wrong2");
        target.login("wrong3");
        target.login("wrong4");

        String output = outputStream.toString();

        assertTrue(output.contains("[JNIIL-Hook] @After Login invoked. Cancelled status: false"));
    }

    @Test
    @DisplayName("Test 3: @At(line) with @Capture - login() line 24")
    void testAtLineHook() throws Exception {
        FunctionalInjector injector = new FunctionalInjector();
        injector.inject(new FunctionalAtHooks());

        FunctionalTarget target = new FunctionalTarget("TestUser");
        target.login("secret123");

        String output = outputStream.toString();

        assertAll(
                () -> assertTrue(output.contains("[JNIIL-Hook] @At Line 24 + Capture Hit!")),
                () -> assertTrue(output.contains("Captured 'currentAttempt': 1")),
                () -> assertTrue(output.contains("Captured 'passwordMatches': true"))
        );
    }

    @Test
    @DisplayName("Test 4: @At(opcode) with slot-based @Capture - processTransaction()")
    void testAtOpcodeHook() throws Exception {
        FunctionalInjector injector = new FunctionalInjector();
        injector.inject(new FunctionalAtHooks());

        FunctionalTarget target = new FunctionalTarget("TestUser");
        target.processTransaction("100");

        String output = outputStream.toString();

        assertTrue(output.contains("[JNIIL-Hook] @At Opcode Triggered! Input string was: 100"));
    }

    @Test
    @DisplayName("Test 5: Parameter sanitization - processTransaction()")
    void testParameterSanitization() throws Exception {
        FunctionalInjector injector = new FunctionalInjector();
        injector.inject(new FunctionalLifecycleHooks());

        FunctionalTarget target = new FunctionalTarget("TestUser");
        target.processTransaction("INVALID_CRASH_TEST");

        String output = outputStream.toString();

        assertAll(
                () -> assertTrue(output.contains("[JNIIL-Hook] Sanitize Hook: Overwriting argument to '500'")),
                () -> assertTrue(output.contains("[Target] Processing transaction amount: 500"))
        );
    }

    @Test
    @DisplayName("Test 6: Flow cancellation - getLoginAttempts()")
    void testCancelExecution() throws Exception {
        FunctionalInjector injector = new FunctionalInjector();
        injector.inject(new FunctionalCancelHooks());

        FunctionalTarget target = new FunctionalTarget("TestUser");
        int attempts = target.getLoginAttempts();

        String output = outputStream.toString();

        assertAll(
                () -> assertTrue(output.contains("[JNIIL-Hook] getLoginAttempts intercepted. Forcing cancellation flag.")),
                () -> assertEquals(1, attempts)
        );
    }

    @Test
    @DisplayName("Test 7: All hooks together (integration test)")
    void testAllHooksTogether() throws Exception {
        FunctionalInjector injector = new FunctionalInjector();
        injector.inject(
                new FunctionalLifecycleHooks(),
                new FunctionalAtHooks(),
                new FunctionalCancelHooks()
        );

        FunctionalTarget target = new FunctionalTarget("Nontage");

        System.out.println("\n--- [Triggering Login Test Sequence] ---");
        target.login("secret123");

        System.out.println("\n--- [Triggering Normal Transaction] ---");
        target.processTransaction("100");
        System.out.println("\n--- [Triggering Sanitization] ---");
        target.processTransaction("INVALID_CRASH_TEST");

        System.out.println("\n--- [Triggering Cancellation] ---");
        int attempts = target.getLoginAttempts();
        System.out.println("[Example] Method getLoginAttempts returned value: " + attempts);

        String output = outputStream.toString();

        assertAll(
                () -> assertTrue(output.contains("[JNIIL-Hook] @Before Login invoked")),
                () -> assertTrue(output.contains("[JNIIL-Hook] @At Line 24 + Capture Hit!")),
                () -> assertTrue(output.contains("[JNIIL-Hook] @At Opcode Triggered! Input string was: 100")),
                () -> assertTrue(output.contains("[JNIIL-Hook] Sanitize Hook: Overwriting argument to '500'")),
                () -> assertTrue(output.contains("[JNIIL-Hook] getLoginAttempts intercepted")),
                () -> assertEquals(1, attempts)
        );
    }
}