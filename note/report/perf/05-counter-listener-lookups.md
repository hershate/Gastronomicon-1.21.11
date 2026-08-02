# Round 5 — Counter / 监听器 map 查找去重

> 多轮性能优化第 5 轮。覆盖：`Counter.add/sub/get` 的重复 map 查找（4→1）、`WildHarvestListener` 的
> 掉落表/掉率 map 查找去重。**诚实定位**：Counter 仅 ElectricKitchen cache-miss 路径用、监听器掉率已缓存——
> 均为低-中频；本轮为「每次调用 map 操作数」的真实下降（非装饰性）。红线：行为逐位一致（已 diff 核对）。

## 1. 改动

### 1.1 `Counter`（[Counter.java](../../src/main/java/io/github/schntgaispock/gastronomicon/util/collections/Counter.java)）
- `add`：原 `containsKey` + `set(get()+amount)`（= containsKey + 2 get + 1 改 ≈ **4 次 map 查找**）→ 单次 `get` 拿到 `Pair` 后**原地改**（1 查找）；并用已算出的 `newValue` 取代维护分支里的 `get(hash)`。
- `sub`：原 `get` + `set(get()-amount)` → 单次 `get` + 原地改。
- `get(Integer)`：`containsKey? get().second() : 0`（2 查找）→ 单次 `get` + null 判（1 查找）。
- 删除因此变 dead 的私有 `set`。

### 1.2 `WildHarvestListener`（[WildHarvestListener.java](../../src/main/java/io/github/schntgaispock/gastronomicon/core/listeners/WildHarvestListener.java)）
- `getDrops(Material, Climate)`：原对 climate 最多 **3 次** map 查找（`containsKey`+`get`+`get`）+ 内层 `containsKey`→ 每层单次 `get` + null 判；`getOrDefault(k,null)` 简化为 `get(k)`。
- `getDropChance(Material/EntityType)`：`containsKey`+`get`（2 查找）→ 单次 `get` + null 判（掉率仍首次读 config 后缓存，逻辑不变）。

### 正确性（红线）
- **Counter**：`git stash` 对比优化前后 [CounterTests](../../src/test/java/io/github/schntgaispock/gastronomicon/CounterTests.java)（5 组 add/sub/max/min/total）输出 **逐位一致**（diff 为空）。max/min 维护逻辑（含其「add 不更新非 min 键的 min」的既有行为）原样保留，仅改存储/查找方式 + 用等价的 `newValue`。
- WildHarvest：`get` + null 判 ≡ `containsKey`+`get`（语义等价）；`getOrDefault(k,null)` ≡ `get(k)`。
- `mvn package -DskipTests` 通过。

## 2. 基准（同 JVM OLD vs NEW，[CounterBenchmark.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/CounterBenchmark.java)）

隔离 add 主路径（累加已存在键，keys=9 与输入槽数一致）；inner=2M × outer=3，best of 8。

| 方式 | ns/add | 加速比 |
|---|---:|---:|
| OLD（containsKey+get+get+改） | 6.06 | — |
| NEW（单次 get+原地改） | **3.43** | **1.77×** |

> WildHarvest 的查找去重为低频路径（每 block-break，且掉率已缓存），未单独墙钟基准；map 操作数 3→2 / 2→1 为解析性结论。

## 3. 诚实评估与闭合判断

- 本轮是低-中频路径上「每次调用 map 操作数」的真实下降，绝对吞吐影响有限。
- **至此，离线可基准、行为可证等价的优化面已基本覆盖**（配方匹配 / LootTable / 数学与分配 / 合成路径 / Counter 与监听器查找）。
- **唯一剩余的高频大项**：`ElectricKitchen` 每 tick 的 `getItemMeta().hashCode()` 哈希（每方块每 tick 9 次 meta 克隆+哈希）。
  优化需缓存 per-slot 物品引用并依赖 `getItemInSlot` 引用稳定性 + 确认无原地 meta 变更——**需服务端 Probe 验证，离线无法安全闭合**（错配缓存会导致合成错配/刷物，触碰红线）。列为后续服务端验证项（见 PROGRESS「后续机会」）。

## 4. 下一轮

Round 6：快速巡检剩余未细查路径（FermenterRefillListener / TreeGrowthListener / Fermenter·GrainMill·Refrigerator ticker / 种子 ticker）有无安全低悬果；若无则收口（版本号 + 合并 release note）。
