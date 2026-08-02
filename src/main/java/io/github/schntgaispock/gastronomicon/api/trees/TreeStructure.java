package io.github.schntgaispock.gastronomicon.api.trees;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import io.github.schntgaispock.gastronomicon.Gastronomicon;
import io.github.schntgaispock.gastronomicon.util.NumberUtil;
import io.github.schntgaispock.gastronomicon.util.item.HeadTextures;
import io.github.thebusybiscuit.slimefun4.libraries.dough.skins.PlayerHead;
import io.github.thebusybiscuit.slimefun4.libraries.dough.skins.PlayerSkin;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public final class TreeStructure {

    // 1.1.6: jackson -> GSON（Paper 服务端自带 com.google.gson），不再 shaded 外部依赖。
    // 用自定义 JsonDeserializer 显式调用构造器，保留 fruitTexture 的派生逻辑与 final 字段。
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(TreeStructure.class, (JsonDeserializer<TreeStructure>) TreeStructure::deserialize)
        .create();

    private static final String TREE_SCHEMATIC_PATH = "plugins/Gastronomicon/schematics/";

    private static final @Getter Map<String, TreeStructure> loadedTrees = new HashMap<>();

    private static TreeStructure deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext ctx) {
        final JsonObject o = json.getAsJsonObject();
        return new TreeStructure(
            ctx.deserialize(o.get("blocks"), int[][][].class),
            o.has("sapling") ? o.get("sapling").getAsString() : null,
            o.has("fruit") ? o.get("fruit").getAsString() : null,
            ctx.deserialize(o.get("palette"), String[].class),
            ctx.deserialize(o.get("root"), int[].class)
        );
    }

    public static void loadTrees() {
        final File treePath = new File(TREE_SCHEMATIC_PATH);
        if (!treePath.exists()) {
            treePath.mkdirs();
        }

        // schematic 文件由 SimpleSapling 在 ItemSetup 阶段（早于本方法）从 jar 解包，
        // 此处仅负责读取。目录不存在/不可读时 listFiles() 返回 null，必须判空以免 NPE。
        final File[] treeFiles = treePath.listFiles();
        if (treeFiles == null) {
            Gastronomicon.info("已加载所有树木结构 (目录为空或不可读)");
            return;
        }
        for (File treeFile : treeFiles) {
            if (!treeFile.isFile()) {
                continue;
            }
            try (FileReader reader = new FileReader(treeFile)) {
                final TreeStructure tree = GSON.fromJson(reader, TreeStructure.class);
                if (tree == null || tree.getSapling() == null) {
                    continue;
                }
                loadedTrees.put(tree.getSapling(), tree);
            } catch (Exception e) {
                Gastronomicon.log(Level.WARNING, "无法加载树木结构文件: " + treeFile.getName());
                e.printStackTrace();
            }
        }

        Gastronomicon.info("已加载所有树木结构");
    }

    private final int[][][] blocks;
    private final String sapling;
    private final String fruit;
    private final String[] palette;
    private final int[] root; // [x, z]
    private final String fruitTexture;

    public TreeStructure(int[][][] blocks, String sapling, String fruit, String[] palette, int[] root) {
        this.blocks = blocks;
        this.sapling = sapling;
        this.fruit = fruit;
        this.palette = palette;
        this.root = root;

        this.fruitTexture = switch (fruit) { // how tf do you get texture from an item
            case "GN_BANANA" -> HeadTextures.BANANA;
            case "GN_LYCHEE" -> HeadTextures.LYCHEE;
            case "GN_VANILLA_BEANS" -> HeadTextures.VANILLA;
            default -> null;
        };
    }

    public void build(Location l, String sapling) {
        final int[][][] structure = getBlocks();
        final int[] root = getRoot();
        final String[] palette = getPalette();

        // schematic 来自磁盘（可被任意修改），使用前必须校验，避免越界/异常导致生成中途崩溃
        if (structure == null || structure.length == 0
            || structure[0] == null || structure[0].length == 0
            || structure[0][0] == null || structure[0][0].length == 0
            || root == null || root.length < 2
            || palette == null) {
            Gastronomicon.log(Level.WARNING, "树木结构数据不完整，跳过生成: " + sapling);
            return;
        }

        final int rootX = root[0];
        final int rootZ = root[1];

        for (int y = 0; y < structure.length; y++) {
            final int[][] plane = structure[y];
            if (plane == null) continue;
            for (int z = 0; z < plane.length; z++) {
                final int[] row = plane[z];
                if (row == null) continue;
                for (int x = 0; x < row.length; x++) {
                    final int id = row[x];
                    if (id <= 0) {
                        continue;
                    }
                    int newX = l.getBlockX() + x - rootX;
                    int newY = l.getBlockY() + y;
                    int newZ = l.getBlockZ() + z - rootZ;
                    if (id == 1) {
                        Block b = l.getWorld().getBlockAt(newX, newY, newZ);
                        b.setType(Material.PLAYER_HEAD);
                        if (fruitTexture != null) PlayerHead.setSkin(b, PlayerSkin.fromBase64(fruitTexture), false);
                        BlockStorage.store(b, getFruit());
                    } else {
                        final int paletteIndex = id - 2;
                        if (paletteIndex < 0 || paletteIndex >= palette.length) {
                            continue;
                        }
                        final String paletteEntry = palette[paletteIndex];
                        final Block b2 = l.getWorld().getBlockAt(newX, newY, newZ);
                        if (paletteEntry != null && paletteEntry.endsWith("LEAVES") && NumberUtil.flip(0.1)) {
                            BlockStorage.store(b2, sapling);
                        }
                        final Material material = Material.matchMaterial(paletteEntry);
                        if (material != null) {
                            b2.setType(material);
                        }
                    }
                }
            }
        }
    }
}
