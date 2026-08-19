# 2026 / 7 / 28
## This is not AI btw
## Accessor 問題及發現
### 問題
發現 Accessor 在 Java22 之後無法突破 Module 隔離，由於主要使用版本都在 8 ~ 17 所以現在才發現，具體問題在 Java22 中 
MagicAccessorImpl 仍然存在，因此 Accessor 會採用 Legacy 模式，但是由於 Module 隔離的關係，負責實作 Accessor Interface 的 Class
會無法繼承 MagicAccessorImpl，並且這似乎得透過 instrumentation 開啟權限，但由於 MagicAccessorImpl 在 unnamed module，倘若開啟權限
會讓整個 default package 都可以存取內部 api，因此我決定在 22 版本就開始用 hidden class，但是原本的 hidden class 設計我沒有想到也有 module 隔離
因此我花了一些時間研究回想當初設計的時候發生的狀況。為何我不使用 IMPL_LOOKUP 而是用 privateLookupIn，而原因在於 IMPL_LOOKUP 的 lookup class
欄位是 Object.class，而 hidden class 要求欄位必須是我 nestmate 的 class，所以解法就出現了，我可以透過 Unsafe 創建一個 IMPL_LOOKUP 並強制把內部欄位的
lookupClass 及 allowedModes 改掉，理論上就可以解決 22 版本以上無法突破 module 的問題。

### 發現
當我嘗試實現這個方法時，我發現我的 UnsafeUtil 沒有辦法強制幫 Lookup 創建實例，因此我打算透過半 Unsafe + Reflection 的方式新建實例並且透過欄位偏移量直接修改
，結果出現了意料之外的錯誤，找不到關鍵的兩個欄位，因此我檢查了我的 JDK 是不是因為快取拿到舊版本的 (欄位重命名)，經過檢查之後我發現欄位名稱沒變，
於是我直接去翻 JDK 源碼，發現了一個很靠北的東西 `Reflection.registerFieldsToFilter(Lookup.class, Set.of("lookupClass", "allowedModes"));`
JDK 把自己的欄位跟方法在反射中隱藏，我原本想直接 patch 這段 code 但是我發現 Reflection 初始化的時候就會把自己加進去隱藏列表了，所以 patch 是行不通的，
所以我又想到我可以透過 IMPL_LOOKUP + VarHandle 直接獲取這段 Field，經過實驗後確實可行，這意味著我可以透過這方式擴充 forceSet/Get。
22 版本以上仍然無法存取在 bootstrap loader 的類，原因: hiddenClass nestmate 相當於創造一個 class 並寄生到目標 class，因此可以直接存取 private 成員，
但是在 bootstrap 的類中無法 implement 來自 app loader 的 accessor interface。解法有幾種，
- 先把 interface define 進 bootstrap : 無法實現，interface 會先被一般 loader 載入，不同 loader 的 class 無法相互 cast。
- 捨棄 interface 改成 MethodHandle 呼叫 : 可行，但這跟我初衷 direct call 有出入，而且這樣得寫三種 fallback。

因此我決定直接放棄 22 以上版本針對 bootstrap loader 的 accessor 支援，反正正常情況也不會有人去存取 bootstrap 的東西，但仍然支援突破 module 隔離。

# 2026 / 8 / 14
## This is not AI SON
## InvocationMonitor 問題 & 解法
### 問題
monitor 無法 hook bootstrap loader 的東西，原因也很簡單，monitor 模組所在 loader 跟 java.base 等在不同 loader.

### 解法
由於 monitor 模組的用法就只有使用 InvocationMonitor 以及其他 api 不像其他模組一樣要處理 interface之類的，所以直接把 monitor 模組放到 bootstrap
loader 加載即可，但因為 monitor 本身需要使用到 UnsafeUtil InjectionUtil 所以需要修改 BootstrapJarBuilder 讓它把這些東西都包進去 bootstrap。

## Accessor 無法存取位於 bootstrap 類別問題 & 解法 回覆自(2026 / 7 / 28)
### 問題
當時卡在生成的 class 無法 implement 來自 app loader 的 accessor interface，由於這是由使用者自行建立的，所以沒辦法做甚麼提前把 interface
加到 bootstrap，因為當你直接使用到 class 的時候就會被 ClassLoader 載入，同時也不可能讓使用者在那邊自己註冊 class 之類的，唯一合理的作法是干預
bootstrap loader 的的尋找邏輯，但很可惜的這種作法會影響效能，以及 bootstrap loader 在 Java 是沒有對應的物件，它只存在於 Native，所以這條路也行不通。

### 解法
跳脫原本的干預搜尋邏輯這個框架思考，(actually我從放棄到想到解法經過了快一個月，所以我實際上是個dumbass)，如果想干預 bootstrap 的搜尋邏輯是不可能的，
所以改成干預一般 loader 的定義邏輯，之前提到你只要直接使用的一個 class 的任何東西他就會觸發連鎖載入，所以只要攔截載入邏輯判斷該 class 的各種資料是否是
我的目標直接用 Unsafe 定義去 bootstrap 就可以解決這問題。

同理我有想到那是否代表針對 JDK-8263089 的注入是否也可以用這種方式解決而不用修改 ClassLoader 邏輯，但很可惜我測試過後問題不是這種方式能解決的。
Java ClassLoader 的搜尋邏輯是透過遞迴從當前 Class 開始詢問註冊表是否有註冊過該 class 如果有的話返回，否則往 parent loader 問，一路問到 bootstrap
後會從 bootstrap 開始看當前 loader 的搜尋環境有沒有這 class 有的話定義這次遞迴所在的 loader，並逐層返回重複剛剛的邏輯，而  JDK-8263089 的問題在於
它的註冊表有註冊過，但是它的搜尋卻不會去找，所以代表它不會去做 "定義" 這件事，最後會直接拋出 NoClassDefFound，(我那時候花了兩天在不知道 JDK-8263089 的情況下研究找到這問題)
所以結論就是目前針對 ClassLoader的 patch 只能保持原樣。

### 擴展發現
我想透過 transformer 去攔截 define，測試過後確定可以抓到 class 第一次被 define，但是沒有辦法中斷 define 流程，所以會變成 bootstrap 有一份，
原 loader 也有一份，所以透過 transform 這招是行不通的，還是得走 bytecode 修改。

# 2026 / 8 / 18
## Still not AI
## Unsafe 演變與限制 & 解法
## 演變 OpenJDK 7 ~ 28 (2026 / 8 / 18 最新 Tag)
- 2002年 Unsafe 誕生 ~ JDK8 這階段 - Unsafe 內部直接呼叫 native method
- JDK9 ~ JDK22 Unsafe 變成空殼，內部改呼叫 jdk.internal.Unsafe
- JDK23 Unsafe 新增限制參數 `--sun-misc-unsafe-memory-access=<value>` Unsafe 內部多了 MemoryAccessOption 
參數有 ALLOW, WARN, DEBUG, DENY 其中 WARN 會在第一次警告之後就不警告 DENY 會直接拋出 UnsupportedOperationException，
  從這時候開始 Unsafe 只要是關於直接或間接操作記憶體的 method 都會呼叫 beforeMemoryAccess 這個方法會直接被 force inline 所以沒有操作空間 也沒有意義
  beforeMemoryAccess 會再去呼叫  beforeMemoryAccessSlow 這邊會獲取 MEMORY_ACCESS_OPTION 這個參數透過 --sun-misc-unsafe-memory-access=<value> 設定 如果沒設定在 JDK23 預設為 ALLOW
- JDK24 ~ 28 MEMORY_ACCESS_OPTION 預設被改成 WARN 所以會跳警告 未來應該會變 DENY。

## 限制
目前如果沒有特別去動 MEMORY_ACCESS_OPTION 他最多是跳警告，但未來也有可能預設是 DENY 或是 Unsafe 被刪掉，不過到那時候應該也要一些時間，其實理論上
我可以用 MethodHandles 之類的 API，但是我想保持最高權限。

## 解法
我做了 UnsafeTransformer 它會在 Instrumentation 生成後第一時間檢查 Java 版本是否大於 23 以及檢查版本 install 的輸入參數，可以選擇保持原樣，
或是隱藏 Unsafe 警告訊息，雖然那也只會出現一次，或是繞過 DENY 限制，也就是說未來如果進入到了預設 DENY JNIIL 理論上可以繞過這項限制。

# 2026 / 8 / 20
## 0 ai
## Dynamic loading of agents 警告
這個警告訊息我以為是在 native 輸出的，不過我去翻 jdk source code 後發現，它是在 jdk21之後被加到 InstrumentationImpl 的 constructor 並直接透過 System.err.println()
輸出警告，所以只要覆蓋 System.err 就可以隱藏警告了。

另外針對 System.load() 所產生的警告目前沒有解法，因為它是在 Module 內部檢查並透過 JavaLangAccess 保留的最初 System.err 輸出的，
呼叫 System.load() 是為了獲取 Instrumentation 也就是在這階段我沒有任何手段可以 retransform，在這階段使用 Unsafe 也會產生警告訊息，所以透過記憶體偏移量操作
System, Module, PrintStream, FilterOutputStream 等都不行，我也試過直接追蹤到 System.load() 的實作，基本上都一樣，所以這個警告訊息目前無法透過啟動參數以外的方式關掉

(c) Nontage 2026 All rights reserved.