# Round 0 — 基准设施、方法论与纯 JVM 基线

> 本轮不改动产品代码，仅建立**可复现的基准框架**并实测纯 JVM 热路径基线。
> 起因为 `/loop` 驱动的多轮性能优化任务（红线：行为等价；详见 [PROGRESS.md](PROGRESS.md)）。

## 1. 交付物

- [benchmark/src/.../BenchmarkHarness.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/BenchmarkHarness.java) —— 极简基准运行器（3 轮预热 + 5 轮测量取最优；消费返回值防死代码消除；零外部依赖，沿用项目 `main` 方法约定）。
- [benchmark/src/.../Probe.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/Probe.java) —— 探针，确定哪些 Bukkit/Slimefun API 可在纯 JVM 下运行。
- [benchmark/src/.../BaselineBenchmark.java](../../../benchmark/src/io/github/schntgaispock/gastronomicon/benchmark/BaselineBenchmark.java) —— 纯 JVM 热路径基线。
- benchmark 编译/运行不进 shaded jar，仅本地 `benchmark/build/`（已 gitignore）。

## 2. 方法论

- **口径**：单线程、JIT 开启、`System.nanoTime()` 计时；每轮 `op()` 内做 100 次微操作，报告 `ns/op`（=每 100 微操作）与 `ops/s`。
- **诚实性**：被测代码均为真实产品类（NumberUtil / LootTable / Counter）。凡需 Bukkit/Slimefun 的叶子调用，已在对应基准点用桩/注入替换并**显式注明**，绝对值仅供同口径前后对比。
- **可复现**：见 [PROGRESS.md](PROGRESS.md) 顶部命令。数字会随机器/JIT 波动，基准程序是事实来源。

## 3. Probe 实测结论（决定基准分层）

| API | 纯 JVM | 备注 |
|---|---|---|
| `new ItemStack(Material)` / `getType()` | ✅ | 仅设字段 |
| `hasItemMeta()` / `getItemMeta()` / `isSimilar()` / `hashCode()` | ❌ | 需 `Bukkit.server`（CraftServer） |
| `Slimefun.getRegistry()` / `SlimefunItem.getByItem()` | ❌ | 需 Slimefun 实例 |

→ 合成匹配热路径无法纯 JVM 跑墙钟；用「调用计数 + 解析器桩 + 游戏内 Probe」补充（见各轮报告）。

## 4. 纯 JVM 基线（ indicative，best-of-5 after 3 warmup，20 000 × 100 微操作/轮）

| 被测 | ns/op(100微操作) | ≈ ns/单次 | 备注 |
|---|---:|---:|---|
| `NumberUtil.asRomanNumeral`(混合) | 2 514 | ~25 | 每次分配 4 个 `String[]` + 字符串拼接 ← 分配主导 |
| `NumberUtil.randomRound`(0..2) | 294 | ~2.9 | `ThreadLocalRandom`，合理 |
| `NumberUtil.clamp(int)` | 52 | ~0.5 | 平凡 |
| `NumberUtil.clamp(double)` | 127 | ~1.3 | 平凡 |
| `LootTable.generate`(n=10) | 1 039 | ~10.4 | 2 次随机 + FP 乘 + `List.get` |
| `LootTable.generate`(n=2) | 782 | ~7.8 | n=2 仅比 n=10 快 ~25% → 开销主导，非 size 项 |
| `Counter.add`(10 distinct) | 166 | ~1.7 | |
| `Counter.get`(10 distinct) | 219 | ~2.2 | |

### 观察（后续轮次的依据）

1. `asRomanNumeral` ~25 ns/次，**分配 4 个 `String[]`** 是主因 → Round 3 改为 `static final` 常量数组。
2. `LootTable.generate` n=2(7.8) vs n=10(10.4) 差距小，说明固定开销（2 次随机抽取 + `nextDouble()` FP 乘 + `List.get` 边界检查 + 调度）主导 → Round 2 改为**单次整数随机**（`nextInt(totalWeight) < prob[roll]`，与别名法语义等价且更精确）+ **数组索引**替代 `List.get`。
3. `Counter` 单次 ~1.7–2.2 ns，暂非瓶颈；其 `findMaxMin` 重扫仅在特定分支触发，留待评估。

## 5. 构建验证

- `mvn compile -B -o -q` 通过（基线，未改产品代码）。
- benchmark 编译运行通过（见上）。

## 6. 本轮不涉及产品代码改动

无；纯基础设施与基线。下一轮 [01-recipe-component-cache.md](01-recipe-component-cache.md) 起开始改产品代码。
