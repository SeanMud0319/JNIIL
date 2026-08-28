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

```mermaid
flowchart TD
    Start(["Start: Call JNIILBootstrap.install()"]) --> CheckInst{Instrumentation<br/>already set?}
    CheckInst -->|Yes| Done([Complete])
    CheckInst -->|No| Mode{Select Mode}
    
    Mode -->|ATTACH_API| AttachSelf[Dynamic Self-Attach<br/>VirtualMachine.attach]
    Mode -->|NATIVE| NativeGet[Get Instrumentation<br/>via JvmContext]
    
    AttachSelf --> GenAgent[Generate Temp Agent JAR<br/>TempAgent.class]
    GenAgent --> LoadAgent[Load Agent<br/>Obtain Instrumentation]
    LoadAgent --> GotInst[Instrumentation Instance<br/>Acquired]
    NativeGet --> GotInst
    
    GotInst --> CheckLoad{JNIIL already<br/>in Bootstrap?}
    CheckLoad -->|Yes| Done
    CheckLoad -->|No| DefineCore[Unsafe.defineClass<br/>Define Core Classes to Bootstrap]
    
    DefineCore --> DefineDetail["Define Classes:<br/>• JNIIL<br/>• JNIIL$InjectionOutputConfig<br/>• MethodInfo<br/>• InsnContext"]
    
    DefineDetail --> LocateJar[Locate JNIIL JAR]
    LocateJar --> LocateDetail["Search Order:<br/>1. CodeSource of JNIILBootstrap.class<br/>2. .m2/repository<br/>3. Gradle cache"]
    
    LocateDetail --> FilterJar[Filter JAR Contents]
    FilterJar --> FilterDetail["Keep:<br/>• functional.internal.*<br/>• annotations.*<br/>• interfaces.*<br/>• top.nontage.relocated.*<br/>• javassist.*<br/>• org.objectweb.*<br/><br/>Remove:<br/>• accessor.*<br/>• injector.* (non-functional)<br/>• agent.*<br/>• utils.*<br/>• verify.*"]
    
    FilterDetail --> AppendBootstrap[instrumentation.appendToBootstrapClassLoaderSearch<br/>Append Filtered JAR]
    
    AppendBootstrap --> HandleUnsafe{JDK23+<br/>&& hiddenWarning?}
    HandleUnsafe -->|Yes| PatchUnsafe[UnsafeTransformer<br/>Modify Unsafe class<br/>Bypass memory access restrictions]
    HandleUnsafe -->|No| SetInst
    
    PatchUnsafe --> SetInst["Set Instrumentation<br/>JNIIL.setInstrumentation()"]
    
    SetInst --> Verify["Verify & Initialize<br/>JNIILPokaYoke.verifyAndInitialize()"]
    Verify --> CheckUnsafe{Failed &&<br/>JDK23+?}
    CheckUnsafe -->|Yes| ThrowDeny[Throw Unsafe Restricted Exception<br/>Suggest forceEnableUnsafe]
    CheckUnsafe -->|No| Done
    
    ThrowDeny --> Done
    
    Done --> End([End])
```

---

## ClassLoader Architecture Flow

```mermaid
flowchart LR
    subgraph Phase1["Phase 1: Startup"]
        A[JNIILBootstrap<br/>Loaded by AppClassLoader]
    end
    
    subgraph Phase2["Phase 2: Core Definition"]
        B[Unsafe.defineClass<br/>Define JNIIL Core Classes]
        C[Classes Enter<br/>Bootstrap ClassLoader]
    end
    
    subgraph Phase3["Phase 3: JAR Filtering & Appending"]
        D[Original JNIIL JAR]
        E[Filter<br/>Remove Non-Core Classes]
        F[Filtered JAR]
        G[appendToBootstrap<br/>ClassLoaderSearch]
    end
    
    subgraph Phase4["Phase 4: Final State"]
        H[Bootstrap ClassLoader]
        I[AppClassLoader]
        J[Module A<br/>ClassLoader]
        K[Module B<br/>ClassLoader]
    end
    
    A --> B
    B --> C
    D --> E --> F --> G
    G --> H
    H -->|Share Core| I
    H -->|Share Core| J
    H -->|Share Core| K
    
    I -.->|Load Non-Core<br/>accessor, utils, agent| D
```

---

## ClassLoader Responsibility Matrix

```mermaid
flowchart TB
    subgraph Bootstrap["Bootstrap ClassLoader (Shared)"]
        B1["JDK Core Classes<br/>(java.*, javax.*, sun.*)"]
        B2["JNIIL Core Classes<br/>(Defined via Unsafe)"]
        B3["functional.internal.*"]
        B4["annotations.*, interfaces.*"]
        B5["monitor.*"]
        B6["top.nontage.relocated.*<br/>(Relocated ASM)"]
        B7["javassist.*, org.objectweb.*"]
    end
    
    subgraph App["App / System ClassLoader"]
        A1["Application Code"]
        A2["accessor.*"]
        A3["injector.* (non-functional)"]
        A4["agent.*, utils.*, verify.*"]
        A5["Other Dependencies"]
    end
    
    subgraph ModuleA["Module A ClassLoader"]
        M1["Module A Business Code"]
        M2["JNIIL Core ← References from Bootstrap"]
        M3["Module A's Own JNIIL<br/>Non-Core Classes (if bundled)"]
    end
    
    subgraph ModuleB["Module B ClassLoader"]
        N1["Module B Business Code"]
        N2["JNIIL Core ← References from Bootstrap"]
        N3["Module B's Own JNIIL<br/>Non-Core Classes (if bundled)"]
    end
    
    Bootstrap -.->|Shared| ModuleA
    Bootstrap -.->|Shared| ModuleB
    Bootstrap -->|Parent| App
```

---

## Key Design Decisions Flowchart

```mermaid
flowchart LR
    subgraph Problem["Root Problem"]
        P1["Multiple ClassLoaders<br/>Load JNIIL Independently"]
        P2["Type Mismatch<br/>ClassCastException"]
    end
    
    subgraph Solution["Solution Strategy"]
        S1["Unsafe.defineClass()<br/>Pre-define to Bootstrap"]
        S2["Filter JAR<br/>Keep Only Core Classes"]
        S3["appendToBootstrap<br/>ClassLoaderSearch"]
    end
    
    subgraph Result["Final Result"]
        R1["Single JNIIL Instance<br/>Shared Across All ClassLoaders"]
        R2["Cross-Module Injection<br/>Type Consistency Guaranteed"]
    end
    
    P1 --> P2
    P2 --> S1 --> S2 --> S3
    S3 --> R1 --> R2
```

---

## What's in Each ClassLoader

### Bootstrap ClassLoader

- JDK core classes (`java.*`, `javax.*`, `sun.*`)
- `top.nontage.jniil.JNIIL` (via Unsafe)
- `top.nontage.jniil.JNIIL$InjectionOutputConfig` (via Unsafe)
- `top.nontage.jniil.injector.functional.internal.*`
- `top.nontage.jniil.annotations.*`
- `top.nontage.jniil.interfaces.*`
- `top.nontage.jniil.monitor.*`
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