# Round 6 — HuntingTrap 每 tick 分配 + 巡检收口

> 多轮性能优化第 6 轮（收口轮）。优化点：`HuntingTrap.tick` 反复 `b.getLocation()` 分配；并对剩余未细查路径做巡检。
> 红线：行为等价（结果一致性已核对）。总览见 [PROGRESS.md](PROGRESS.md)。

## 1. 巡检结论（剩余路径）

对 `core/`（监听器、工作站 ticker、命令）与 `integration/` 扫描 `.stream()/.toList()`、`getConfig()`、ticker 方法：

| 路径 | 结论 |
|---|---|
| 命令（GastroCommandExecutor/GastroTabCompleter）的 stream | 玩家命令驱动，**低频**，不动 |
| `getConfig()` 读取 | 仅 WildHarvest（已缓存）+ GastroWorkstation（懒加载缓存），无新增 per-tick 读 |
| `FishingNet` ticker | 1.1.4 已优化（waterlogged 读-比-写、`%20` 节流）；`findNextRecipe` 分配为每 `240/speed` tick 一次，**低频**，不动 |
| `ElectricKitchen` 每 tick 哈希 | 见下「未闭合项」——需服务端验证 |
| **`HuntingTrap.tick`** | **本轮修**：反复 `getLocation()` 分配 |

## 2. 改动：`HuntingTrap.tick`（每 tick × 每陷阱）

[HuntingTrap.java](../../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/manual/HuntingTrap.java)：
原 tick 调用 `b.getLocation()` **约 5 次**（`containsKey`、`get`、三坐标各一次），每次返回**新 `Location`** 对象；并把 `triggeredTraps.containsKey+get`（2 次查找）合一。改为：
```java
final Location l = b.getLocation();                 // 1 次分配
final Boolean triggered = triggeredTraps.get(l);    // 1 次查找
if (triggered != null) { if (triggered) { ...用 l.getX()/getY()/getZ()... } }
else { startCatch(l); }
```
每个陷阱每 tick 触发（含空闲陷阱的 `get` 检查）。高密度陷阱下显著降低分配/GC 压力。

### 正确性（红线）
- `get+null判` ≡ `containsKey+get`（`triggeredTraps` 值为 `Boolean`，`if (triggered)` 对非空 Boolean 解箱同原 `if (get())`）。
- 坐标改用缓存的 `l`，数值不变。
- 基准结果一致性断言通过；`mvn package -DskipTests` 通过。

## 3. 基准（同 JVM OLD vs NEW，[TickerBenchmark.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/TickerBenchmark.java)）

`org.bukkit.Location` 需 World，纯 JVM 无法构造；用轻量 stub `Loc`（含 hashCode/equals，模拟其作 map 键的开销）建模「每次 new 一个对象」的分配+查找差。**真实 Location 字段更多（World/yaw/pitch），故真实节省 >= 此处。** inner=1M × outer=3，best of 8。

| 方式 | ns/tick | 加速比 |
|---|---:|---:|
| OLD（5×new Loc + containsKey+get） | 4.87 | — |
| NEW（1×new Loc + 单次 get） | **2.62** | **1.86×** |

## 4. 收口判断（campaign closure）

经 0–6 轮，**离线可基准、行为可证等价的优化面已系统覆盖**：

| 频率档 | 路径 | 状态 |
|---|---|---|
| 高频（每 tick × 每方块） | 配方匹配 `getByItem` 解析 | ✅ Round 1（5–6×，调用 100% 消除） |
| 高频 | `ElectricKitchen` 每 tick `getItemMeta` 哈希 | ⏸ 见下「未闭合项」 |
| 中频 | LootTable.generate | ✅ Round 2 |
| 中频 | 合成路径分配 | ✅ Round 4 |
| 中频 | Counter / 监听器查找 / HuntingTrap 分配 | ✅ Round 5 / 6 |
| 低频（注册期） | NumberUtil / FoodEffect 数学 | ✅ Round 3 |

### 唯一未闭合项（需服务端验证，离线无法安全闭合）

`ElectricKitchen.findNextRecipe` 每 tick 为缓存键计算 `Σ hashIgnoreAmount(slot)`，每槽 `getItemMeta().hashCode()`（meta 克隆）。优化需缓存「per-slot 物品引用→哈希」并依赖 `BlockMenu.getItemInSlot` 引用稳定性 + 确认无原地 meta 变更。
**风险**：若缓存键过期（原地改 meta 未换引用）会导致合成错配/刷物（触碰红线）。**必须服务端 Probe 验证**后实施，离线不做。

## 5. 收口动作

- 版本号 `1.1.6 → 1.2.0`（性能里程碑）。
- 合并 release note：[../../release/1.2.0.md](../../release/1.2.0.md)。
- `/loop` 任务收口（CronDelete）。
