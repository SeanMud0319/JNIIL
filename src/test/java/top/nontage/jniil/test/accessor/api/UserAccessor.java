package top.nontage.jniil.test.accessor.api;

import top.nontage.jniil.annotations.Accessor;
import top.nontage.jniil.annotations.Invoker;

/**
 * ACCESSOR + INVOKER HYBRID INTERFACE
 *
 * <p>This interface demonstrates that {@code @Accessor} and {@code @Invoker}
 * can be used together in the same interface.</p>
 *
 * <p><b>@Accessor</b> - Direct field read/write (getter/setter):</p>
 * <ul>
 *   <li>Maps interface methods directly to target object fields</li>
 *   <li>No actual method invocation occurs - field is accessed directly</li>
 *   <li>Supports both instance and static fields</li>
 *   <li>Use {@code isStatic} to declare whether the target field is static</li>
 * </ul>
 *
 * <p><b>@Invoker</b> - Method invocation:</p>
 * <ul>
 *   <li>Calls the actual method on the target object</li>
 *   <li>Supports both instance and static methods</li>
 *   <li>Can invoke private methods (accessible via the accessor)</li>
 *   <li>Use {@code isStatic} to declare whether the target method is static</li>
 * </ul>
 *
 * <p><b>IMPORTANT - Static Access Rules:</b></p>
 * <ul>
 *   <li>Interface methods themselves must NOT be declared static</li>
 *   <li>AccessorFactory generates instance methods that delegate to static targets</li>
 *   <li>For @Accessor: set {@code isStatic = true} if the target field is static</li>
 *   <li>For @Invoker: set {@code isStatic = true} if the target method is static</li>
 *   <li>If {@code isStatic} does not match the actual target, an exception is thrown</li>
 * </ul>
 *
 * <p><b>Why not static interface methods?</b></p>
 * Interface static methods cannot be overridden by the generated proxy class.
 * The AccessorFactory creates instance proxies, so all methods must be instance methods.
 *
 * @see top.nontage.jniil.annotations.Accessor
 * @see top.nontage.jniil.annotations.Invoker
 * @see top.nontage.jniil.accessor.AccessorFactory
 */
public interface UserAccessor {

    // ============================================================
    // @Accessor - Instance field access (isStatic = false)
    // ============================================================

    @Accessor(value = "name")
    String getName();

    @Accessor(value = "name")
    void setName(String name);

    @Accessor(value = "age")
    int getAge();

    @Accessor(value = "age")
    void setAge(int age);

    @Accessor(value = "salary")
    double getSalary();

    @Accessor(value = "salary")
    void setSalary(double salary);

    @Accessor(value = "active")
    boolean isActive();

    @Accessor(value = "active")
    void setActive(boolean active);

    // ============================================================
    // @Accessor - Static field access (isStatic = true)
    // Note: The interface method is NOT static.
    // The target field (staticField) IS static in AccessorTarget.
    // isStatic = true tells the generator to use GETSTATIC/PUTSTATIC.
    // ============================================================

    @Accessor(value = "staticField", isStatic = true)
    String getStaticField();

    @Accessor(value = "staticField", isStatic = true)
    void setStaticField(String value);

    // ============================================================
    // @Invoker - Instance method invocation (isStatic = false)
    // ============================================================

    @Invoker(value = "greet")
    String greet(String greeting);

    @Invoker(value = "calculate")
    int calculate(int a, int b);

    @Invoker(value = "printInfo")
    void printInfo();

    // ============================================================
    // @Invoker - Static method invocation (isStatic = true)
    // Note: The interface method is NOT static.
    // The target method (getStaticField) IS static in AccessorTarget.
    // isStatic = true tells the generator to use INVOKESTATIC.
    // ============================================================

    @Invoker(value = "getStaticField", isStatic = true)
    String getStaticFieldViaInvoker();

    @Invoker(value = "setStaticField", isStatic = true)
    void setStaticFieldViaInvoker(String value);

    // ============================================================
    // @Invoker - Private method invocation
    // Private methods are instance methods, so isStatic = false (default)
    // ============================================================

    @Invoker(value = "privateMethod")
    String callPrivateMethod(String input);

    @Invoker(value = "callPrivateMethod")
    String callPrivateMethodViaPublic(String input);
}