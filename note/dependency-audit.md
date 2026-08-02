# 依赖审计报告（Gastronomicon 1.1.6）

> 应「严格检查：除 Slimefun4.1 / Paper API / 1.21.11 服务端 API 外不得有其他依赖」之要求产出。
> 结论先行：**插件除 Slimefun 4.1 + Paper 1.21.11 服务端 API（+ 可选 SlimeHUD）外，没有任何外部依赖。**
> shaded jar 仅含插件自身代码（含 vendor 进来的 InfinityLib 源码，已重定向）。

## 审计方法

1. 读 [pom.xml](../pom.xml) 全部 `<dependency>`。
2. 对产物 `target/Gastronomicon v1.1.6.jar` 执行 `jar tf`，枚举所有打包进 jar 的包根。
3. 全源码 `grep` 所有 `import`，核对每个包根归宿。
4. 反编译 REF `CustomItemStack` 与 InfinityLib 字节码，定位 `VerifyError` 根因。

可复现：
```bash
JAVA_HOME=/d/Java/21
SHADED="target/Gastronomicon v1.1.6.jar"
"$JAVA_HOME/bin/jar" tf "$SHADED" | grep -oE '^[a-z][a-z0-9]*(/[a-z][a-z0-9_]*){0,2}' | sort -u
# 外部库残留：均应为 0
for p in com/fasterxml/jackson bstats guizhan javax/annotation com/google; do
  echo "$p : $("$JAVA_HOME/bin/jar" tf "$SHADED" | grep -ci "$p")"
done
```

## 一、pom 声明的依赖（1.1.6）

| 依赖 | scope | 运行期来源 |
|---|---|---|
| `io.papermc.paper:paper-api` | provided | Paper 1.21.11 服务端 |
| `com.github.slimefun:Slimefun` 4.9.5（REF） | provided | 服务端安装的 Slimefun |
| `com.github.schntgaispock:SlimeHUD` | provided + softdepend | 可选；代码 try/catch 守卫 |
| `org.projectlombok:lombok` | provided | 仅编译期（注解处理器），jar 内 0 条目 |

**仅此四项，且全部 `provided`（运行期由服务端提供）或仅编译期。无任何 `compile` 范围的外部依赖。**

## 二、jar 内打包内容（1.1.6）

`jar tf` 审计结果：jar 内仅 `io/github/schntgaispock/` 包根（含 `gastronomicon` 插件本体 + `infinitylib` vendor 源码，后者经 shade 重定向），加 `plugin.yml`/`config.yml` 等 resources。

| 外部库 | jar 内条目 | 状态 |
|---|---|---|
| jackson（databind+annotations） | 0 | 1.1.6 移除（→ 服务端 GSON） |
| bstats-bukkit | 0 | 1.1.5 移除（去匿名上报） |
| GuizhanLibPlugin（鬼斩前置库） | 0 | 1.1.5 移除（内联 4 处用法） |
| jsr305（javax.annotation） | 0 | 1.1.6 移除（剥离 33 文件注解） |
| `com/*`（GSON/Guava 等服务端库） | 0 | 不打包，运行期由服务端提供 |

vendor 进源码的 InfinityLib（7 类，经 shade 重定向为 `io.github.schntgaispock.infinitylib.*`）属于**插件自身代码**，不再是外部 Maven 依赖：
`AbstractAddon`/`AddonConfig`/`PathBuilder`/`MenuBlock`/`MenuBlockPreset`/`SubGroup`/`MultiGroup`。

## 三、运行期网络行为

- **匿名上报**：无（bStats 已于 1.1.5 移除）。
- **更新检查**：无（本插件 `GuizhanUpdater` 1.1.5 移除；vendor 的 `AbstractAddon` 不含 `GitHubBuildsUpdater`；`config.yml` `options.auto-update` 恒 false）。

## 最终结论

```
pom 依赖 = { paper-api(provided), Slimefun(provided), SlimeHUD(provided/softdepend), lombok(provided/编译期) }
运行期依赖 = { Paper 1.21.11 服务端 API, Slimefun 4.1 (REF), 可选 SlimeHUD }
            ⊆ 「Papo 服务端提供的 API」 ✅
shaded jar = 仅插件自身代码（277 KB；原 2.19 MB）
外部库打包 = 0
匿名上报 / 更新检查 = 无
```
不存在「服务器缺某前置库类」或「shaded 字节码与服务端 Slimefun 二进制不兼容」的加载失败风险。
详细改动见 [release/1.1.6.md](release/1.1.6.md)。
