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
| 6 | HuntingTrap.tick getLocation() 分配 + 剩余路径巡检 | ✅ | [06-ticker-allocations-survey.md](06-ticker-allocations-survey.md) |
| 7 | ElectricKitchen 每 tick 输入哈希引用缓存（源码证安全） | ✅ | [07-electrickitchen-hash-cache.md](07-electrickitchen-hash-cache.md) |
| 8 | 最终巡检（种子/FoodNet/canCraft/命令）→ **确认无可行优化，正式收口** | ✅ | 见下 |

## 状态：正式收口

Round 7 完成了此前列为「需服务端验证」的最大单项项（从 REF 源码证安全并实施）。Round 8 最终巡检确认
**高/中频、离线可安全优化的面已用尽**。版本 `1.2.0`（未发布，含 Round 0–7），合并 release note
[../release/1.2.0.md](../release/1.2.0.md)。

### Round 8 最终巡检（均已查、均无安全优化空间）

| 路径 | 频率 | 结论 |
|---|---|---|
| `GastroFood.onRightClick`（食用） | 中 | 一次 `getByItem` + Bukkit 调用；改用 `this` 低价值且有微风险，不动 |
| 手动工作站 `canCraft`（Fermenter/GrainMill/Refrigerator/MultiStove/CulinaryWorkbench） | 每合成 | GrainMill/CulinaryWorkbench `return true`；其余单次 PDC/charge 读，已极简 |
| `AbstractSeed` 及种子 ticker | 事件驱动（随机 tick/破坏） | 低频；`onExplode` 的 `new ItemStack(AIR)` 仅爆炸时，不动 |
| `FishingNet` ticker | 每 tick | 1.1.4 已优化（waterlogged 读-比-写、`%20` 节流） |
| 命令（GastroCommandExecutor/TabCompleter）stream | 玩家命令 | 低频，不动 |

### 评估后**不做**的结构项（风险/收益不成立）

- **配方索引**（findRecipe 命中失败时由 O(R) 线性扫降至 O(候选)）：shaped/shapeless + 组组件 + 「首个命中优先」
  使索引复杂且易破坏优先级语义；Round 1 后 `matches()` 已很便宜，且仅命中失败时扫描——**风险 > 收益，不做**。
- **`RecipeInput.getAll()` 缓存**：ElectricKitchen 仅命中失败路径调用；返回共享可变数组危及公开 API——不做。

### 残留理论风险（已记录，非阻塞）

第三方插件对电厨食材槽物品调用 `setItemMeta` 原地改 meta（不换引用）会使 Round 7 缓存键过期。
对食材槽这在实践中不发生；若将来确证存在，加周期性强制重算兜底即可。

### 量化总览（各轮最佳结果）

| 轮 | 优化 | 基准结果 |
|---|---|---|
| 1 | RecipeComponent 缓存 getByItem | getByItem 调用稳态 100% 消除；findRecipe 墙钟 5.3–6.1× |
| 2 | LootTable.generate | 分布逐位恒等；小表/均衡表 2.3–2.5×，大表持平 |
| 3 | roundToPrecision / asRomanNumeral / teleport Math.pow | 6.45× / 1.25× / 解析性（运行期） |
| 4 | 合成路径 stream→loop | craft-collect 3.36× |
| 5 | Counter.add 查找 4→1 | 1.77× |
| 6 | HuntingTrap.tick getLocation() 缓存 | 1.86×（stub；真实节省更大） |
| 7 | ElectricKitchen 每 tick 哈希引用缓存 | 稳态 **~130×**（435.9→3.4 ns/tick）；getItemMeta 调用 9→0 |

> ⏳进行中 ⏸待办 ✅完成 ❌放弃（注明原因）

## 热点地图（按频率 × 单次成本排序）

1. **配方匹配** `GastroRecipe.matches` → 每成分 `SlimefunItem.getByItem(component)` + `isItemSimilar`/`isSimilar`。
   - 每次合成、每个 ElectricKitchen tick（每方块）触发，对每个候选配方×每格原料重复调用 `getByItem`。
   - `getByItem(ItemStack)`（REF）：Material 集合查询 + `getItemDataService().getItemData(item)`（**读 ItemMeta/PDC，克隆 meta**）。重复对同一不可变模板解析是纯浪费。← **Round 1**
2. **LootTable.generate**：FishingNet 每 tick、HuntingTrap 捕获、野生采集。2 次 `ThreadLocalRandom` + FP 乘 + `List.get`。← **Round 2**
3. **NumberUtil.asRomanNumeral**：每次调用分配 4 个 `String[]`（药水效果描述）。← **Round 3**
4. **GastroWorkstation 合成**：`Arrays.stream(getInputSlots()).mapToObj(...).toList()` 等每次合成分配多个流对象与列表。← **Round 4**
5. ElectricKitchen Counter、SeedListener/WildHarvestListener 逐方块事件等。← **Round 5+**
