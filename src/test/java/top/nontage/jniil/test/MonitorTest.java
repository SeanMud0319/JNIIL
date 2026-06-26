package top.nontage.jniil.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.monitor.*;
import top.nontage.jniil.test.target.MonitorTarget;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InvocationMonitor Test Suite")
class MonitorTest {

    private MonitorTarget target;

    @BeforeEach
    void setUp() {
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);
        target = new MonitorTarget("TestUser", 25, 1000);
    }

    @Test
    @DisplayName("Test 1: Method-specific monitoring")
    void testMethodSpecificMonitoring() throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);
        Method method = MonitorTarget.class.getMethod("deposit", int.class);

        InvocationMonitor.register(method, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                called.set(true);
                assertEquals(500, args[0]);
            }

        });

        target.deposit(500);
        assertTrue(called.get());
    }

    @Test
    @DisplayName("Test 2: Return value override")
    void testReturnValueOverride() throws Exception {
        Method method = MonitorTarget.class.getMethod("getBalance");

        InvocationMonitor.register(method, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                control.setReturnValue(9999);
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        });

        int balance = target.getBalance();
        assertEquals(9999, balance);
    }

    @Test
    @DisplayName("Test 3: Constructor monitoring")
    void testConstructorMonitoring() throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);

        InvocationMonitor.registerAllConstructors(MonitorTarget.class, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                called.set(true);
                assertNotNull(args);
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        });

        new MonitorTarget("NewUser", 30);
        assertTrue(called.get());
    }

    @Test
    @DisplayName("Test 4: Method cancellation")
    void testMethodCancellation() throws Exception {
        Method method = MonitorTarget.class.getMethod("processLogin", String.class, String.class);

        InvocationMonitor.register(method, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                String username = (String) args[0];
                if ("blocked".equals(username)) {
                    control.cancel();
                    control.setReturnValue("BLOCKED");
                }
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        });

        String result = target.processLogin("blocked", "anything");
        assertEquals("BLOCKED", result);
    }

    @Test
    @DisplayName("Test 5: ClassMatcher with pattern")
    void testClassMatcher() throws Exception {
        AtomicBoolean matched = new AtomicBoolean(false);

        InvocationMonitor.matchClass("top.nontage.jniil.test.target.MonitorTarget")
                .methodNameStartsWith("calc")
                .withListener(new InvocationListener() {
                    @Override
                    public void onInvoke(CallerDetail callerDetail, Object target,
                                         Executable targetMethod,
                                         Object[] args, InvocationControl control) {
                        matched.set(true);
                    }

                    @Override
                    public boolean needsCaller() {
                        return false;
                    }
                });

        InvocationMonitor.applyMatchersToAllLoadedClasses();

        target.calculateBonus(100, 2);
        assertTrue(matched.get());
    }

    @Test
    @DisplayName("Test 6: Parameter manipulation - cap withdrawal")
    void testParameterManipulation() throws Exception {
        MonitorTarget freshTarget = new MonitorTarget("Fresh", 20, 10000);

        Method withdrawMethod = MonitorTarget.class.getMethod("withdraw", int.class);

        InvocationMonitor.register(withdrawMethod, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                int amount = (int) args[0];
                if (amount > 500) {
                    MonitorTarget t = (MonitorTarget) target;
                    t.withdraw(500);
                    control.cancel();
                    control.setReturnValue(true);
                }
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        });

        freshTarget.withdraw(2000);

        Field balanceField = MonitorTarget.class.getDeclaredField("balance");
        balanceField.setAccessible(true);
        int actualBalance = balanceField.getInt(freshTarget);

        assertEquals(9500, actualBalance, "Withdrawal should be capped to 500");
    }

    @Test
    @DisplayName("Test 7: Unregister listener")
    void testUnregisterListener() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        Method method = MonitorTarget.class.getMethod("getName");

        InvocationListener listener = new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                callCount.incrementAndGet();
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        };

        InvocationMonitor.register(method, listener);
        target.getName();
        assertEquals(1, callCount.get());

        InvocationMonitor.unregister(method, listener);
        target.getName();
        assertEquals(1, callCount.get());
    }
}