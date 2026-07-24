package top.nontage.jniil.test.examples;

import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.accessor.AccessorFactory;
import top.nontage.jniil.test.accessor.api.UserAccessor;
import top.nontage.jniil.test.accessor.target.AccessorTarget;

/**
 * ACCESSOR EXAMPLE
 *
 * <p>This example demonstrates the {@code AccessorFactory} which generates
 * ultra-high-performance accessors at runtime with zero reflection overhead.</p>
 *
 * <p><b>Key features demonstrated:</b></p>
 * <ul>
 *   <li>{@code @Accessor} - Direct field access (getter/setter)</li>
 *   <li>{@code @Invoker} - Method invocation</li>
 *   <li>Both annotations can be mixed in the same interface</li>
 *   <li>Supports static fields and methods via {@code isStatic = true}</li>
 *   <li>Can invoke private methods</li>
 * </ul>
 *
 * <p><b>Static Access Rules:</b></p>
 * <ul>
 *   <li>Interface methods must NOT be static</li>
 *   <li>For @Accessor: use {@code isStatic = true} for static fields</li>
 *   <li>For @Invoker: use {@code isStatic = true} for static methods</li>
 * </ul>
 *
 * <p><b>Performance note:</b></p>
 * Generated accessors achieve direct-call speed, significantly faster than
 * reflection-based approaches.
 *
 * @see UserAccessor
 * @see AccessorFactory
 */
public class AccessorExample {

    public static void main(String[] args) {
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);

        System.out.println("=== Accessor Example ===\n");

        System.out.println("Both @Accessor and @Invoker can be used in the same interface.");
        System.out.println("  - @Accessor → Direct field access (getter/setter)");
        System.out.println("  - @Invoker  → Method invocation");
        System.out.println();
        System.out.println("Static Access Rules:");
        System.out.println("  - Interface methods must NOT be static");
        System.out.println("  - @Accessor: use isStatic = true for static fields");
        System.out.println("  - @Invoker: use isStatic = true for static methods");
        System.out.println();

        AccessorTarget target = new AccessorTarget("Alice", 25, 50000.0);

        UserAccessor accessor = AccessorFactory.getAccessor(target, UserAccessor.class);

        System.out.println("--- @Accessor (Instance Field Access) ---");
        System.out.println("Original: name=" + accessor.getName() +
                ", age=" + accessor.getAge() +
                ", salary=" + accessor.getSalary() +
                ", active=" + accessor.isActive());

        accessor.setName("Bob");
        accessor.setAge(30);
        accessor.setSalary(60000.0);
        accessor.setActive(false);

        System.out.println("Modified: name=" + accessor.getName() +
                ", age=" + accessor.getAge() +
                ", salary=" + accessor.getSalary() +
                ", active=" + accessor.isActive());

        System.out.println("\n--- @Accessor (Static Field Access) ---");
        System.out.println("Note: Interface method is NOT static, but isStatic = true.");
        System.out.println("  getStaticField() = " + accessor.getStaticField());
        accessor.setStaticField("NewStaticValue");
        System.out.println("  After setStaticField(): " + accessor.getStaticField());

        System.out.println("\n--- @Invoker (Instance Method Call) ---");
        String greetResult = accessor.greet("Hello");
        System.out.println("  greet(\"Hello\") = " + greetResult);

        int calcResult = accessor.calculate(10, 20);
        System.out.println("  calculate(10, 20) = " + calcResult);

        System.out.println("  Calling printInfo():");
        accessor.printInfo();

        System.out.println("\n--- @Invoker (Static Method Call) ---");
        System.out.println("Note: Interface method is NOT static, but isStatic = true.");
        System.out.println("  getStaticFieldViaInvoker() = " + accessor.getStaticFieldViaInvoker());
        accessor.setStaticFieldViaInvoker("ViaStaticTarget");
        System.out.println("  After setStaticFieldViaInvoker(): " + accessor.getStaticField());

        System.out.println("\n--- @Invoker (Private Method Call) ---");
        String privateResult = accessor.callPrivateMethod("secret");
        System.out.println("  callPrivateMethod(\"secret\") = " + privateResult);

        String publicResult = accessor.callPrivateMethodViaPublic("viaPublic");
        System.out.println("  callPrivateMethodViaPublic(\"viaPublic\") = " + publicResult);

        int r = accessor.calculate(30, Integer.valueOf(30));
        System.out.println(" calculate(30, Integer.valueOf(30)) = " + r);

        System.out.println("\n=========================================");
    }
}