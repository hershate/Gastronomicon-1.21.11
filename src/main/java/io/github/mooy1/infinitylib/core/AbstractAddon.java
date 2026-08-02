package io.github.mooy1.infinitylib.core;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * 精简版 AbstractAddon。
 * <p>
 * 1.1.6 起 InfinityLib 不再作为外部 jar 依赖，而是把本插件实际使用到的极少部分
 * 以源码形式 vendor 进来并适配 REF Slimefun 4.9.5。相比原 InfinityLib，本类移除了：
 * <ul>
 *   <li>自动更新（{@code GitHubBuildsUpdater}）—— 已按需求移除更新检查；</li>
 *   <li>命令框架（{@code AddonCommand}）—— 本插件自带命令实现；</li>
 *   <li>{@code Environment} / 单元测试支持；</li>
 *   <li>shade 重定位自检（{@code InfinityLib.PACKAGE}）—— 已 vendor 为源码，不再需要。</li>
 * </ul>
 * 保留：生命周期 {@code load/enable/disable}、{@link AddonConfig} 配置、
 * {@code instance/config/log/createKey}、{@link SlimefunAddon} 实现。
 */
public abstract class AbstractAddon extends JavaPlugin implements SlimefunAddon {

    private static AbstractAddon instance;

    private final String bugTrackerURL;
    private AddonConfig config;
    private int slimefunTickCount;
    private boolean enabling;
    private boolean disabling;
    private boolean loading;

    public AbstractAddon(String githubUserName, String githubRepo, String autoUpdateBranch, String autoUpdateKey) {
        // githubUserName/Repo/Branch 仅用于构造 issue 反馈地址；autoUpdateKey 不再使用（更新检查已移除）。
        this.bugTrackerURL = "https://github.com/" + githubUserName + "/" + githubRepo + "/issues";
    }

    private void handle(RuntimeException e) {
        e.printStackTrace();
    }

    @Override
    public final void onLoad() {
        if (loading) {
            throw new IllegalStateException(getName() + " is already loading! Do not call super.onLoad()!");
        }
        loading = true;
        try {
            load();
        } catch (RuntimeException e) {
            handle(e);
        } finally {
            loading = false;
        }
    }

    @Override
    public final void onEnable() {
        if (enabling) {
            throw new IllegalStateException(getName() + " is already enabling! Do not call super.onEnable()!");
        }
        enabling = true;
        instance = this;

        try {
            config = new AddonConfig("config.yml");
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        // 按 Slimefun 的 tick 节奏累加全局计数（FishingNet 等按 tick 节流复检水浸状态）。
        // 原 InfinityLib 用其 Scheduler.repeat；此处直接用 Bukkit 调度器，等价。
        final int tickRate = Slimefun.getTickerTask().getTickRate();
        Bukkit.getScheduler().runTaskTimer(this, () -> slimefunTickCount++, tickRate, tickRate);

        try {
            enable();
        } catch (RuntimeException e) {
            handle(e);
        } finally {
            enabling = false;
        }
    }

    @Override
    public final void onDisable() {
        if (disabling) {
            throw new IllegalStateException(getName() + " is already disabling! Do not call super.onDisable()!");
        }
        disabling = true;
        try {
            disable();
        } catch (RuntimeException e) {
            handle(e);
        } finally {
            disabling = false;
            instance = null;
            config = null;
            slimefunTickCount = 0;
        }
    }

    /**
     * Called when the plugin is loaded
     */
    protected void load() {
    }

    /**
     * Called when the plugin is enabled
     */
    protected abstract void enable();

    /**
     * Called when the plugin is disabled
     */
    protected abstract void disable();

    @Override
    public final JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public final String getBugTrackerURL() {
        return bugTrackerURL;
    }

    @Override
    public final AddonConfig getConfig() {
        return instance().config;
    }

    @Override
    public final void reloadConfig() {
        instance().config.reload();
    }

    @Override
    public final void saveConfig() {
        instance().config.save();
    }

    @Override
    public final void saveDefaultConfig() {
        // 由 onEnable() 中的 AddonConfig 构造覆盖，此处无需操作
    }

    @SuppressWarnings("unchecked")
    public static <T extends AbstractAddon> T instance() {
        return (T) Objects.requireNonNull(instance, "Addon is not enabled!");
    }

    public static AddonConfig config() {
        return instance().getConfig();
    }

    @SuppressWarnings("unused")
    public static void log(Level level, String... messages) {
        Logger logger = instance().getLogger();
        for (String msg : messages) {
            logger.log(level, msg);
        }
    }

    /**
     * Creates a NameSpacedKey from the given string
     */
    public static NamespacedKey createKey(String s) {
        return new NamespacedKey(instance(), s);
    }

    /**
     * 自插件启用以来累计的 Slimefun tick 数（按 {@code Slimefun.getTickerTask().getTickRate()} 节奏递增）。
     */
    public static int slimefunTickCount() {
        return instance().slimefunTickCount;
    }

}
