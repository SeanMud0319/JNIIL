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

(c) Nontage 2026 All rights reserved.