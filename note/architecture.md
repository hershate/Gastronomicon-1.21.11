# Gastronomicon（美食家）架构与要点

> 本文档基于源码（截至 `master` @ `72d0dbc`）整理，用于快速理解项目结构与核心机制。
> 所有外部引用均保留可点击的相对路径。

## 1. 项目概述

- **定位**：粘液科技 (Slimefun) 的食物主题附属插件，新增大量食材、料理、烹饪工作站与作物/种子系统。
- **基座**：基于 [InfinityLib](https://github.com/mooy1/infinitylib) 的 `AbstractAddon` / `MenuBlock` 开发。
- **版本**：`1.1.4`（见 [pom.xml](../pom.xml#L8)）。1.1.4 为逻辑/稳定性/安全性修复版本，改动见 [release/1.1.4.md](release/1.1.4.md)。
- **当前主线工作**：将本附属适配至 Minecraft `1.21.11`，参考源码位于 [REF/Slimefun4.1](../REF/Slimefun4.1)（非官方维护分支，版本 `4.9.5`，**只读，不得修改**）。

## 2. 环境与依赖

| 依赖 | 关系 | 说明 |
|---|---|---|
| `Slimefun` | 硬依赖 | 必需 |
| `GuizhanLibPlugin`（鬼斩前置库） | 硬依赖 | [Gastronomicon.java](../src/main/java/io/github/schntgaispock/gastronomicon/Gastronomicon.java#L52-L57) 启动时强校验，缺失则禁用插件 |
| `ExoticGarden`（异域花园） | 软依赖 | 提供部分原料（橙子/番茄/蒜…），缺失时自动隐藏相关配方 |
| `DynaTech`（动力科技） | 软依赖 | 自动化作物培育，**默认禁用**（见 [config.yml](../src/main/resources/config.yml#L9) 与 commit `97d7f69`，因不兼容） |
| `SlimeHUD` | 软依赖 | HUD 展示，版本不兼容时静默跳过 |
| `InfinityLib` | 编译期打包 | shade 时重定向到 `io.github.schntgaispock.infinitylib` |

- **构建**：Java 17，Paper 1.19.3 API，maven-shade-plugin 打包。
- **入口**：`io.github.schntgaispock.gastronomicon.Gastronomicon`（见 [plugin.yml](../src/main/resources/plugin.yml#L7)）。

## 3. 顶层目录结构

```
src/main/java/io/github/schntgaispock/gastronomicon/
├── Gastronomicon.java            # 主类
├── api/                          # 公开 API（供其它附属/脚本调用）
│   ├── events/                   # 事件（食物食用、食物合成）
│   ├── food/                     # FoodEffect、GastroFoodBuilder
│   ├── items/                    # FoodItemStack、主题 ItemStack
│   ├── loot/                     # 掉落表
│   ├── recipes/                  # 配方系统（GastroRecipe 及组件）
│   └── trees/                    # 树结构（岛屿/树生成）
├── core/                         # 插件核心实现
│   ├── Climate.java
│   ├── command/                  # /gastronomicon 命令
│   ├── listeners/                # 种子、发酵、树木生长、野生采集监听
│   ├── setup/                    # 注册中心（Item/Research/Listener/Command）
│   └── slimefun/
│       ├── GastroGroups.java     # 物品组
│       ├── GastroResearch.java   # 研究
│       ├── GastroStacks.java     # 物品堆定义（id、材质、名称）
│       ├── recipes/              # GastroRecipeType / GastroRecipeGroups
│       └── items/
│           ├── food/             # GastroFood / SimpleGastroFood
│           ├── seeds/            # 各类种子
│           └── workstations/
│               ├── automatic/    # ElectricKitchen、FishingNet
│               └── manual/       # GastroWorkstation 基类 + 各手动工作站
├── integration/                  # DynaTech、SlimeHUD 集成
└── util/                         # 工具类（集合、ItemUtil、RecipeUtil…）
```

## 4. 启动流程

[Gastronomicon.enable()](../src/main/java/io/github/schntgaispock/gastronomicon/Gastronomicon.java#L48-L112) 顺序：

1. 校验 `GuizhanLibPlugin`（缺失即禁用）。
2. 可选启动 `GuizhanUpdater` 自动更新（仅 `Build` 开头版本）。
3. 初始化 bstats 指标。
4. 依次调用：`ItemSetup.setup()` → `ResearchSetup.setup()` → `ListenerSetup.setup()` → `CommandSetup.setup()`。
5. 软依赖集成（`SlimeHUDSetup` / `DynaTechSetup`），用 try-catch 包裹，版本不兼容仅告警。
6. 加载 `player.yml`（玩家档案）、`custom-food.yml`（自定义食物）。
7. `TreeStructure.loadTrees()` 加载树木结构。

## 5. 核心子系统

### 5.1 食物系统（核心中的核心）

- **类层级**：`SlimefunItem` → `SimpleGastroFood` → `GastroFood`。
- **食用入口**：[GastroFood.onRightClick()](../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/food/GastroFood.java#L49-L85)
  - 饱食度满（`foodLevel >= 20`）时取消食用。
  - 触发 `PlayerGastroFoodConsumeEvent`（可被取消）。
  - 依次应用该食物的全部 `FoodEffect` → 增加 hunger/saturation → 返还容器（如碗/瓶）→ 扣减物品。
- **FoodEffect 种类**（见 [FoodEffect.java](../src/main/java/io/github/schntgaispock/gastronomicon/api/food/FoodEffect.java)）：
  `heal`（回血）、`positivePotionEffect` / `negativePotionEffect`（正/负药水）、`removePotionEffect`、`xp`、`giveItem` / `giveSlimefunItem`、`air`（氧气）、`warm`（温暖/减冰冻）、`teleport`（随机传送）、`move`（位移）、`extinguish`（灭火）、`clearPotionEffects`（清效果）、`chanceOf`（按概率触发另一效果）。
- **完美食物 (Perfect Food)**：
  - 每种食物 `register()` 时自动生成一个**隐藏的完美版本**（[GastroFood.java:91-99](../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/food/GastroFood.java#L91-L99)）。
  - 完美版本各项 FoodEffect 数值有固定加成（如 `PERFECT_MULTIPLIER_HEALTH = 1.25`、药水额外 +1 级，见 [FoodEffect.java:44-53](../src/main/java/io/github/schntgaispock/gastronomicon/api/food/FoodEffect.java#L44-L53)）。
- **熟练度 (Proficiency)**：
  - 每次合成某食物，`player.yml` 中 `<uuid>.proficiencies.<itemId>` +1（[GastroWorkstation.java:184-189](../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/manual/GastroWorkstation.java#L184-L189)）。
  - 触发完美食物的概率 = `clamp(proficiency / 864, 0, 0.25)`（即熟练度达 216 时封顶 25%），由 `NumberUtil.randomRound` 决定产出普通/完美版（[GastroWorkstation.java:191-197](../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/manual/GastroWorkstation.java#L191-L197)）。
- **构建器**：[GastroFoodBuilder](../src/main/java/io/github/schntgaispock/gastronomicon/api/food/GastroFoodBuilder.java) 链式构建，`build()` 产出 `[普通, 完美]` 双输出；`MULTI_STOVE` 配方走 `MultiStoverecipe`（带温度），其余走 `Shaped/ShapelessGastroRecipe`。`SimpleGastroFoodBuilder` 为其简化版（默认 shapeless、无工具需求）。

### 5.2 配方系统

- **配方类型** [GastroRecipeType](../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/recipes/GastroRecipeType.java)：`MULTI_STOVE`（多功能炉）、`CULINARY_WORKBENCH`（烹饪台）、`MILL`（磨坊）、`FERMENTER`（发酵机）、`HARVEST`（收获）、`BREAK`（破坏方块）、`KILL`（击杀）、`TRAP`（陷阱）。
- **配方维度**（区分于原版 Slimefun）：`ingredients`（原料，3×3）、`tools`（工具，可消耗耐久）、`container`（容器，如碗/桶）、`temperature`（仅 MULTI_STOVE：LOW/MEDIUM/HIGH 等）。
- **匹配**：`RecipeShape.SHAPED`（有序，位置敏感）/ `SHAPELESS`（无序）。匹配走 [GastroRecipe.matches()](../src/main/java/io/github/schntgaispock/gastronomicon/api/recipes/GastroRecipe.java)，结果为 `RecipeMatchResult`（区分 `isMatch` 与 `isCraftable`）。
- **注册**：`RecipeRegistry`；`ItemSetup` 中既用 builder 隐式注册，也用 `RecipeRegistry.registerRecipe(...)` 显式注册（如面粉→面团的多种替代配方）。

### 5.3 工作站

- **基类** [GastroWorkstation](../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/manual/GastroWorkstation.java)（继承 InfinityLib `MenuBlock`）。
  - **槽位布局**：原料 9 格（3×3）、工具 6 格、容器 2 格、输出 2 格；点击 `CRAFT_BUTTON_SLOT(53)` 合成。
  - **合成流程**：收集各槽 → 计算输入 hash → 命中缓存（`lastInputHashAndRecipe`，按方块 Location 缓存上次配方）则直接复用，否则 `findRecipe` 搜索 → 触发 `PlayerGastroFoodCraftEvent` → 计算产出（含完美食物判定）→ 扣原料/容器 → 放置产出。
  - 抽象方法：`getGastroRecipeType()`、`canCraft(...)`（检查能量/水等前置，**不**检查配方）。
- **手动工作站**：`CulinaryWorkbench`、`MultiStove`（带温度）、`Refrigerator`、`GrainMill`、`Fermenter`（含普通/大型）、`HuntingTrap`、`ChefAndroidTrainer`。
- **自动工作站**：`ElectricKitchen`（3 档，聚合烹饪台/炉/冰箱/磨坊/发酵机）、`FishingNet`（3 档）。

### 5.4 种子与作物

位于 [core/slimefun/items/seeds/](../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/seeds/)：

| 类 | 生长方式 |
|---|---|
| `SimpleSeed` | 简单种植，无独立作物物 |
| `CropSeed` | 作物型（绑 Minecraft 作物 Material，如 WHEAT/POTATOES） |
| `FruitingSeed` | 结果型（如南瓜茎，挂果到相邻方块，引用岛屿 id） |
| `VineSeed` | 藤蔓型（如香草） |
| `DuplicatingSeed` | 复制型（如芹菜） |
| `SimpleSapling` | 树苗型（生成树结构，见 `TreeStructure` / `schematics/`） |

- 种子/原料获取：破坏特定方块掉落、击杀特定生物掉落、渔网/陷阱获取（掉率见 [config.yml](../src/main/resources/config.yml#L17-L43)）。

## 6. 第三方集成

- **ExoticGarden**：`ItemSetup` 中以 `egAvailable` 控制依赖其原料的配方是否注册（缺失则不注册，玩家无感知）。
- **DynaTech**：[DynaTechSetup](../src/main/java/io/github/schntgaispock/gastronomicon/integration/DynaTechSetup.java) 向其培育仓注入本附属作物；**默认关闭**（`disable-dynatech-integration: true`）。
- **SlimeHUD**：[SlimeHUDSetup](../src/main/java/io/github/schntgaispock/gastronomicon/integration/SlimeHUDSetup.java) 注册 HUD 显示。

## 7. 配置项（config.yml）

| 路径 | 默认值 | 含义 |
|---|---|---|
| `options.auto-update` | `true` | 自动更新 |
| `disable-exotic-garden-recipes` | `false` | 禁用依赖异域花园的配方 |
| `disable-dynatech-integration` | `true` | 禁用动力科技作物集成 |
| `perfect-food-max-chance` | `0.25` | 完美食物最大概率（**注意：见下文待确认点**） |
| `proficiency-threshold` | `256` | 训练厨师机器人 / 计入档案所需熟练度 |
| `drops.block-break-chances` | — | 各方块掉落种子概率 |
| `drops.mob-kill-chances` | — | 各生物掉落物品概率 |
| `drops.disabled-in` | `[]` | 禁用掉落的世界列表 |
| `profile.titles` | — | 玩家头衔（按熟练度阈值分级） |

## 8. REF 参考（Slimefun4.1）

- [REF/Slimefun4.1](../REF/Slimefun4.1) 是适配 `1.21.1–1.21.11` 的非官方 Slimefun 维护分支（`4.9.5`），含中文本地化、移除联网上报、安全加固。
- **用途**：本附属适配高版本时，所有 Slimefun API 调用须以该源码为准。
- **约束**：`REF/` 整体未被 git 跟踪（`?? REF/`），**只读，禁止修改**。
- 其自带 [REF/Slimefun4.1/note/release/](../REF/Slimefun4.1/note/release)（4.9.3/4.9.4/4.9.5 版本说明）属参考项目自身记录，非本附属文档。

## 9. 待确认点（适配/一致性观察）

> 原记录的 3 个疑点已在 **1.1.4** 修复（详见 [release/1.1.4.md](release/1.1.4.md)）：

1. ~~**`perfect-food-max-chance` 似未被读取**~~：✅ 已修复。[GastroWorkstation](../src/main/java/io/github/schntgaispock/gastronomicon/core/slimefun/items/workstations/manual/GastroWorkstation.java) 现读取 [config.yml](../src/main/resources/config.yml#L12) 的 `perfect-food-max-chance`（默认 0.25）并钳制到 [0,1]。
2. ~~**MILK_BUCKET 疑似笔误**~~：✅ 已修复。[ItemSetup.java:117](../src/main/java/io/github/schntgaispock/gastronomicon/core/setup/ItemSetup.java#L117) 已改为 `Material.MILK_BUCKET`。
3. ~~**`_giveItem` / `giveItem(item, amount)`**~~：✅ 已修复。[FoodEffect](../src/main/java/io/github/schntgaispock/gastronomicon/api/food/FoodEffect.java) 的 amount 传参、完美食物 ×1.5 加成、`move()` 向量原地修改均已修正。

> 1.1.4 另修复了若干审计中发现的严重问题（工作站「幽灵堆」刷物品、命令写错玩家档案、
> HuntingTrap/FishingNet 异步并发、TreeStructure 启动 NPE 等），完整清单见
> [release/1.1.4.md](release/1.1.4.md)。

---

*维护说明：本文档随项目演进更新；每次版本发布在 `note/release/` 下新增 `<版本号>.md` 记录改动。*
