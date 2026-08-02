# Round 2 — LootTable.generate 优化（整数随机 + 数组索引 + 满桶跳过）

> 多轮性能优化第 2 轮。优化点：`LootTable.generate`（FishingNet 每 tick、HuntingTrap、野生采集的高频路径）。
> 红线：概率分布**恒等**（已用 2000 万次采样验证）。基准与总览见 [PROGRESS.md](PROGRESS.md)。

## 1. 改动（行为等价）

[LootTable.java](../../src/main/java/io/github/schntgaispock/gastronomicon/api/loot/LootTable.java)：

| 项 | 旧 | 新 |
|---|---|---|
| drops 存储 | `List<T>`（`stream().toList()`，不可变 List） | `T[]`（构造期 `toArray`） |
| 取元素 | `drops.get(roll)`（方法派发+边界检查） | `drops[roll]`（数组索引） |
| 概率比较 | `nextDouble()*totalWeight < prob[roll]`（**两次** `current()` + FP 乘） | `nextInt(totalWeight) < prob[roll]`（单次 `current()`，整数） |
| 第二次随机 | 恒执行 | **满概率桶（`prob[roll] >= totalWeight`）跳过** |

- 同步更新 [EmptyItemLootTable](../../src/main/java/io/github/schntgaispock/gastronomicon/api/loot/EmptyItemLootTable.java)：`super(List.of(),…)` → `super(new ItemStack[0],…)`。
- 构造器签名由 Lombok `@RequiredArgsConstructor` 自动随之改变（`List<T>` → `T[]`）；仅 `build()` 与 `EmptyItemLootTable` 两处调用，均已适配。

### 等价性论证（红线）

1. **分布**：`nextInt(totalWeight) < prob[roll]` 与 `nextDouble()*totalWeight < prob[roll]` 在 `totalWeight>0` 时分布恒等（前者是别名法的**精确离散**形式）。已用既有 [LootTableTests](../../src/test/java/io/github/schntgaispock/gastronomicon/LootTableTests.java)（2000 万次采样）实测：**改前改后各组百分比逐位一致**（见下）。
2. **满桶跳过**：`prob[roll]==totalWeight` 时，原 `nextDouble()*tw<tw` 恒真 → 必返回 `d[roll]`；新实现提前返回 `d[roll]`，等价。别名法 build 结束后，未参与配对的「大」桶 prob 恒 = totalWeight。
3. **退化**（全零权重，`totalWeight<=0`）：旧实现恒走 alias 分支返回 `drops[0]`；新实现显式 `tw<=0 → d[0]`，保持一致。
4. `mvn package -DskipTests` 通过；jar 284 KB。

### 分布实测（LootTableTests，20M 采样，数值=2×百分比）

改前 = 改后（逐位一致）：权重 1→1, 4→4, 6→6, 10→10, 11→11, 100→100。

## 2. 基准（同 JVM OLD vs NEW，[LootTableBenchmark.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/LootTableBenchmark.java)）

用反射从真实 `build()` 产出的表读出 drops/tw/prob/alias，新旧算法**同进程交替**测量（消除跨运行噪声）。inner=5M × outer=3 = 15M generates/round，best of 8。

| 表形态 | OLD ns/gen | NEW ns/gen | 加速比 |
|---|---:|---:|---:|
| uniform n=10（权重各异） | 10.34 | 10.63 | ~1.0×（噪声内，新算法工作 ≤ 旧） |
| skewed n=3 (70/10/2) | 11.43 | 11.41 | ~1.0× |
| **binary n=2 (50/50)** | 6.23 | **2.54** | **2.45×** |
| **single n=1** | 6.02 | **2.56** | **2.36×** |

### 解读（诚实）

- 别名法：先**均匀**随机选槽（`nextInt(n)`），再以 `prob/tw` 概率决定保留该槽或走 alias。**满桶跳过**仅在「被选中的槽 prob==tw」时省去第二次随机。
- 故收益取决于**多少槽是满概率**：
  - 小表 / 均衡表（如 binary 50/50、单掉落）→ 几乎所有槽满 → **2.3–2.5×**。
  - 大型权重各异的表 → 多数槽需第二次随机 → 与旧版持平（新算法工作量 ≤ 旧版，差异在噪声内）。
- 偏斜表（一个主导项）只有主导项所在槽（被选中概率 1/n）满，故收益有限——这是别名法的固有结构，非本优化可解。
- `nextDouble()*tw` vs `nextInt(tw)` 本身成本相当（前者一 draw+FP 乘；后者对非 2 幕 bound 有拒绝采样）；本优化的净收益主要来自「单次 `current()`」与「满桶免第二次 draw」。

### 实际影响

FishingNet 各档掉落表通常较小（数种鱼），多为小表/近均衡 → 落在 **2× 以上**的收益区。大型杂项掉落表（如野生采集多物）→ 持平，无回退。

## 3. 结论

分布恒等、永不回退、小表/均衡表显著加速（2.3–2.5×）。LootTable.generate 优化面已完成。
（整数形式另为别名法的精确离散实现，边际更正确。）

## 4. 下一轮

Round 3：`NumberUtil.asRomanNumeral`（每次调用分配 4 个 `String[]`，改 `static final` 常量）。
