package com.dwinovo.packupandgo.command;

import com.dwinovo.packupandgo.PackUpAndGoMod;
import com.dwinovo.packupandgo.capture.RegionCapture;
import com.dwinovo.packupandgo.capture.RegionRestore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * /packup 命令族——W1 的测试入口(物品交互是 W2 的事,先用命令把内核跑通)。
 *
 * <ul>
 *   <li>{@code /packup capture <pos1> <pos2> [name]} — 采集区域落盘 .snbt(可读可手改)
 *   <li>{@code /packup list} — 列出已存档
 * </ul>
 *
 * <p>存档位置:世界目录下 {@code packupandgo/}。SNBT 文本格式:验收时人眼可查
 * (箱子里的钻石几颗,打开文件数得出来),也让 W2 的桶 NBT 走同一种字节。
 */
public final class PackUpCommands {

    private PackUpCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("packup")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("capture")
                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                        .executes(ctx -> doCapture(ctx, defaultName()))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> doCapture(ctx,
                                                        StringArgumentType.getString(ctx, "name"))))))) // Brigadier 尾参语法:word 名紧跟坐标后
                .then(Commands.literal("list")
                        .executes(PackUpCommands::doList))
                .then(Commands.literal("restore")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("anchor", BlockPosArgument.blockPos())
                                        .executes(ctx -> doRestore(ctx,
                                                StringArgumentType.getString(ctx, "name")))))));
    }

    private static String defaultName() {
        return "pack-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd-HHmmss"));
    }

    private static int doCapture(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            BlockPos pos1 = BlockPosArgument.getLoadedBlockPos(ctx, "pos1");
            BlockPos pos2 = BlockPosArgument.getLoadedBlockPos(ctx, "pos2");
            // 尺寸防呆:任意一维 >64(星旗舰桶上限)直接拒——不是不能采,是不该有这么大的桶
            int dx = Math.abs(pos1.getX() - pos2.getX()) + 1;
            int dy = Math.abs(pos1.getY() - pos2.getY()) + 1;
            int dz = Math.abs(pos1.getZ() - pos2.getZ()) + 1;
            if (Math.max(dx, Math.max(dy, dz)) > 64) {
                ctx.getSource().sendFailure(Component.literal(
                        String.format("[PackUp&Go] 区域 %d×%d×%d 超上限——最大桶 64³,再大该分几桶提", dx, dy, dz)));
                return 0;
            }
            ServerLevel level = ctx.getSource().getLevel();

            RegionCapture.Result r = RegionCapture.capture(level, pos1, pos2);

            Path dir = storeDir(ctx.getSource());
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".snbt");
            Files.writeString(file, r.root().toString(), StandardCharsets.UTF_8);

            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format("[PackUp&Go] 已装桶 %s: %d 格(实体方块 %d,方块实体 %d) → %s",
                            name, r.totalCells(), r.blockCount(), r.blockEntityCount(),
                            file.getFileName())), false);
            PackUpAndGoMod.LOG.info("captured {} → {}", name, file);
            return r.blockCount();
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[PackUp&Go] 写盘失败: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[PackUp&Go] 采集失败: " + e));
            PackUpAndGoMod.LOG.error("capture failed", e);
            return 0;
        }
    }

    private static int doRestore(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            Path file = storeDir(ctx.getSource()).resolve(name + ".snbt");
            if (!Files.exists(file)) {
                ctx.getSource().sendFailure(Component.literal("[PackUp&Go] 没有这桶: " + name
                        + "(用 /packup list 看看)"));
                return 0;
            }
            net.minecraft.nbt.CompoundTag root = net.minecraft.nbt.TagParser.parseTag(
                    Files.readString(file, StandardCharsets.UTF_8));
            BlockPos anchor = BlockPosArgument.getLoadedBlockPos(ctx, "anchor");
            ServerLevel lvl = ctx.getSource().getLevel();

            // 高度/边界不够就整单拒绝,先于覆盖预告——别说「将要覆盖」结果一格没写。
            String rejection = RegionRestore.rejectionReason(lvl, anchor, root);
            if (rejection != null) {
                ctx.getSource().sendFailure(Component.literal("[PackUp&Go] " + rejection));
                return 0;
            }

            // 落点碰撞预告:目标区域已有多少非空格——不拦(OP 场景自己看着办),但要说
            net.minecraft.nbt.ListTag sizeTag = root.getList("size", net.minecraft.nbt.Tag.TAG_INT);
            BlockPos farCorner = new BlockPos(
                    anchor.getX() + sizeTag.getInt(0) - 1,
                    anchor.getY() + sizeTag.getInt(1) - 1,
                    anchor.getZ() + sizeTag.getInt(2) - 1);
            int occupied = 0;
            for (BlockPos p : BlockPos.betweenClosed(anchor, farCorner)) {
                if (!lvl.getBlockState(p).isAir()) {
                    occupied++;
                }
            }
            if (occupied > 0) {
                final int occ = occupied;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        String.format("[PackUp&Go] 注意:落点区域已有 %d 格非空,展开将原样覆盖(空气格清地形)", occ)), false);
            }

            RegionRestore.Result r = RegionRestore.restore(lvl, anchor, root);
            if (r.rejected()) {
                ctx.getSource().sendFailure(Component.literal("[PackUp&Go] " + r.rejection()));
                return 0;
            }

            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format("[PackUp&Go] 已展开 %s @ %s: 写入 %d 格(方块实体 %d,跳过 %d)",
                            name, anchor.toShortString(), r.placedBlocks(),
                            r.restoredBlockEntities(), r.skippedCells())), false);
            for (String note : r.notices()) {
                String line = note;
                ctx.getSource().sendSuccess(() -> Component.literal("[PackUp&Go] " + line)
                        .withStyle(ChatFormatting.YELLOW), false);
            }
            PackUpAndGoMod.LOG.info("restored {} @ {}", name, anchor);
            return r.placedBlocks();
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[PackUp&Go] 读盘失败: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[PackUp&Go] 展开失败: " + e));
            PackUpAndGoMod.LOG.error("restore failed", e);
            return 0;
        }
    }

    private static int doList(CommandContext<CommandSourceStack> ctx) {
        try (Stream<Path> s = Files.list(storeDir(ctx.getSource()))) {
            List<String> names = s.filter(p -> p.getFileName().toString().endsWith(".snbt"))
                    .map(p -> p.getFileName().toString().replace(".snbt", ""))
                    .sorted()
                    .toList();
            if (names.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("[PackUp&Go] 还没有任何桶"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "[PackUp&Go] 已存 " + names.size() + " 桶: " + String.join(", ", names)), false);
            }
            return names.size();
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[PackUp&Go] 读目录失败: " + e.getMessage()));
            return 0;
        }
    }

    /** 世界根/packupandgo/ —— 不按维度分目录。超限桶的大件在同根 packs/ 子目录。 */
    static Path storeDir(CommandSourceStack src) {
        return src.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve(PackUpAndGoMod.MOD_ID);
    }
}
