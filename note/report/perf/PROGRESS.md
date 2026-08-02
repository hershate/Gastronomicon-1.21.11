# 性能优化进度日志（PROGRESS）

> `/loop` 驱动的多轮性能优化任务。红线：**安全性 / 稳定性 / 兼容性不得破坏**（行为等价）；
> 已授权必要时整体重写。每轮针对一个优化点，必须包含 benchmark（`benchmark/`）量化前后，
> 并在 `note/report/perf/` 出报告。逐轮 commit。

## 基准与可复现

- 构建环境见 [../../build.md](../../build.md)：JDK 21（`/d/Java/21`）、Maven 3.9.9、离线编译。
- benchmark 不进 jar（仅在本地编译运行）；运行方式：
  ```bash
  export JAVA_HOME=/d/Java/21
  CP="$(cat benchmark/cp.txt)"
  mvn compile -B -o -q                       # 先编译主代码到 target/classes
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath -Dmdep.outputFile=benchmark/cp.txt -o -q
  "$JAVA_HOME/bin/javac" -cp "target/classes;$CP" -d benchmark/build $(find benchmark/src -name '*.java')
  "$JAVA_HOME/bin/java"  -cp "benchmark/build;target/classes;$CP" <基准主类全名>
  ```
- 基准方法论见 [BenchmarkHarness](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/BenchmarkHarness.java)：
  **v2（Round 2 起）**：`measure(name, outerCalls, innerOps, op)`，op() 内扁平循环做 innerOps 次工作（不经 lambda），5 轮预热 + 8 轮测量取最优；ns/op = 单次工作单元。
  > Round 0 报告的绝对值用旧 harness（per-100 微操作）；Round 2 起为 per-op。**同口径前后对比**均用各自轮次的同 JVM 测量（Round 2 用同 JVM OLD vs NEW）。

## 关键约束（Probe 实测结论）

经 [Probe](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/Probe.java) 实测，纯 JVM（无服务端）下：
- ✅ `new ItemStack(Material)`、`stack.getType()` 可用。
- ❌ `hasItemMeta()` / `getItemMeta()` / `isSimilar()` / `hashCode()` —— 需 `Bukkit.server`（CraftServer）。
- ❌ `Slimefun.getRegistry()` / `SlimefunItem.getByItem()` —— 需 Slimefun 实例。

**后果**：合成匹配热路径（依赖 `isSimilar`/`getByItem`/`getItemMeta`）**无法纯 JVM 基准**。策略分两层：
1. 纯 JVM 路径（NumberUtil / LootTable / Counter / 配方缓存机制）→ 真实墙钟基准。
2. 耦合路径（完整 matches()）→ 调用计数 + 解析器桩模型 + 游戏内 Probe（用户提供服务端跑）。

## 轮次总览

| 轮 | 优化点 | 状态 | 报告 |
|---|---|---|---|
| 0 | 基准设施 + Probe + 纯 JVM 基线 | ✅ | [00-baseline-and-methodology.md](00-baseline-and-methodology.md) |
| 1 | RecipeComponent 缓存 SlimefunItem 解析（最热路径） | ✅ | [01-recipe-component-cache.md](01-recipe-component-cache.md) |
| 2 | LootTable.generate（数组索引 + 整数随机 + 满桶跳过） | ✅ | [02-loottable-generate.md](02-loottable-generate.md) |
| 3 | NumberUtil/FoodEffect 数学与分配清理（含 teleport Math.pow 循环不变量） | ✅ | [03-numberutil-food-math.md](03-numberutil-food-math.md) |
| 4 | 合成路径分配（stream→loop）+ ElectricKitchen getInputSlots 复用 | ✅ | [04-craft-path-allocations.md](04-craft-path-allocations.md) |
| 5 | Counter.add 查找 4→1 + WildHarvest map 查找去重 | ✅ | [05-counter-listener-lookups.md](05-counter-listener-lookups.md) |
| 6 | HuntingTrap.tick getLocation() 分配 + 剩余路径巡检 → **收口** | ✅ | [06-ticker-allocations-survey.md](06-ticker-allocations-survey.md) |

## 收口状态

**离线可基准、行为可证等价的优化面已系统覆盖（Round 0–6）。** 版本 `1.1.6 → 1.2.0`，合并 release note [../release/1.2.0.md](../release/1.2.0.md)。

### 唯一未闭合项（需服务端验证，离线无法安全闭合）

**`ElectricKitchen.findNextRecipe` 每 tick 的 `getItemMeta().hashCode()` 哈希**（每方块每 tick 9 次 meta 克隆+哈希）。
优化方案：缓存 `(slot → 上次 ItemStack 引用, 上次哈希)`，引用未变则复用哈希、跳过 getItemMeta。
**前提**（均需服务端 Probe 实测确认）：
1. `BlockMenu.getItemInSlot(slot)` 在槽位未变时返回**同一引用**（否则缓存永不命中，无收益但不影响正确性）；
2. 槽位物品不会被**原地改 meta**（`setItemMeta` 不换引用）——否则缓存键过期会导致合成错配/刷物（红线）。
确认后按 Round 1 的 volatile 惰性缓存范式实施，预期为该路径的最大单项收益。

### 量化总览（各轮最佳结果）

| 轮 | 优化 | 基准结果 |
|---|---|---|
| 1 | RecipeComponent 缓存 getByItem | getByItem 调用稳态 100% 消除；findRecipe 墙钟 5.3–6.1× |
| 2 | LootTable.generate | 分布逐位恒等；小表/均衡表 2.3–2.5×，大表持平 |
| 3 | roundToPrecision / asRomanNumeral / teleport Math.pow | 6.45× / 1.25× / 解析性（运行期） |
| 4 | 合成路径 stream→loop | craft-collect 3.36× |
| 5 | Counter.add 查找 4→1 | 1.77× |
| 6 | HuntingTrap.tick getLocation() 缓存 | 1.86×（stub；真实节省更大） |

> ⏳进行中 ⏸待办 ✅完成 ❌放弃（注明原因）

## 热点地图（按频率 × 单次成本排序）

1. **配方匹配** `GastroRecipe.matches` → 每成分 `SlimefunItem.getByItem(component)` + `isItemSimilar`/`isSimilar`。
   - 每次合成、每个 ElectricKitchen tick（每方块）触发，对每个候选配方×每格原料重复调用 `getByItem`。
   - `getByItem(ItemStack)`（REF）：Material 集合查询 + `getItemDataService().getItemData(item)`（**读 ItemMeta/PDC，克隆 meta**）。重复对同一不可变模板解析是纯浪费。← **Round 1**
2. **LootTable.generate**：FishingNet 每 tick、HuntingTrap 捕获、野生采集。2 次 `ThreadLocalRandom` + FP 乘 + `List.get`。← **Round 2**
3. **NumberUtil.asRomanNumeral**：每次调用分配 4 个 `String[]`（药水效果描述）。← **Round 3**
4. **GastroWorkstation 合成**：`Arrays.stream(getInputSlots()).mapToObj(...).toList()` 等每次合成分配多个流对象与列表。← **Round 4**
5. ElectricKitchen Counter、SeedListener/WildHarvestListener 逐方块事件等。← **Round 5+**
