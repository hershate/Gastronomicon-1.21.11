# 依赖审计报告（Gastronomicon 1.1.5）

> 应「严格检查：除 Slimefun4.1 / Paper API / 1.21.11 服务端 API 外不得有其他依赖」之要求产出。
> 结论先行：**运行期依赖 footprint = 仅服务端提供的 API；其余代码全部 vendor（shaded）进插件 jar，自包含、不依赖服务器另装任何库；无匿名上报、无更新检查。**

## 审计方法

1. 读 [pom.xml](../pom.xml) 的全部 `<dependency>`（groupId/artifactId/scope）。
2. 对产物 `target/Gastronomicon v1.1.5.jar` 执行 `jar tf`，枚举所有打包进 jar 的包根。
3. 对全源码 `grep` 所有 `import`，逐一核对每个包根的归宿（jar 内 / 服务端提供 / 仅编译期）。
4. 反编译 InfinityLib `AbstractAddon`，确认更新检查的实例化与触发条件。

可复现：
```bash
JAVA_HOME=/d/Java/21
SHADED="target/Gastronomicon v1.1.5.jar"
"$JAVA_HOME/bin/jar" tf "$SHADED" | grep -oE '^[a-z][a-z0-9]*(/[a-z][a-z0-9_]*){0,2}' | sort -u
```

## 一、服务端提供（不在 jar 内，运行期由 1.21.11 服务端提供）✅ 允许

| 依赖 | scope | 服务端来源 |
|---|---|---|
| `io.papermc.paper:paper-api` 1.19.3 | provided | Paper 1.21.11 服务端 |
| `com.github.slimefun:Slimefun` 4.9.5（REF） | provided | REF Slimefun（服务端安装） |
| `SlimeHUD` 1.3.0 | provided + softdepend | 可选；代码 try/catch 守卫，缺失/不兼容静默跳过 |
| `org.projectlombok:lombok` | provided | 仅编译期（注解处理器），jar 内 0 条目 |

> 这些 import 的包根（`org.bukkit.*`、`io.papermc.*`、`net.kyori.*`、`io.github.thebusybiscuit.slimefun4.*`、`me.mrCookieSlime.*`、`com.destroystokyo.paper.*`、`net.md_5.bungee.*`、`com.google.gson.*`、`org.jetbrains.annotations`）均由服务端提供，**不打包进 jar**。

## 二、vendor 进 jar 的外部库（shaded，自包含）⚠️ 经用户决定保留

以下为当前 shade 进 jar 的非本插件代码。**它们运行期不需要服务器另装任何东西**（已在插件自己的 jar 内），因此不会引发 `NoClassDefFoundError` 类加载失败：

| 库 | jar 内条目 | 用途 | 是否可换为服务端 API |
|---|---|---|---|
| `com.fasterxml.jackson`（jackson-databind + annotations） | 878 | `TreeStructure` 树结构 JSON、`ChunkPDC` 区块 JSON | 可换服务端自带的 GSON（暂保留） |
| `io.github.schntgaispock.infinitylib`（InfinityLib，已重定向） | 27 | 插件地基：`AbstractAddon`/`MenuBlock`/`AddonConfig`，所有工作站基于它 | 移除需重写机器层（暂保留） |
| `javax.annotation`（jsr305） | 12 | `@Nonnull`/`@Nullable` 注解，33 个源文件 | 可移除注解（暂保留） |

> 用户已明确选择「保留现状 + 出审计报告」：上述库均为自包含 shaded，**不构成运行期外部依赖**，亦不会导致加载失败。

## 三、已移除项（本次审计/1.1.5 清理）

| 项 | 处置 | 原因 |
|---|---|---|
| ~~`GuizhanLibPlugin`~~（鬼斩前置库） | **1.1.5 移除** | 原 provided 依赖，服务器版本不含 `slimefun.addon.Scheduler` 致 `NoClassDefFoundError` 无法加载。4 处用法全部内联 |
| ~~`org.bstats`（bstats-bukkit）~~ | **1.1.5 移除** | 匿名上报（Metrics）。移除代码 + pom 依赖 + shade 重定向；jar 内现 **0** 条目 |
| ~~`GuizhanUpdater`（自动更新器）~~ | **1.1.5 移除** | 仅 Build 版本触发，release 从不执行；包路径同样版本敏感 |

## 四、更新检查功能状态（应「移除更新检查」要求）

- **本插件自带更新器**：`GuizhanUpdater.start(...)` 已于 1.1.5 删除（见 [release/1.1.5.md](release/1.1.5.md)）。
- **InfinityLib 基类的更新检查**（`GitHubBuildsUpdater`）：反编译 [AbstractAddon](../src/main/java/io/github/schntgaispock/gastronomicon/Gastronomicon.java) 构造器确认——**仅当 `getDescription().getVersion().matches("Build \\d+")` 时才实例化 updater**；release 版本号 `"1.1.5"` 不匹配 → `updater` 保持 `null` → `onEnable` 中两处 `updater.start()` 均被 `ifnull` 跳过。故 **release 永不联网检查更新**。
- 双保险：[config.yml](../src/main/resources/config.yml) `options.auto-update` 已置 `false`（该键为 InfinityLib 前置校验所需，保留但恒为 false）。
- 结论：**插件不会发起任何更新检查网络请求。**

## 五、匿名上报功能状态（应「移除匿名上报」要求）

- bStats `Metrics`（插件 id 16941）及 `SimplePie` 图表已从 [Gastronomicon.enable()](../src/main/java/io/github/schntgaispock/gastronomicon/Gastronomicon.java) 删除；pom 移除 `bstats-bukkit` 依赖与 `org.bstats` shade 重定向。
- 审计：shaded jar `grep -ci bstats` = **0**。
- 结论：**插件不发送任何匿名使用统计。**

## 最终结论

```
运行期依赖 = { Paper 1.21.11 服务端 API, Slimefun 4.1 (REF), 可选 SlimeHUD }
            ⊆ 「1.21.11 服务端提供的 API」 ✅
其余代码 = 全部 shaded 进插件自身 jar（jackson / InfinityLib / jsr305），自包含，无需服务器另装
匿名上报 = 无
更新检查 = 无
```
不存在任何会导致加载失败的「服务器缺失类」风险（guizhanlib 那类问题已根除）。
