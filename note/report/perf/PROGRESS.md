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
  3 轮预热 + 5 轮测量取最优；消费返回值防死代码消除。

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
| 1 | RecipeComponent 缓存 SlimefunItem 解析（最热路径） | ⏳ | — |
| 2 | LootTable.generate（整数随机 + 数组索引，去 FP） | ⏸ | — |
| 3 | NumberUtil.asRomanNumeral（静态化常量数组，去分配） | ⏸ | — |
| 4 | GastroWorkstation 合成路径分配（去 stream/toList） | ⏸ | — |
| 5 | 机器 ticker / 监听器 / Counter 等 | ⏸ | — |

> ⏳进行中 ⏸待办 ✅完成 ❌放弃（注明原因）

## 热点地图（按频率 × 单次成本排序）

1. **配方匹配** `GastroRecipe.matches` → 每成分 `SlimefunItem.getByItem(component)` + `isItemSimilar`/`isSimilar`。
   - 每次合成、每个 ElectricKitchen tick（每方块）触发，对每个候选配方×每格原料重复调用 `getByItem`。
   - `getByItem(ItemStack)`（REF）：Material 集合查询 + `getItemDataService().getItemData(item)`（**读 ItemMeta/PDC，克隆 meta**）。重复对同一不可变模板解析是纯浪费。← **Round 1**
2. **LootTable.generate**：FishingNet 每 tick、HuntingTrap 捕获、野生采集。2 次 `ThreadLocalRandom` + FP 乘 + `List.get`。← **Round 2**
3. **NumberUtil.asRomanNumeral**：每次调用分配 4 个 `String[]`（药水效果描述）。← **Round 3**
4. **GastroWorkstation 合成**：`Arrays.stream(getInputSlots()).mapToObj(...).toList()` 等每次合成分配多个流对象与列表。← **Round 4**
5. ElectricKitchen Counter、SeedListener/WildHarvestListener 逐方块事件等。← **Round 5+**
