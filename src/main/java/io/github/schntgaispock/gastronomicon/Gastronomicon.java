package io.github.schntgaispock.gastronomicon;


import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import io.github.mooy1.infinitylib.core.AbstractAddon;
import io.github.mooy1.infinitylib.core.AddonConfig;
import io.github.schntgaispock.gastronomicon.api.trees.TreeStructure;
import io.github.schntgaispock.gastronomicon.core.setup.CommandSetup;
import io.github.schntgaispock.gastronomicon.core.setup.ListenerSetup;
import io.github.schntgaispock.gastronomicon.core.setup.ResearchSetup;
import io.github.schntgaispock.gastronomicon.core.setup.ItemSetup;
import io.github.schntgaispock.gastronomicon.integration.DynaTechSetup;
import io.github.schntgaispock.gastronomicon.integration.SlimeHUDSetup;
import io.github.schntgaispock.gastronomicon.util.StringUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

@Getter
public class Gastronomicon extends AbstractAddon {

    private static @Getter Gastronomicon instance;

    private AddonConfig playerData;
    private AddonConfig customFood;

    public Gastronomicon() {
        super("SlimefunGuguProject", "Gastronomicon", "master", "options.auto-update");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void enable() {
        instance = this;

        // 1.1.5: 移除对 GuizhanLibPlugin 的运行期依赖。原 Scheduler / GuizhanUpdater /
        // ItemStackHelper / PotionEffectTypeHelper 均为鬼斩前置库提供的类，不同版本间包路径
        // （net.guizhanss.guizhanlib.* ↔ net.guizhanss.guizhanlibplugin.* / ...gugu...）
        // 会迁移/删除，导致服务器安装的版本与本插件编译期不一致时抛 NoClassDefFoundError
        // 而无法加载（#issue: Papo 服务端缺少 slimefun.addon.Scheduler）。现已全部内联实现，
        // 插件自包含、与鬼斩前置库版本无关。原 Scheduler.run == Bukkit.runTask（同步下一 tick）。

        getLogger().info("#======================================#");
        getLogger().info("#    Gastronomicon by SchnTgaiSpock    #");
        getLogger().info("#   美食家    粘液科技简中汉化组汉化   #");
        getLogger().info("#======================================#");

        // 1.1.5: 移除匿名上报（bStats Metrics）与更新检查。
        //   - 匿名上报：原 new Metrics(this, 16941) + SimplePie 已删除，pom 移除 bstats 依赖与 shade 重定向。
        //   - 更新检查：本插件自带的 GuizhanUpdater 已于 1.1.5 移除；InfinityLib AbstractAddon 的
        //     GitHubBuildsUpdater 仅对 "Build NN" 开发版实例化（反编译确认构造器 ifeq 跳过），
        //     release "1.1.5" 版本号不匹配 → updater 保持 null → onEnable 中两处 start() 均被
        //     ifnull 跳过，故 release 永不联网检查更新；config options.auto-update 亦置 false 双保险。

        ItemSetup.setup();
        ResearchSetup.setup();
        ListenerSetup.setup();
        CommandSetup.setup();

        if (isPluginEnabled("SlimeHUD")) {
            try {
                info("检测到服务器已安装 SlimeHUD!");
                info("接入相关功能...");
                SlimeHUDSetup.setup();
            } catch (Exception e) {
                warn("该服务器安装的 SlimeHUD 版本不兼容");
                warn("请更新 SlimeHUD 至最新版本!");
            }
        }

        // If disable-exotic-garden-recipes is true "!" will change it to false and the rest of the code won't run.
        // If disable-exotic-garden-recipes is false "!" will change it to true and the rest of the code will run checking for ExoticGarden.

        if (!getConfig().getBoolean("disable-exotic-garden-recipes") && !isPluginEnabled("ExoticGarden")) {
            warn("检测到服务器未安装 异域花园(ExoticGarden)!");
            warn("需要异域花园物品的配方将被隐藏。");
        }

        if (isPluginEnabled("DynaTech") && !getConfig().getBoolean("disable-dynatech-integration", true)) {
            try {
                info("检测到服务器已安装 动力科技(DynaTech)!");
                info("正在向动力科技添加相关作物...");
                DynaTechSetup.setup();
            } catch (Exception e) {
                warn("该服务器安装的 DynaTech 版本不兼容");
                warn("请更新 DynaTech 至最新版本!");
            }
        }

        playerData = new AddonConfig("player.yml");
        customFood = new AddonConfig("custom-food.yml");

        TreeStructure.loadTrees();
    }

    @Override
    public void disable() {
        instance = null;
        if (getPlayerData() != null) {
            getPlayerData().save();
        }
    }

    public static NamespacedKey key(String name) {
        return new NamespacedKey(Gastronomicon.getInstance(), name);
    }

    public static boolean isPluginEnabled(String name) {
        return getInstance().getServer().getPluginManager().isPluginEnabled(name);
    }

    public static int scheduleSyncDelayedTask(Runnable runnable, long delay) {
        return Bukkit.getScheduler().scheduleSyncDelayedTask(getInstance(), runnable, delay);
    }

    public static BukkitTask scheduleSyncRepeatingTask(Runnable runnable, long delay, long interval) {
        return Bukkit.getScheduler().runTaskTimer(getInstance(), runnable, delay, interval);
    }

    public static boolean checkPermission(Player player, String permissionNode, String message) {
        if (player.hasPermission(permissionNode)) {
            return true;
        }

        if (message != null)
            Gastronomicon.sendMessage(player, message);
        return false;

    }

    public static void info(String message) {
        getInstance().getLogger().info(message);
    }

    public static void warn(String message) {
        getInstance().getLogger().warning(message);
    }

    public static void error(String message) {
        getInstance().getLogger().severe(message);
    }

    public static void sendMessage(Player player, String message) {
        player.sendMessage(/* ChatColor.of("#c91df4") + "§l美食家§7§l> §7" + */ StringUtil.formatColors(message));
    }

    public static void sendMessage(Player player, Component message) {
        final Component text = Component.text()
            .content("美食家")
            .color(TextColor.color(0xc9, 0x1d, 0xf4))
            .decorate(TextDecoration.BOLD)
            .append(Component.text()
                .content(">")
                .color(TextColor.color(0xaa, 0xaa, 0xaa))
                .decorate(TextDecoration.BOLD)
                .appendSpace()
                .asComponent())
            .asComponent();
        player.sendMessage(text);
    }
}
