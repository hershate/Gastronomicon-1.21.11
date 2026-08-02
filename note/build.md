# 构建指南（note/build.md）

> 本文档记录 Gastronomicon 适配 REF Slimefun 4.9.5（适配 1.21.11）的构建环境与编译要点。
> 适配仍在进行中，部分章节会随进度补充。

## 1. 构建环境

| 组件 | 版本 / 路径 | 说明 |
|---|---|---|
| JDK | Oracle JDK 21.0.12，位于 `D:\Java\21` | `JAVA_HOME=/d/Java/21`（bash 风格）。项目 source/target=17，JDK 21 可编译 |
| Maven | 3.9.9，手动下载解压到 `C:\Users\<用户>\apache-maven-3.9.9` | 系统原本未安装 Maven |
| Maven 镜像 | 阿里云 central 镜像，`~/.m2/settings.xml` | 加速 central 仓库下载（国外源约 90KB/s） |

### 1.1 安装 Maven（本机当时无 mvn）
```bash
curl -L -o /tmp/maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
tar -xzf /tmp/maven.tar.gz -C /c/Users/14212   # 解压到 ~/apache-maven-3.9.9
```
后续每次调用需显式指定：
```bash
export JAVA_HOME=/d/Java/21 MAVEN_OPTS="-Xmx2048m"
/c/Users/14212/apache-maven-3.9.9/bin/mvn <goal>
```

### 1.2 阿里云镜像（`~/.m2/settings.xml`）
```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```
> `mirrorOf=central` 只镜像 central；jitpack、papermc 等仓库仍走原地址（InfinityLib、SlimeHUD、GuizhanLibPlugin 等从 jitpack/papermc 下载）。

## 2. REF Slimefun 的本地安装

按需求，Slimefun 使用 `REF/Slimefun4.1`（非官方维护分支，版本 `4.9.5`，适配 1.21.1–1.21.11）。需先把它编译安装到本地 Maven 仓库：

```bash
export JAVA_HOME=/d/Java/21 MAVEN_OPTS="-Xmx2048m"
cd "d:/Github/repo/Gastronomicon-1.21.11/REF/Slimefun4.1"
/c/Users/14212/apache-maven-3.9.9/bin/mvn install -DskipTests -B
```
- 产物坐标：`com.github.slimefun:Slimefun:4.9.5`（注意 groupId/artifactId 与原 jitpack 的 `com.github.SlimefunGuguProject:Slimefun4` 不同）
- 安装到 `~/.m2/repository/com/github/slimefun/Slimefun/4.9.5/`
- dough-api 已被 shade 进 jar（包 `io.github.bakedlibs.dough`），不再作为传递依赖

## 3. pom.xml 改动

将 Slimefun 依赖从汉化组 jitpack 版切换到 REF：
```xml
<dependency>
    <groupId>com.github.slimefun</groupId>
    <artifactId>Slimefun</artifactId>
    <version>4.9.5</version>
    <scope>provided</scope>
</dependency>
```
（原 `com.github.SlimefunGuguProject:Slimefun4:2024.3.1` 及其 dough-api exclusion 已移除）

## 4. 编译命令

```bash
export JAVA_HOME=/d/Java/21 MAVEN_OPTS="-Xmx2048m"
cd "d:/Github/repo/Gastronomicon-1.21.11"
/c/Users/14212/apache-maven-3.9.9/bin/mvn compile -B        # 仅编译
/c/Users/14212/apache-maven-3.9.9/bin/mvn package -B         # 编译+打包（生成 jar）
```

## 5. API 适配要点（REF 4.9.5 vs 汉化组 2024.3.1）

REF 基于官方 experimental 分支，与汉化组版本存在深度 API 差异。已适配/适配中的项：

| # | 差异 | 涉及 | 状态 |
|---|---|---|---|
| 1 | `StorageCacheUtils`（汉化组）→ `BlockStorage` | 8 文件 | ✅ |
| 2 | `Slimefun.getDatabaseManager()...removeBlock()` → `BlockStorage.clearBlockInfo()` | 5 处 | ✅ |
| 3 | 内联 `new ItemStack[]{...}`（含 SlimefunItemStack 元素）→ `RecipeUtil.items(...)` | ItemSetup 等 | ✅ |
| 4 | `RecipeUtil` 改造：方法参数改 `Object`，新增 `toStack()`/`items()`（SlimefunItemStack 经 `asOne()` 转） | RecipeUtil | ✅ |
| 5 | `new CustomItemStack(...)` → `CustomItemStack.create(...)`（REF 改为 final 工具类） | Climate/MultiStove/GastroRecipe/GastroStacks 等 | ✅ |
| 6 | `SlimefunItemStack` 不再继承 `ItemStack`（核心）：构造参数/builder/字段/instanceof 等全面迁移 | 多文件 | ✅ |
| 7 | `NewBlockStorageUtil`（GuizhanLibPlugin，依赖汉化组 `SlimefunBlockData`）→ `BlockStorage` | TreeStructure/SeedListener/HuntingTrap/TreeGrowthListener/SimpleSeed | ✅ |
| 8 | `GastroFood.getItem()` 协变覆盖冲突；`instanceof SlimefunItemStack` 在 ItemStack 上失效 | GastroFood/GastroWorkstation/RecipeComponent | ✅ |
| 9 | `Research.addItems(SlimefunItemStack...)` 失效（REF 改为 `SlimefunItem...`/`ItemStack...`）→ 用 `RecipeUtil.items()` 转 | ResearchSetup | ✅ |
| 10 | `GroupRecipeComponent(NamespacedKey, SlimefunItemStack...)` 失效 → 调用处 `.asOne()` | GastroRecipeGroups | ✅ |
| 11 | 直接构造 `ShapedGastroRecipe`/`MultiStoveRecipe` 传入 SFIS 产出/工具 → 调用处 `.asOne()` | ItemSetup（6 处） | ✅ |
| 12 | `DynaTechSetup` 缺少 `RecipeUtil` import | DynaTechSetup | ✅ |

### 5.1 关键 REF API 速查
- `SlimefunItemStack` 转 `ItemStack`：`sfis.asOne()`（带 SF id 副本）、`sfis.asQuantity(n)`
- `SlimefunItemStack.getItem()` 返回 **SlimefunItem**（不是 ItemStack！）；`SlimefunItem.getItem()` 返回 ItemStack
- `BlockStorage`：`getInventory(loc)`、`check(loc)→SlimefunItem`、`checkID(loc)→String`、`getLocationInfo(loc,key)→String`、`addBlockInfo(loc,key,val)`、`clearBlockInfo(loc)`、`hasBlockInfo(loc)`、`store(Block, String id)`
- `CustomItemStack.create(Material, String name, String... lore)`（静态）
- `SlimefunItem.getByItem(ItemStack)→SlimefunItem`（判空，判断 ItemStack 是否 SF 物品）

## 6. 编译结果与待办

完整构建已验证通过：`mvn package -DskipTests` → **BUILD SUCCESS**（约 21s），生成：
- `target/Gastronomicon v1.1.3.jar`（shaded，约 2.19 MB，**部署用**）
- `target/original-Gastronomicon v1.1.3.jar`（shade 前，约 261 KB）

> **1.1.6 更新**：移除全部外部依赖后，产物 `target/Gastronomicon v1.1.6.jar` 缩至 **约 277 KB**（shaded），
> 仅含插件自身代码 + vendor 的 InfinityLib（重定向）；`com/*` 等服务端库 0 打包。
> 完整依赖审计见 [dependency-audit.md](dependency-audit.md)，改动见 [release/1.1.6.md](release/1.1.6.md)。

剩余遗留项：

- ~~**FoodEffect.java**：4 处 deprecation 警告（GuizhanLibPlugin 的 `PotionEffectTypeHelper`、`ItemStackHelper`）~~ — **1.1.5 已彻底解决**：鬼斩前置库运行期依赖整体移除，相关 import 与 API 调用全部内联（见 [release/1.1.5.md](release/1.1.5.md)），不再有此类警告。
- **运行时风险点**（未验证，需在服务器中实测）：
  - `BlockStorage.store(Block, String)` 是否等价于原 `NewBlockStorageUtil.createBlock` 的行为（树木果实方块、种子放置、种子传播）。
  - `SlimefunItem.getByItem(component)` 在 `SingleRecipeComponent`/`GroupRecipeComponent` 中匹配是否与原 `instanceof SlimefunItemStack` 语义一致。
  - `GastroWorkstation` 熟练度/完美食物分支：原逻辑用 `instanceof SlimefunItemStack` 判断产出，现改为 `SlimefunItem.getByItem(recipeOutputs[0]) != null`，语义一致但需验证无 SF 物品产出时不会误判。

