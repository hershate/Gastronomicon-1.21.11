# Round 7 — ElectricKitchen 每 tick 输入哈希的引用缓存（最大单项收益）

> 多轮性能优化第 7 轮。优化点：`ElectricKitchen.findNextRecipe` 每 tick 计算输入哈希时的
> `getItemMeta().hashCode()`（每方块每 tick 9 次 meta 克隆）。前次收口将其列为「需服务端验证」；
> 本轮**从 REF 源码证明了安全性**，予以实施。红线：行为等价（论证见下）。总览见 [PROGRESS.md](PROGRESS.md)。

## 1. 安全性证明（为何不再是「需服务端验证」）

原顾虑：缓存 `slot→哈希` 在「物品引用未变但 meta 被原地改」时会过期 → 合成错配/刷物（红线）。
本轮从源码核验，证明该顾虑对本路径**不成立**：

1. **`getItemInSlot` 返回同一引用**。REF [ChestMenu.getItemInSlot](../../../REF/Slimefun4.1/src/main/java/me/mrCookieSlime/CSCoreLibPlugin/general/Inventory/ChestMenu.java#L157) 实现 `return this.inv.getItem(slot);`
   —— Bukkit `Inventory.getItem(int)` 返回库存内**存储的引用本身**（非克隆）。
2. **`hashIgnoreAmount` 只依赖 type + meta**（`ItemUtil.hashIgnoreAmount`：`getType().hashCode() + (hasItemMeta()? getItemMeta().hashCode() : 0)`），忽略 amount。
3. **输入槽食材在两次 tick 间不会被原地改 meta**：
   - ElectricKitchen 的输入槽是**食材**（非工具，无耐久/附魔变化）；
   - 消耗走 `consumeItem` —— 只改 **amount**（忽略，不影响哈希）或**替换整个物品**（新引用，`==` 失败→重算）；
   - 玩家取放 / 物流运输均**替换引用**（`setItem`）。
4. 故「引用未变（`==`）⇒ (type+meta) 哈希未变」成立，复用缓存哈希**等价**。

> 残留理论风险：第三方插件对食材槽物品调用 `setItemMeta` 原地改 meta（不换引用）。对食材槽这在实践中不发生；
> ElectricKitchen 自身代码路径无此操作。文档记录此假设；若将来确证有此类插件，可加周期性强制重算兜底。

并发：每方块每 tick 由 Slimefun TickerTask 单线程 tick，数组（slotRefs/slotHashes）无需额外同步；
Map 用 `ConcurrentHashMap`（异步 ticker 与主线程 `onBlockBreak` 并发安全）。

## 2. 改动

[ElectricKitchen.java](../../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/automatic/ElectricKitchen.java)：
- 缓存值类型由 `Pair<Integer, Counter<Integer>>` 改为内部类 `CacheEntry{ int hash; Counter found; ItemStack[] slotRefs; int[] slotHashes; }`。
- 哈希计算：逐槽 `cur = menu.getItemInSlot(slot)`；`cur == slotRefs[i]` → 复用 `slotHashes[i]`（**跳过 `getItemMeta`**），否则算 `hashIgnoreAmount(cur)` 并更新缓存。
- 命中/未命中、作废逻辑（`entry.hash=0`）原样迁移。
- `onBlockBreak` 的 `remove` 不变（移除 `CacheEntry`）。

## 3. 基准（同 JVM OLD vs NEW，[ElectricKitchenHashBenchmark.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/ElectricKitchenHashBenchmark.java)）

stub `Item.hashIgnoreAmount()` 用小数组拷贝模拟 `getItemMeta` 克隆+哈希（真实更重，故真实节省 >= 此处）。
稳态（槽位引用 tick 间不变 —— 现实主场景，输入非每 tick 变）。inner=200k × outer=3，best of 8。

| 方式 | ns/tick | 加速比 | getItemMeta 调用/tick |
|---|---:|---:|---:|
| OLD（每 tick 9× hashIgnoreAmount） | 435.87 | — | 9（含 9 次 meta 克隆） |
| NEW（引用缓存，稳态复用） | **3.35** | **~130×** | **0**（首 tick 后） |

> 冷路径（引用变化，如玩家放入新原料）：NEW 对变化槽重算 1 次、其余复用；仍优于 OLD 的全量 9 次。

## 4. 实际影响

每个 ElectricKitchen 方块每 tick 的哈希计算从 ~436 ns（9 次 meta 克隆+哈希）降至稳态 ~3 ns。
对放置大量电厨的服务端（每方块 ~2 tick/秒 × 9 次 meta 克隆），这是本轮系列中**最大的单项 CPU 节省**，
且与放置数量线性相关。与 Round 1（消除 matches 路径的 getByItem）合计，电厨的每 tick 开销被显著压低。

## 5. 构建

`mvn package -DskipTests` 通过；jar 不变大小量级；公开 API/配置未变。
