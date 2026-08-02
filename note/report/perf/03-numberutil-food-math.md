# Round 3 — NumberUtil / FoodEffect 数学与分配清理

> 多轮性能优化第 3 轮。覆盖：`NumberUtil.asRomanNumeral`、`roundToPrecision`、`FoodEffect.teleport` 的
> 循环不变量。**诚实定位**：前两者为注册期路径（现实影响有限），后者为运行期路径（每次食用传送食物）。
> 红线：结果恒等（已断言核对）。总览见 [PROGRESS.md](PROGRESS.md)。

## 1. 改动

### 1.1 `NumberUtil.asRomanNumeral`（注册期）
[NumberUtil.java](../../src/main/java/io/github/schntgaispock/gastronomicon/util/NumberUtil.java)：4 个 `String[]` 查表（千/百/十/个位）原为**每次调用新建**，提为 `static final` 常量。算法不变。

### 1.2 `NumberUtil.roundToPrecision`（注册期）
`Math.pow(10, precision)`（~20-40 ns）→ `pow10(precision)` 的 `switch`（0..6 常量，~1 ns），异常 precision 回退 `Math.pow`。结果恒等。

### 1.3 `FoodEffect.teleport`（运行期 ⚡）
[FoodEffect.java](../../src/main/java/io/github/schntgaispock/gastronomicon/api/food/FoodEffect.java)：原循环每次迭代重算 `Math.pow(r, 3)`（`r` 为 `[1,10]` 整数，循环不变量）：
```java
for (int i = 0; i < 10 + Math.pow(r, 3); i++) { ... }
```
改为提至循环外、用整数乘法（`r` 为小整数，`r*r*r` 精确等价 `Math.pow(r,3)`）：
```java
final int maxTries = 10 + r * r * r;
for (int i = 0; i < maxTries; i++) { ... }
```
每次食用传送食物触发，循环最多 `10+r³`（r≤10 → ≤1010）次迭代 → **消除全部 `Math.pow` 调用**（每次 ~20-40 ns × 迭代数）。

### 正确性（红线）
- asRomanNumeral：算法逐字不变，仅数组提为常量。基准 `-ea` 断言新旧结果恒等（通过）。
- roundToPrecision：`Math.pow(10,p)` 与 switch 常量在 p=0..6 逐位相等；断言通过。
- teleport：`r`∈[1,10] 整数，`r*r*r` 与 `Math.pow(r,3)` 数学等价（小整数立方精确）；`int` 不溢出（≤1000）。
- `mvn package -DskipTests` 通过；jar 284 KB。

## 2. 基准（同 JVM OLD vs NEW，[NumberUtilBenchmark.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/NumberUtilBenchmark.java)）

inner=2M × outer=3，best of 8，`-ea` 断言新旧结果恒等。

| 函数 | OLD ns/op | NEW ns/op | 加速比 | 说明 |
|---|---:|---:|---:|---|
| asRomanNumeral | 26.42 | 21.11 | **1.25×** | 去掉 4 个 `String[]` 分配；残余成本为字符串拼接 |
| roundToPrecision | 14.80 | 2.30 | **6.45×** | `Math.pow` → `switch` 常量 |

> teleport 的 `Math.pow` 消除无法纯 JVM 基准（需 Bukkit Player），其收益为解析性：每次食用省去
> 「迭代数 × ~20-40 ns」（r=10 时约 1010 × 30 ns ≈ 30 µs/次）。

## 3. 诚实评估

- asRomanNumeral / roundToPrecision **仅注册期**调用（FoodEffect/FoodItemStack 描述构造，启动时数百次），
  现实吞吐影响有限；本次为「每次调用成本」的清理（去分配、去 Math.pow），属正确性中性的代码卫生。
- teleport 是**真运行期**路径，Math.pow 循环不变量消除对频繁使用传送食物的玩家有实际收益。

## 4. 下一轮

Round 4：`GastroWorkstation` 合成路径的**每次合成分配**（`Arrays.stream(...).mapToObj(...).toList()` 等）——每次手动合成触发，值得做。
