# JNIIL Bootstrap ClassLoader Deployment

## Overview

In modular architectures (OSGi, JPMS, custom classloader isolation), each module has its own ClassLoader. JNIIL injected across module boundaries fails because:

| Scenario                             | Problem                                  |
|--------------------------------------|------------------------------------------|
| Module A has JNIIL, Module B doesn't | `NoClassDefFoundError` in B              |
| Both modules bundle JNIIL            | Two copies loaded → `ClassCastException` |
| Bidirectional injection              | Type mismatch, cannot cast               |

**Root cause:** JNIIL core classes are loaded multiple times by different ClassLoaders.

**Solution:** Put JNIIL core into Bootstrap ClassLoader (parent of all ClassLoaders) → single shared instance across entire JVM.

---

## Bootstrap Flow

1. **Get Instrumentation**
    - `ATTACH_API`: dynamic attach via Attach API
    - `NATIVE`: obtain via JvmContext (native library)

2. **Define JNIIL into Bootstrap**
    - `Unsafe.defineClass("top.nontage.jniil.JNIIL", ...)`
    - `Unsafe.defineClass("top.nontage.jniil.JNIIL$InjectionOutputConfig", ...)`
    - This ensures JNIIL core exists in Bootstrap before any child ClassLoader can load it

3. **Locate JNIIL JAR**
    - From `CodeSource` of `JNIILBootstrap.class`
    - Fallback to `.m2/repository` or Gradle cache

4. **Filter the JAR**
    - Keep: `functional.internal.*`, `annotations.*`, `interfaces.*`, `top.nontage.relocated.*`, `javassist.*`, `org.objectweb.*`
    - Remove: everything else (accessor, injector implementations, utils, agent, verify, test code)

5. **Append to Bootstrap**
    - `instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(filteredJar))`
    - Bootstrap ClassLoader now searches this JAR when loading classes

6. **Complete**
    - Core JNIIL is now shared across all ClassLoaders

---

## What's in Each ClassLoader

### Bootstrap ClassLoader

- JDK core classes (`java.*`, `javax.*`, `sun.*`)
- `top.nontage.jniil.JNIIL` (via Unsafe)
- `top.nontage.jniil.JNIIL$InjectionOutputConfig` (via Unsafe)
- `top.nontage.jniil.injector.functional.internal.*`
- `top.nontage.jniil.annotations.*`
- `top.nontage.jniil.interfaces.*`
- `top.nontage.relocated.*` (relocated ASM)
- `javassist.*`
- `org.objectweb.*` (original ASM)

### Default / App ClassLoader

- `top.nontage.jniil.accessor.*`
- `top.nontage.jniil.injector.*` (non-functional parts)
- `top.nontage.jniil.agent.*`
- `top.nontage.jniil.utils.*`
- `top.nontage.jniil.verify.*`
- Application code and dependencies

### Module ClassLoaders (A, B, ...)

- Module-specific code
- References JNIIL core from Bootstrap (shared)
- Own copy of JNIIL non-core classes if bundled

---

## Key Design Decisions

### Why `Unsafe.defineClass()` before `appendToBootstrapClassLoaderSearch()`?

`appendToBootstrapClassLoaderSearch()` only affects **future** class loading. If JNIIL was already loaded by a child ClassLoader, it won't be reloaded into Bootstrap. `Unsafe.defineClass()` forces JNIIL into Bootstrap first, ensuring the shared instance exists.

### Why `inject(Object)` instead of `inject(Injectable)`?

If parameter is `Injectable`, method signature resolution requires loading the `Injectable` interface. In Bootstrap environment, `Injectable` may not be in Bootstrap's search path → `NoClassDefFoundError`. Using `Object` defers type checking until method body executes.

### Why doesn't `InjectionUtil` cache `Instrumentation` in a static field?

Static fields bind at class initialization time, triggering premature class loading. This risks JNIIL being loaded by a child ClassLoader. Instead, each method call fetches `Instrumentation` from `JNIIL.getInstrumentation()` at runtime.

---

## Summary

- **Problem:** Multiple ClassLoaders → Multiple JNIIL copies → Type mismatch
- **Solution:** Put JNIIL core in Bootstrap → Single shared instance
- **How:** `Unsafe.defineClass()` + Filtered JAR + `appendToBootstrapClassLoaderSearch()`
- **Result:** All ClassLoaders share the same JNIIL core
- **Key:** `install()` must be called **BEFORE** any JNIIL API usage