# Round 4 — 合成路径分配优化（stream → loop）

> 多轮性能优化第 4 轮。优化点：`GastroWorkstation` 手动合成「收集槽位物品」的每次合成分配；
> 附带 `ElectricKitchen.findNextRecipe` 的 `getInputSlots()` 复用。
> **诚实定位**：手动合成路径为玩家点击驱动（低-中频）；本轮主收益是**降低每次合成的分配/GC 压力**，
> 绝对吞吐影响有限。红线：行为等价（哈希一致性已核对）。总览见 [PROGRESS.md](PROGRESS.md)。

## 1. 改动

### 1.1 `GastroWorkstation` 合成收集（每次手动合成）
[GastroWorkstation.java](../../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/manual/GastroWorkstation.java)：
原料/容器/工具的收集原用 `Arrays.stream(slots).mapToObj(...).toList()/toArray()`，每次合成分配
**3 个流管线**（Spliterator + 包装对象）+ 2 个 `ArrayList` + `int→Integer` 装箱。改为：
- plain `for` 循环直接填数组；
- `containers`/`tools` 用 `Arrays.asList(arr)` 薄包装（零拷贝、零装箱），保持 `List` 签名以兼容
  `findRecipe` / `recipe.matches(ingredients, containers, tools)`。
- `getInputSlots()/getContainerSlots()/getToolSlots()` 各取一次存 local 复用（原在流内每次调用）。

### 1.2 `ElectricKitchen.findNextRecipe`（每 tick × 每方块）
[ElectricKitchen.java](../../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/automatic/ElectricKitchen.java)：
`getInputSlots()`（每次返回 `new int[]`）原在哈希循环（每 tick）与匹配循环（每 miss）各调一次。
提为方法入口 `final int[] inputSlots = getInputSlots();` 复用于两处。每次合成/每 tick 少一次 9-int 数组分配。

### 未做（评估后放弃）
- **`RecipeInput.getAll()` 缓存**：唯一调用方为 ElectricKitchen（仅 cache-miss 路径），收益边际；
  且返回共享可变数组会危及公开 API 健壮性，不值。
- **ElectricKitchen 每 tick 的 `getItemMeta().hashCode()` 哈希**：是该路径最大单项成本，但优化需
  缓存 per-slot 物品引用并依赖 `getItemInSlot` 引用稳定性，需服务端实测验证——风险高，列为后续机会（见 PROGRESS）。

### 正确性（红线）
- 收集结果集合不变（元素、顺序、null 处理一致）；基准断言 OLD/NEW 哈希相等（通过）。
- `Arrays.asList` 返回固定大小视图，`findRecipe`/`matches` 仅读不写，兼容。
- `mvn package -DskipTests` 通过。

## 2. 基准（同 JVM OLD vs NEW，[CraftPathBenchmark.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/CraftPathBenchmark.java)）

桩 Item（`asOne` 克隆成本 OLD/NEW 等价，已剔除以隔离「收集方式」开销差）；inner=1M × outer=3，best of 8。

| 方式 | ns/craft-collect | 加速比 |
|---|---:|---:|
| OLD（stream + toList） | 155.83 | — |
| NEW（loop + Arrays.asList） | **46.44** | **3.36×** |

分配：OLD 每次约 3 流管线 + 2 `ArrayList` + 装箱；NEW 3 数组 + 2 `Arrays.asList` 薄包装，零装箱。

## 3. 诚实评估

- 手动合成低-中频，绝对节省（~110 ns/craft × 数次/秒）有限；**主收益是分配/GC 压力下降**与代码更直白。
- ElectricKitchen 的 `getInputSlots()` 复用是每 tick 微分配的减少，量级很小但零风险。

## 4. 后续机会（未闭合，需服务端验证）

- **ElectricKitchen 每 tick 哈希的 `getItemMeta().hashCode()`**（9 次/方块/tick）：若 `BlockMenu.getItemInSlot`
  在槽位未变时返回同一引用，可缓存 `(slot→上次物品引用, 上次哈希)`，引用未变则复用哈希、跳过 getItemMeta。
  需服务端 Probe 验证引用稳定性，风险中等，潜在收益大。

## 5. 下一轮

Round 5：监听器路径（SeedListener / WildHarvestListener 每 block-break）与 Counter 评估。
