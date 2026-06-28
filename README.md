![CI](https://github.com/SeanMud0319/JNIIL/actions/workflows/ci.yml/badge.svg)
# JNIIL - Java Non-intrusive Instrumentation Library

## Overview

JNIIL is a runtime dynamic instrumentation library for Java. It provides powerful bytecode manipulation capabilities with a clean, developer-friendly API. The library enables runtime method modification, AOP-style method interception, and zero-overhead reflection replacement.

---

## Java Version Supported

| Version | Status |
|:--------|:------:|
| Java 8  |   ✅    |
| Java 11 |   ✅    |
| Java 17 |   ✅    |
| Java 21 |   ✅    |
| Java 22 |   ✅    |
| Java 25 |   ✅    |


## Table of Contents

- [Core Features](#core-features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Core Components](#core-components)
    - [Injector](#injector)
        - [StandardMethodInjector](#standardmethodinjector)
        - [InstructionInjector](#instructioninjector)
        - [FunctionalInjector](#functionalinjector)
    - [Monitor](#monitor)
    - [Accessor](#accessor)
- [Project Structure](#project-structure)
- [Build & Test](#build--test)
- [License](#license)

---

## Core Features

| Feature      | Description                                                                         |
|--------------|-------------------------------------------------------------------------------------|
| **Inject**   | Dynamically modify method bodies at runtime                                         |
| **Monitor**  | Intercept method entries to retrieve caller and method metadata (AOP-style)         |
| ~~Shadow~~   | ~~Create shadow classes that synchronize with actual runtime classes~~ (Deprecated) |
| **Accessor** | Ultra-high-performance reflection replacement with direct-call speed                |

---

## Installation
You need to compile [JvmContext](https://github.com/SeanMud0319/JvmContext) first, then use publishToMavenLocal.

### Gradle

```gradle
repositories {
    mavenLocal()
}

dependencies {
    implementation("top.nontage:jniil:1.0-SNAPSHOT")
}
```

### Maven

```xml
<dependency>
    <groupId>top.nontage</groupId>
    <artifactId>jniil</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Quick Start

```java
import top.nontage.jniil.agent.JNIILBootstrap;

public class Main {
    public static void main(String[] args) {
        // Mandatory initialization
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);
        
        // Your code here...
    }
}
```

---

## Core Components

### Injector

Modifies method bodies at runtime. JNIIL provides three types of injectors based on your technical needs.

#### StandardMethodInjector

**Powered by Javassist.** Perfect for line-number-based, straightforward injections. Less suited for highly complex method structures.

**Example:**

```java
import javassist.CtMethod;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.interfaces.Injectable;

public class MyInjector implements Injectable {

    @InjectMethodInfo(
        targetType = MyTarget.class,
        targetMethodName = "printInfo"
    )
    @Before
    @Override
    public String getInjectSourceCode(CtMethod ctMethod) {
        return "System.out.println(\"Hello World\");";
    }
}

// Usage
StandardMethodInjector injector = new StandardMethodInjector();
injector.inject(new MyInjector());
```

**When to use:** Simple injections, prefer writing raw Java source strings.

---

#### InstructionInjector

**Fully built on top of ASM.** Allows precise, low-level modifications at any arbitrary bytecode instruction. Requires a solid understanding of JVM bytecode.

**Example:**

```java
import org.objectweb.asm.tree.*;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.injector.insn.InsnContext;
import top.nontage.jniil.interfaces.InsnInjectable;

import static org.objectweb.asm.Opcodes.*;

public class MyInstructionInjector implements InsnInjectable {

    @InjectMethodInfo(
        targetType = MyTarget.class,
        targetMethodName = "process",
        targetMethodParamTypes = {int.class}
    )
    @At(opcode = BIPUSH, shiftAfter = true)
    @Override
    public InsnList apply(InsnContext ctx, InsnList insns) {
        insns.add(new InsnNode(POP));
        insns.add(new VarInsnNode(BIPUSH, 5));
        return insns;
    }
}
```

**When to use:** Need precise control over individual bytecode instructions, understand JVM opcodes.

---

#### FunctionalInjector

**(Highly Recommended)** Merges the strengths of both worlds. An event-driven injector that simplifies data extraction and supports seamless injection points based on either line numbers or specific instruction blocks.

**Example:**

```java
import top.nontage.jniil.annotations.*;
import top.nontage.jniil.injector.functional.MethodInfo;
import top.nontage.jniil.interfaces.FunctionalInjectable;

public class MyHooks implements FunctionalInjectable {

    @InjectMethodInfo(
        targetType = MyTarget.class,
        targetMethodName = "login",
        targetMethodParamTypes = {String.class}
    )
    @Before
    public static void beforeLogin(MethodInfo info) {
        String password = info.getArgument(0);
        System.out.println("Password: " + password);
    }

    @InjectMethodInfo(
        targetType = MyTarget.class,
        targetMethodName = "login",
        targetMethodParamTypes = {String.class}
    )
    @At(line = 24, shiftAfter = true)
    @Capture({"attempts", "matched"})
    public static void atLoginState(MethodInfo info) {
        int attempts = info.getLocal("attempts");
        boolean matched = info.getLocal("matched");
        System.out.println("Attempts: " + attempts + ", Matched: " + matched);
    }
}
```

**When to use:** Recommended for most use cases. Event-driven, easy data extraction, supports flow control.

**Key Features:**
- `@Before` - Method entry interception
- `@After` - Method exit interception
- `@At(line)` - Specific source line interception
- `@At(opcode)` - Specific bytecode instruction interception
- `@Capture` - Extract local variables by name or slot index
- `info.cancel()` - Cancel method execution
- `info.setReturnValue()` - Override return value

---

### Monitor

**AOP-style runtime method interception.** Unlike injectors which modify bytecode once at startup, Monitor inserts a dispatch call at method entry that routes to registered listeners.

**⚠️ Warning:** Do NOT use both Injector and Monitor on the same method. They both modify bytecode and may interfere.

**Example:**

```java
import top.nontage.jniil.monitor.*;

public class MonitorDemo {
    public static void main(String[] args) throws Exception {
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);

        Method method = MyTarget.class.getMethod("deposit", int.class);
        InvocationMonitor.register(method, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail caller, Object target,
                                 Executable method, Object[] args,
                                 InvocationControl control) {
                System.out.println("Deposit: " + args[0]);
            }
        });
    }
}
```

**Features:**
- Caller inspection (`CallerDetail`)
- Argument inspection and modification
- Execution cancellation (`control.cancel()`)
- Return value override (`control.setReturnValue()`)
- Constructor interception
- Pattern matching with `ClassMatcher`

---

### Accessor

**Zero-overhead reflection replacement.** Generates ultra-high-performance accessors at runtime with direct-call speed.

**Example:**

```java
import top.nontage.jniil.annotations.Accessor;
import top.nontage.jniil.annotations.Invoker;

public interface UserAccessor {
    @Accessor("name")
    String getName();
    
    @Accessor("name")
    void setName(String name);
    
    @Invoker("greet")
    String greet(String greeting);
}

// Usage
UserAccessor accessor = AccessorFactory.getAccessor(target, UserAccessor.class);
String name = accessor.getName();
accessor.setName("Bob");
String result = accessor.greet("Hello");
```

**Key Features:**
- `@Accessor` - Direct field read/write (getter/setter)
- `@Invoker` - Method invocation
- Both annotations can be mixed in the same interface
- Supports instance and static fields/methods via `isStatic` flag
- Can invoke private methods
- Accessor caching per instance
- Zero reflection overhead - direct-call speed

**Static Access Rules:**
- Interface methods must NOT be static
- Use `isStatic = true` for static fields or methods
- Mismatch between `isStatic` and actual target throws an exception

---

## Project Structure

```
src/
├── main/java/top/nontage/jniil/
│   ├── accessor/          # AccessorFactory and related classes
│   ├── agent/             # JNIILBootstrap - framework initialization
│   ├── annotations/       # @Accessor, @Invoker, @Before, @After, @At, @Capture
│   ├── injector/          # Injector implementations
│   │   ├── functional/    # FunctionalInjector (event-driven)
│   │   ├── insn/          # InstructionInjector (ASM-based)
│   │   └── StandardMethodInjector (Javassist-based)
│   ├── monitor/           # InvocationMonitor (AOP-style interception)
│   └── utils/             # Utility classes
│
└── test/java/top/nontage/jniil/test/
    ├── accessor/          # Accessor tests and targets
    ├── examples/          # Manual demo examples
    ├── injector/          # Injector test implementations
    └── target/            # Target classes for testing
```

---

## Build & Test

### Gradle Tasks

```bash
# Run all tests
./gradlew test

# Run specific component tests
./gradlew testStandard
./gradlew testInstruction
./gradlew testFunctional 
./gradlew testMonitor    
./gradlew testAccessor   

# Run manual examples
./gradlew runStandard    
./gradlew runInstruction 
./gradlew runFunctional  
./gradlew runMonitor     
./gradlew runAccessor    

# Build shadow JAR
./gradlew shadowJar

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

### Requirements

- Java 8 or higher
- Gradle 8.0 or higher

---

## License

Apache License 2.0

---

# 中文版

---

## 概述

JNIIL 是一個 Java 執行時期動態 Instrumentation 函式庫。它提供強大的位元組碼操作能力，並具備乾淨、開發者友善的 API。該函式庫支援執行時期方法修改、AOP 風格的方法攔截，以及零開銷的反射替代方案。

---

## Java 支援版本

| 版本      | 測試狀態 |
|:--------|:----:|
| Java 8  |  ✅   |
| Java 11 |  ✅   |
| Java 17 |  ✅   |
| Java 21 |  ✅   |
| Java 22 |  ✅   |
| Java 25 |  ✅   |

## 目錄

- [核心功能](#核心功能)
- [安裝](#安裝)
- [快速入門](#快速入門)
- [核心元件](#核心元件)
    - [Injector](#injector-1)
        - [StandardMethodInjector](#standardmethodinjector-1)
        - [InstructionInjector](#instructioninjector-1)
        - [FunctionalInjector](#functionalinjector-1)
    - [Monitor](#monitor-1)
    - [Accessor](#accessor-1)
- [專案結構](#專案結構)
- [建置與測試](#建置與測試)
- [授權條款](#授權條款)

---

## 核心功能

| 功能           | 說明                           |
|--------------|------------------------------|
| **Inject**   | 動態修改方法本體                     |
| **Monitor**  | 攔截方法進入點以取得呼叫者與方法中繼資料（AOP 風格） |
| ~~Shadow~~   | ~~建立與實際執行時期類別同步的影子類別~~（已棄用）  |
| **Accessor** | 超高效能反射替代方案，達到直接呼叫速度          |

---

## 安裝

### Gradle

```gradle
dependencies {
    implementation("top.nontage:jniil:1.0-SNAPSHOT")
}
```

### Maven

```xml
<dependency>
    <groupId>top.nontage</groupId>
    <artifactId>jniil</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## 快速入門

```java
import top.nontage.jniil.agent.JNIILBootstrap;

public class Main {
    public static void main(String[] args) {
        // 強制初始化
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);
        
        // 你的程式碼...
    }
}
```

---

## 核心元件

### Injector

在執行時期修改方法本體。JNIIL 根據你的技術需求提供三種注入器。

#### StandardMethodInjector

**基於 Javassist。** 非常適合基於行號的簡單注入。不適合處理高度複雜的方法結構。

**範例：**

```java
import javassist.CtMethod;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.interfaces.Injectable;

public class MyInjector implements Injectable {

    @InjectMethodInfo(
        targetType = MyTarget.class,
        targetMethodName = "printInfo"
    )
    @Before
    @Override
    public String getInjectSourceCode(CtMethod ctMethod) {
        return "System.out.println(\"Hello World\");";
    }
}

// 使用方式
StandardMethodInjector injector = new StandardMethodInjector();
injector.inject(new MyInjector());
```

**適用場景：** 簡單注入，偏好撰寫原始 Java 字串。

---

#### InstructionInjector

**完全基於 ASM。** 允許在任何任意位元組碼指令進行精確的低階修改。需要對 JVM 位元組碼有紮實的理解。

**範例：**

```java
import org.objectweb.asm.tree.*;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.injector.insn.InsnContext;
import top.nontage.jniil.interfaces.InsnInjectable;

import static org.objectweb.asm.Opcodes.*;

public class MyInstructionInjector implements InsnInjectable {

    @InjectMethodInfo(
        targetType = MyTarget.class,
        targetMethodName = "process",
        targetMethodParamTypes = {int.class}
    )
    @At(opcode = BIPUSH, shiftAfter = true)
    @Override
    public InsnList apply(InsnContext ctx, InsnList insns) {
        insns.add(new InsnNode(POP));
        insns.add(new VarInsnNode(BIPUSH, 5));
        return insns;
    }
}
```

**適用場景：** 需要精確控制個別位元組碼指令，理解 JVM 運算碼。

---

#### FunctionalInjector

**（強烈推薦）** 融合兩者的優點。事件驅動的注入器，簡化資料提取，並支援基於行號或特定指令區塊的無縫注入點。

**範例：**

```java
import top.nontage.jniil.annotations.*;
import top.nontage.jniil.injector.functional.MethodInfo;
import top.nontage.jniil.interfaces.FunctionalInjectable;

public class MyHooks implements FunctionalInjectable {

    @InjectMethodInfo(
        targetType = MyTarget.class,
        targetMethodName = "login",
        targetMethodParamTypes = {String.class}
    )
    @Before
    public static void beforeLogin(MethodInfo info) {
        String password = info.getArgument(0);
        System.out.println("密碼: " + password);
    }

    @InjectMethodInfo(
        targetType = MyTarget.class,
        targetMethodName = "login",
        targetMethodParamTypes = {String.class}
    )
    @At(line = 24, shiftAfter = true)
    @Capture({"attempts", "matched"})
    public static void atLoginState(MethodInfo info) {
        int attempts = info.getLocal("attempts");
        boolean matched = info.getLocal("matched");
        System.out.println("嘗試次數: " + attempts + ", 是否匹配: " + matched);
    }
}
```

**適用場景：** 推薦大多數使用案例。事件驅動、易於提取資料、支援流程控制。

**主要功能：**
- `@Before` - 方法進入點攔截
- `@After` - 方法出口攔截
- `@At(line)` - 特定原始碼行攔截
- `@At(opcode)` - 特定位元組碼指令攔截
- `@Capture` - 按名稱或 slot 索引提取區域變數
- `info.cancel()` - 取消方法執行
- `info.setReturnValue()` - 覆寫回傳值

---

### Monitor

**AOP 風格的執行時期方法攔截。** 與 Injector（在啟動時一次性修改位元組碼）不同，Monitor 在方法進入點插入 dispatch 調用，路由到已註冊的監聽器。

**⚠️ 警告：** 不建議對同一個方法同時使用 Injector 和 Monitor。兩者都會修改位元組碼，可能互相干擾。

**範例：**

```java
import top.nontage.jniil.monitor.*;

public class MonitorDemo {
    public static void main(String[] args) throws Exception {
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);

        Method method = MyTarget.class.getMethod("deposit", int.class);
        InvocationMonitor.register(method, new InvocationListener() {
            @Override
            public void onInvoke(CallerDetail caller, Object target,
                                 Executable method, Object[] args,
                                 InvocationControl control) {
                System.out.println("存款: " + args[0]);
            }
        });
    }
}
```

**功能：**
- 呼叫者檢查（`CallerDetail`）
- 參數檢查與修改
- 執行取消（`control.cancel()`）
- 回傳值覆寫（`control.setReturnValue()`）
- 建構子攔截
- 使用 `ClassMatcher` 進行模式匹配

---

### Accessor

**零開銷的反射替代方案。** 在執行時期產生超高效能的 accessor，達到直接呼叫速度。

**範例：**

```java
import top.nontage.jniil.annotations.Accessor;
import top.nontage.jniil.annotations.Invoker;

public interface UserAccessor {
    @Accessor("name")
    String getName();
    
    @Accessor("name")
    void setName(String name);
    
    @Invoker("greet")
    String greet(String greeting);
}

// 使用方式
UserAccessor accessor = AccessorFactory.getAccessor(target, UserAccessor.class);
String name = accessor.getName();
accessor.setName("Bob");
String result = accessor.greet("Hello");
```

**主要功能：**
- `@Accessor` - 直接欄位讀寫（getter/setter）
- `@Invoker` - 方法呼叫
- 兩個註解可以在同一個介面中混合使用
- 透過 `isStatic` 旗標支援實例和靜態欄位/方法
- 可以呼叫私有方法
- 每個實例的 Accessor 快取
- 零反射開銷 - 直接呼叫速度

**靜態存取規則：**
- 介面方法不能是 `static`
- 靜態欄位或方法使用 `isStatic = true`
- `isStatic` 與實際目標不符時會拋出例外

---

## 專案結構

```
src/
├── main/java/top/nontage/jniil/
│   ├── accessor/          # AccessorFactory 及相關類別
│   ├── agent/             # JNIILBootstrap - 框架初始化
│   ├── annotations/       # @Accessor, @Invoker, @Before, @After, @At, @Capture
│   ├── injector/          # Injector 實作
│   │   ├── functional/    # FunctionalInjector（事件驅動）
│   │   ├── insn/          # InstructionInjector（基於 ASM）
│   │   └── StandardMethodInjector（基於 Javassist）
│   ├── monitor/           # InvocationMonitor（AOP 風格攔截）
│   └── utils/             # 工具類別
│
└── test/java/top/nontage/jniil/test/
    ├── accessor/          # Accessor 測試與目標
    ├── examples/          # 手動示範範例
    ├── injector/          # Injector 測試實作
    └── target/            # 測試用的目標類別
```

---

## 建置與測試

### Gradle 任務

```bash
# 執行所有測試
./gradlew test

# 執行特定元件的測試
./gradlew testStandard  
./gradlew testInstructio
./gradlew testFunctional
./gradlew testMonitor   
./gradlew testAccessor  

# 執行手動範例
./gradlew runStandard   
./gradlew runInstruction
./gradlew runFunctional 
./gradlew runMonitor    
./gradlew runAccessor   

# 建置 Shadow JAR
./gradlew shadowJar

# 發布到本地 Maven 倉庫
./gradlew publishToMavenLocal
```

### 需求

- Java 8 或更高版本
- Gradle 8.0 或更高版本

---

## 授權條款

Apache License 2.0
