package top.nontage.jniil.shadow.metadata;

import top.nontage.jniil.shadow.transformer.ShadowTransformer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A builder-style class for creating a binding between a single shadow class
 * and multiple target class instances or suppliers.
 * <p>
 * This class facilitates the configuration of which target instances should be
 * used when a shadow class's methods or fields are accessed. An instance of
 * {@code MultiBinding} holds one shadow class and a map of target class names
 * to suppliers that provide the actual instances.
 * <p>
 * <b>Supports multiple bindings for the same shadow class to different target types.</b>
 *
 * @see ShadowTransformer#apply(MultiBinding...)
 */
public class MultiBinding {
    private final Class<?> shadowClass;
    private final Map<String, Supplier<Object>> suppliers = new HashMap<>();

    /**
     * Constructs a new MultiBinding for the specified shadow class.
     *
     * @param shadowClass The class annotated with {@code @ShadowOf} that will act as the shadow.
     */
    public MultiBinding(Class<?> shadowClass) {
        this.shadowClass = shadowClass;
    }

    /**
     * Constructs a new MultiBinding with one or more initial target instances.
     *
     * @param shadowClass The shadow class.
     * @param firstTarget The first target instance.
     * @param moreTargets Additional target instances (optional).
     */
    public MultiBinding(Class<?> shadowClass, Object firstTarget, Object... moreTargets) {
        this(shadowClass);
        bind(firstTarget);
        for (Object target : moreTargets) {
            bind(target);
        }
    }

    /**
     * Binds a concrete object instance to its runtime class type.
     * This is a convenience method that infers the target class from the instance.
     *
     * @param instance The non-null object instance to be bound.
     * @return This {@code MultiBinding} instance for method chaining.
     * @throws IllegalArgumentException if the instance is null.
     */
    public MultiBinding bind(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Binding instance cannot be null.");
        }
        return bindInstance(instance);
    }

    /**
     * Private helper to bind an instance by capturing its generic type.
     */
    @SuppressWarnings("unchecked")
    private <T> MultiBinding bindInstance(T instance) {
        return bind((Class<T>) instance.getClass(), instance);
    }

    /**
     * Binds a supplier that provides a target object instance.
     * The target class is inferred from the object returned by the supplier's {@code get()} method.
     *
     * @param <T>      The type of object provided by the supplier.
     * @param supplier The non-null supplier that provides the target instance.
     * @return This {@code MultiBinding} instance for method chaining.
     */
    @SuppressWarnings("unchecked")
    public <T> MultiBinding bind(Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Binding supplier cannot be null.");
        }
        T instance = supplier.get();
        if (instance == null) {
            throw new IllegalArgumentException("Supplier cannot provide a null instance for type inference.");
        }
        return bind((Class<T>) instance.getClass(), supplier);
    }

    /**
     * Binds a concrete object instance to a specific target class type.
     * This is useful when you want to bind an instance of a subclass to a
     * shadow that targets its superclass.
     *
     * @param <T>         The type of the target class.
     * @param targetClass The class type to which the instance should be bound.
     * @param instance    The non-null object instance.
     * @return This {@code MultiBinding} instance for method chaining.
     */
    public <T> MultiBinding bind(Class<T> targetClass, T instance) {
        if (targetClass == null) {
            throw new IllegalArgumentException("Target class cannot be null.");
        }
        if (instance == null) {
            throw new IllegalArgumentException("Binding instance cannot be null.");
        }
        this.suppliers.put(targetClass.getName().replace('.', '/'), () -> instance);
        return this;
    }

    /**
     * Binds a supplier to a specific target class type.
     * This is the most explicit binding method, associating a supplier with a given
     * target class.
     *
     * @param <T>         The type of the target class.
     * @param targetClass The class type to which the supplier is associated.
     * @param supplier    The non-null supplier that provides the target instance.
     * @return This {@code MultiBinding} instance for method chaining.
     */
    @SuppressWarnings("unchecked")
    public <T> MultiBinding bind(Class<T> targetClass, Supplier<T> supplier) {
        if (targetClass == null) {
            throw new IllegalArgumentException("Target class cannot be null.");
        }
        if (supplier == null) {
            throw new IllegalArgumentException("Binding supplier cannot be null.");
        }
        this.suppliers.put(targetClass.getName().replace('.', '/'), (Supplier<Object>) supplier);
        return this;
    }

    /**
     * Binds multiple instances at once.
     *
     * @param instances The instances to bind
     * @return This {@code MultiBinding} instance for method chaining.
     */
    public MultiBinding bindAll(Object... instances) {
        for (Object instance : instances) {
            bind(instance);
        }
        return this;
    }

    /**
     * Binds multiple suppliers at once.
     *
     * @param suppliers The suppliers to bind
     * @return This {@code MultiBinding} instance for method chaining.
     */
    @SafeVarargs
    public final MultiBinding bindAll(Supplier<Object>... suppliers) {
        for (Supplier<Object> supplier : suppliers) {
            bind(supplier);
        }
        return this;
    }

    /**
     * Gets the shadow class associated with this binding.
     */
    public Class<?> getShadowClass() {
        return shadowClass;
    }

    /**
     * Gets the map of target class internal names to their instance suppliers.
     */
    public Map<String, Supplier<Object>> getSuppliers() {
        return suppliers;
    }

    /**
     * Returns the number of bindings in this MultiBinding.
     */
    public int size() {
        return suppliers.size();
    }

    /**
     * Checks if this MultiBinding has any bindings.
     */
    public boolean isEmpty() {
        return suppliers.isEmpty();
    }

    @Override
    public String toString() {
        return "MultiBinding{" +
                "shadowClass=" + shadowClass.getName() +
                ", bindings=" + suppliers.size() +
                '}';
    }
}