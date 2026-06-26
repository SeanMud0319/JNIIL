package top.nontage.jniil.test.examples;

import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.monitor.*;
import top.nontage.jniil.test.target.MonitorTarget;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

public class MonitorExample {

    public static void main(String[] args) throws Exception {
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);

        System.out.println("=== InvocationMonitor Example ===\n");

        // ============================================================
        // Example 1: Method-specific monitoring
        // ============================================================
        Method depositMethod = MonitorTarget.class.getMethod("deposit", int.class);
        InvocationMonitor.register(depositMethod, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                System.out.println("[Monitor] deposit() called from: "
                        + callerDetail.getCallerClass().getSimpleName()
                        + "." + callerDetail.getCallerMethodName());
                System.out.println("[Monitor] Deposit amount: " + args[0]);
            }

        });

        System.out.println("--- Example 1: Method-specific monitoring ---");
        MonitorTarget target = new MonitorTarget("Alice", 25, 1000);
        target.deposit(500);

        // ============================================================
        // Example 2: Return value override
        // ============================================================
        Method getBalanceMethod = MonitorTarget.class.getMethod("getBalance");
        InvocationMonitor.register(getBalanceMethod, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                System.out.println("[Monitor] Intercepting getBalance() - returning 9999");
                control.setReturnValue(9999);
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        });

        System.out.println("\n--- Example 2: Return value override ---");
        int balance = target.getBalance();
        System.out.println("Balance: " + balance);

        // ============================================================
        // Example 3: Constructor monitoring
        // ============================================================
        Constructor<?> constructor = MonitorTarget.class.getConstructor(String.class, int.class);
        InvocationMonitor.register(constructor, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                System.out.println("[Monitor] Constructor called! Name: " + args[0] + ", Age: " + args[1]);
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        });

        System.out.println("\n--- Example 3: Constructor monitoring ---");
        MonitorTarget newTarget = new MonitorTarget("Bob", 30);

        // ============================================================
        // Example 4: ClassMatcher with pattern matching
        // ============================================================
        InvocationMonitor.matchClass("top.nontage.jniil.test.target.MonitorTarget")
                .methodNameStartsWith("calc")
                .withListener(new InvocationListener() {
                    @Override
                    public void onInvoke(CallerDetail callerDetail, Object target,
                                         Executable targetMethod,
                                         Object[] args, InvocationControl control) {
                        System.out.println("[Monitor] Matcher hook (calc*): " + targetMethod.getName()
                                + " - doubling return value");
                        if (args.length > 0 && args[0] instanceof Integer) {
                            int base = (int) args[0];
                            control.setReturnValue(base * 2);
                        }
                    }

                    @Override
                    public boolean needsCaller() {
                        return false;
                    }
                });

        InvocationMonitor.applyMatchersToAllLoadedClasses();

        System.out.println("\n--- Example 4: ClassMatcher with wildcard ---");
        int bonus = target.calculateBonus(100, 3);
        System.out.println("Bonus result: " + bonus);

        // ============================================================
        // Example 5: Parameter manipulation
        // ============================================================
        Method withdrawMethod = MonitorTarget.class.getMethod("withdraw", int.class);
        InvocationMonitor.register(withdrawMethod, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                int amount = (int) args[0];
                System.out.println("[Monitor] Withdraw requested: " + amount);
                if (amount > 1000) {
                    System.out.println("[Monitor] Amount too large, reducing to 1000");
                    args[0] = 1000;
                }
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        });

        System.out.println("\n--- Example 5: Parameter manipulation ---");
        MonitorTarget target5 = new MonitorTarget("David", 35, 5000);
        target5.withdraw(2000);

        // ============================================================
        // Example 6: Method cancellation
        // ============================================================
        Method processLoginMethod = MonitorTarget.class.getMethod("processLogin", String.class, String.class);
        InvocationMonitor.register(processLoginMethod, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail callerDetail, Object target,
                                 Executable targetMethod,
                                 Object[] args, InvocationControl control) {
                String username = (String) args[0];
                System.out.println("[Monitor] Login attempt for: " + username);

                if ("blocked".equals(username)) {
                    System.out.println("[Monitor] Blocking login for blocked user!");
                    control.cancel();
                    control.setReturnValue("BLOCKED");
                }
            }

            @Override
            public boolean needsCaller() {
                return false;
            }
        });

        System.out.println("\n--- Example 6: Method cancellation ---");
        String result1 = target.processLogin("admin", "secret");
        System.out.println("Login result: " + result1);
        String result2 = target.processLogin("blocked", "anything");
        System.out.println("Login result: " + result2);

        System.out.println("\n=========================================");
    }
}