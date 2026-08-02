# Round 1 — RecipeComponent 缓存 SlimefunItem 解析（最热路径）

> 多轮性能优化第 1 轮。优化点：消除配方匹配中对**不可变模板**重复调用 `SlimefunItem.getByItem`。
> 红线：行为等价（见下「正确性」）。基准与报告见 [PROGRESS.md](PROGRESS.md)。

## 1. 问题

配方匹配是本插件最高频路径：每次手动合成、每个 ElectricKitchen **每 tick × 每个放置的方块**都会触发
`findRecipe` → 遍历候选配方 → 对每个配方×每格原料调 `component.matches()`。

`SingleRecipeComponent.matches` / `GroupRecipeComponent.matches` 原**每次调用**都执行：
```java
SlimefunItem sfComponent = SlimefunItem.getByItem(component);  // 对同一不可变 component 重复解析
```
而 `component` 是注册后即不可变的配方模板，解析结果恒定。REF 的 `getByItem(ItemStack)`（见
[REF/.../SlimefunItem.java](../../../REF/Slimefun4.1/src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java#L1176)）对 SF 物品会读 `PersistentDataContainer`（克隆 ItemMeta）——重复对同一模板解析是纯浪费。

## 2. 改动（行为等价）

- [SingleRecipeComponent](../../src/main/java/io/github/schntgaispock/gastronomicon/api/recipes/components/SingleRecipeComponent.java)：惰性缓存 `getByItem(component)` 结果（`volatile` 字段 + `resolved` 标志，首次 `matches()` 解析一次）；`hashCode()` 同样惰性缓存（注册期去重路径）。
- [GroupRecipeComponent](../../src/main/java/io/github/schntgaispock/gastronomicon/api/recipes/components/GroupRecipeComponent.java)：惰性把组内每个模板的解析结果 + 模板本身展开为数组，`matches()` 改数组索引遍历（替代 Set 迭代器）。`toArray` 保留 Set 迭代顺序，故「命中首个即返回」结果不变。

**可见性**：`matches()` 会由 ElectricKitchen 的异步 ticker 调用（[ElectricKitchen.java:241](../../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/automatic/ElectricKitchen.java#L241)），故用 `volatile`；首次并发解析为良性竞争（幂等）。

### 正确性（红线核对）

- 解析结果对不可变 component 恒定 → 缓存值 ≡ 每次重解析值。**唯一理论差异**：运行期 SF 物品被注销。Slimefun 正常运行不注销已注册物品，故实际无差异（与原实现等价）。
- GroupRecipeComponent 迭代顺序：`toArray()` ≡ Set 迭代顺序，首个命中结果一致。
- 公开 API（构造器、`matches`/`getDisplayItem`/`hashCode` 签名）不变；仅新增 private 缓存字段。
- `mvn package -DskipTests` 通过；shaded jar 284 KB（基线 277 KB，+7 KB 缓存字段，可忽略）。

## 3. 基准（忠实算法模型）

真实 `matches()` 依赖 `isSimilar()`/`getByItem()`，Probe 实测无法纯 JVM 运行（见 [00 报告](00-baseline-and-methodology.md)）。
故用**忠实算法模型**量化优化机制：resolver 模拟 `getByItem`，两档成本：

- **轻档**：`HashMap.get`（≈ vanilla getByItem 快速负路径：Material 集合未命中即返回）。
- **重档**：`HashMap.get` + 合成一次 meta 克隆/PDC 读（≈ SF 物品路径）。

负载：200 配方 × 4 槽 = **800 次 `component.matches`/findRecipe**；10 000 次 findRecipe；SF 物品占比 30%。
（[RecipeCacheBenchmark.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/RecipeCacheBenchmark.java)）

| resolver 档 | BEFORE ns/findRecipe | AFTER ns/findRecipe | 墙钟加速比 | resolver 调用 BEFORE→AFTER |
|---|---:|---:|---:|---|
| 轻（vanilla 快速负路径） | 3 078 | 576 | **5.34×** | 64 000 000 → **0** |
| 重（SF 物品 PDC 读） | 6 123 | 1 010 | **6.07×** | 64 000 000 → **0** |

**关键结论**：
1. **稳态下 `getByItem` 调用 100% 消除**（精确值：64 000 000 → 0）。这是最强的、与 resolver 成本无关的证据。
2. 墙钟每 findRecipe 加速 **5.3–6.1×**；真实服务端 getByItem（PDC 读）更接近重档，故 ~6× 为合理预期。
3. 收益随「放置的 ElectricKitchen 方块数 × tick 频率」线性放大——高负载多机电厨的服务端获益最大。

> 调用计数为精确值；墙钟为同口径前后对比模型，绝对值需游戏内 Probe 用真实 getByItem 校准（待补）。

## 4. 影响面

- 直接受益：所有 `findRecipe` 调用——`GastroWorkstation`（手动合成）、`ElectricKitchen`（异步 tick）、
  `MultiStove.findRecipe`（继承）、以及任何遍历 `RecipeRegistry.getRecipes` 的路径。
- 间接：注册期 `GastroRecipe` 去重（`hashCode` 缓存）开销略降。

## 5. 待办/后续

- 游戏内 Probe 校准真实 `getByItem` 墙钟（确认落在重档曲线附近）——可选，不阻塞。
- Round 2 起：LootTable.generate（去 FP、整数随机、数组索引）。
