package com.dwinovo.packupandgo.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 超限 pack 的磁盘持久化——「家太大 NBT 装不下」时,完整数据落世界存档目录,
 * 桶里只留一根指针(design §四 风险预登记①)。
 *
 * <p>文件位:{@code saves/<world>/data/packupandgo/pack-<id>.nbt}(gzip 压缩)。
 * 桶 NBT 里存 {@code external:true + pack_id + 摘要}——tooltip/尺寸照常直读,
 * 只有真正展开时才回磁盘取全文。
 *
 * <p>已知取舍(v1):桶被烧毁/丢弃时文件不回收(孤儿文件)。备份世界=连 data 目录
 * 一起备份,搬档不丢家。
 */
public final class PackStore {

    /** 内联阈值:压缩后超过 1.5MB 落盘(ItemStack NBT 安全上限 2MB 留余量)。 */
    static final int INLINE_LIMIT = 1_500_000;

    private PackStore() {}

    private static Path dir(ServerLevel level) {
        // 与 /packup 命令的 SNBT 存档同根(世界根/packupandgo/)——桶的落盘大件进 packs/ 子目录
        return level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("packupandgo").resolve("packs");
    }

    /** 序列化估重(gzip 后字节数)——落盘判断与写盘共用同一种编码,所见即所存。 */
    static int compressedSize(CompoundTag pack) {
        try {
            var bos = new java.io.ByteArrayOutputStream();
            NbtIo.writeCompressed(pack, bos);
            return bos.size();
        } catch (IOException e) {
            return Integer.MAX_VALUE; // 写不动的东西一律视为超限,交给落盘路径报错
        }
    }

    /**
     * pack 落盘,返回 id。调用方随后把「指针桩」写进桶 NBT(见 BucketData#loadInto)。
     */
    static String store(ServerLevel level, CompoundTag pack) throws IOException {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Path d = dir(level);
        Files.createDirectories(d);
        NbtIo.writeCompressed(pack, d.resolve("pack-" + id + ".nbt").toFile());
        return id;
    }

    /** 展开时回读;文件丢失(存档被裁/手工误删)抛 IOException,由交互层翻译成人话。 */
    static CompoundTag load(ServerLevel level, String id) throws IOException {
        var file = dir(level).resolve("pack-" + id + ".nbt").toFile();
        if (!file.isFile()) {
            throw new IOException("pack file missing: " + id);
        }
        return NbtIo.readCompressed(file);
    }
}
