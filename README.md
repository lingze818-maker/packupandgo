# Pack Up & Go (提桶跑路)

![License](https://img.shields.io/badge/License-MIT-green)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-blue)
![Forge](https://img.shields.io/badge/Forge-47.x-orange)

**Put your home in a bucket. Take it anywhere. Put it back down — exactly as it was.**

Pack Up & Go lets you capture a region of your world into a bucket item — blocks, block entities, **container contents, and even your tamed pets** — then release it somewhere else, fully intact. Move your home across the map, or into another dimension.

[中文说明见下方](#中文说明)

---

## ✨ Features

- **框选 → 装桶 → 展开** | Select → Capture → Place back
  Sneak-right-click two corners with the **Selection Wrench**, then right-click with a bucket. Your home is serialized into the bucket — including **chest contents, furnace fuel, sign text, crop growth stages**.
- **动物随迁** | **Tamed pets come along**
  Tamed wolves, cats, parrots, horses (named or not) and farm animals inside the region are packed with the house and respawn at the new location. Copy mode gives them fresh UUIDs; Move mode keeps their identity.
- **复制/移动双模式** | Copy / Move modes
  Default **Copy**: your original base stays untouched. **Shift+capture (Move)**: the region is cleared — bucket it and go.
- **跨维度展开** | **Place in another dimension**
  A filled bucket can be placed in the Overworld, the Nether, the End, or a modded dimension. See [limits](#cross-dimension-placement).
- **四档桶** | Four bucket tiers
  | Tier | Max size | Recipe |
  |------|----------|--------|
  | Wooden | 16³ | planks + bucket |
  | Iron | 32³ | iron ingot + bucket |
  | Golden | 48³ | gold ingot + bucket |
  | Star | 64³ | nether star + bucket |
- **加载状态一眼可辨** | Loaded state at a glance
  A loaded bucket shows a tiny house peeking out of the rim (Golden/Star tiers also glow enchanted). New buckets also name the dimension they were packed in.

## 📦 Installation

- Minecraft **1.20.1** + Forge **47.x**
- Drop the jar into your `mods` folder. That's it.

## 🛠️ Usage

1. Craft a **Selection Wrench** (iron ingot + stick)
2. **Sneak + right-click** two corners of the region you want to pack (both corners in the same dimension)
3. Right-click with a bucket tier that fits the size, still in that dimension
4. Travel anywhere — another part of the map, or another dimension — and right-click the ground. **Your home unfolds.** That place uses up the filled bucket; the same packed home cannot be placed from it again. Empty buckets still pack as before. If the spot is too short for the build height, nothing is written and the bucket is kept.

## Cross-dimension placement

Selection and packing stay single-dimension: the two corners, and the click that fills the bucket, have to be in the same world. After that the bucket is not locked to a dimension. Placement restores the same data as a same-dimension place: block states, block entities (containers, signs, furnaces), crop age, and tamed or farm animals.

The pack format is still `packupandgo.v1`. New buckets add an optional `source_dimension` field (also copied onto the stub of an oversized pack). It is a label, not a lock. Buckets packed before this field existed have no source dimension and still place.

What we refuse, and what vanilla still does after a successful place:

- **Build height.** The whole box has to sit inside the target dimension's build limits (Overworld y=-64–319, Nether and End y=0–255; modded dimensions use their own limits). If the house is taller than the dimension, or this anchor would stick out, the place is refused and the bucket is not consumed. The mod does not clip the house — clipping would drop chests and still use up the bucket. The chat tells you the y range that does fit.
- **World border.** The footprint has to sit inside that dimension's world border, or the place is refused the same way.
- **Water in hot dimensions (the Nether).** A water bucket evaporates there. A packed house does not: water is written back as a block, so it stays. It can still flow, and water meeting lava still makes stone. The chat says so. Wet sponges still dry immediately and may drink nearby water — that part is vanilla, and the chat warns you.
- **Beds.** Placed intact. In a dimension where beds do not work (the Nether and the End), right-clicking one explodes, same as vanilla. Don't click it. The chat warns you when the pack contains a bed.
- **Respawn anchors.** Placed intact. They explode on use in dimensions where anchors do not work (Overworld, the End).
- **Portals.** If vanilla removes a nether portal, end portal, or end gateway block while the rest of the house is still being pasted, that block is written once more after the frame is in place. A portal that survived the first pass is left alone. An incomplete nether portal can still be removed by vanilla; the chat counts those. End portal blocks teleport between the End and the Overworld from whichever dimension you placed them in. Nether portals still link the Nether and the Overworld.
- **Animals.** They are not rewritten. Hoglins and piglins still zombify after about 15 seconds outside a piglin-safe dimension. Striders are not removed: they shiver and slow down whenever they leave lava, in every dimension including the Nether. A cooler dimension does not kill them; leave lava underfoot if you want them to walk normally. A mob whose type is missing, or whose data will not load, is skipped; the blocks still place and the bucket is still consumed, with a count in chat.
- **After place.** Vanilla ticks still apply. Nether wart will not spread in the Overworld, ice can melt, chorus plants still need end stone. The saved block state itself is not edited.
- **Oversized packs on disk.** Files live at `saves/<world>/packupandgo/packs/pack-<id>.nbt`, on the world root — not under `data/`, and not under `DIM-1` or `DIM1`. Placing in another dimension reads that same file. The file does not have to contain a dimension key.

## 🧪 Compatibility

- Works in singleplayer and LAN
- No client-side requirement (server-side mod)
- Plays nice alongside most mods (vanilla serialization — modded machines are best-effort via NBT)

## 中文说明

**提桶跑路**——把家框选装进提桶，异地原样展开。可以在另一个维度放下。

- 框选扳手潜行右键定两角（两角必须在同一维度），提桶右键装桶（方块+容器内容+作物状态+驯服动物全保真）。装桶这一下也要在框选所在的维度
- 默认**复制模式**（原地保留），Shift+装桶为**移动模式**（原地清空，真·提桶跑路）
- 已装载的桶右键展开成功后即消耗，同一桶家不能再放第二次；空桶装桶流程不变。放到别的维度也是放下一次就消耗
- 四档桶容量：木 16³ / 铁 32³ / 金 48³ / 星 64³
- 装载状态：金/星桶泛附魔光，桶口探出小屋剪影。新装的桶会在提示里写上来源维度（主世界 / 下界 / 末地，模组维度显示 id）

### 跨维度展开

装进桶之后不锁维度。主世界、下界、末地、模组维度都能放。放下的内容和同维度一样：方块状态、方块实体（箱子、告示牌、熔炉）、作物年龄、驯服动物和农场动物。

格式仍是 `packupandgo.v1`。新桶多一个可选字段 `source_dimension`，只用来提示「装自哪」，不当门禁。没有这个字段的旧桶一样能放。超限落盘的桩上也会抄一份，tooltip 不用回磁盘。

放不下的情况（一格都不写，桶不消耗，聊天栏说明原因）：

- **建筑高度。** 整栋家必须落在目标维度的可建范围里（主世界 y=-64–319，下界和末地 y=0–255；模组维度用它自己的上下限）。家比这个维度还高，或者这次锚点会探出去，就拒绝。不裁切——裁掉的可能是箱子，桶却已经没了。聊天栏会告诉你这次会占的 y，以及这个维度允许的 y
- **世界边界。** 占地探出该维度的世界边界，同样整单拒绝

原样放下、但原版规则仍会接着生效的情况（会在聊天栏提醒，不改桶里的数据）：

- **下界里的水。** 水桶里的水会蒸发；这只桶是按方块写回去的，水**不会**蒸发。它仍可能流动，碰到岩浆会变成石头。湿海绵仍会立刻晾干，并可能吸走周围的水
- **床。** 原样放下。在床不能用的维度（下界、末地）右键会爆炸，别去点
- **重生锚。** 原样放下。在锚不能用的维度（主世界、末地）右键会爆炸
- **传送门。** 如果原版在房子还没写完时拆掉了下界传送门、末地传送门或末地折跃门，整栋写完后再补一次。第一遍就留下来的门不再写。框不在桶里的下界门，原版仍可能拆掉，聊天栏会报格数。末地传送门方块在任何维度踩上去都会按原版在末地和主世界之间传送。下界门仍连接下界和主世界
- **生物。** 不改 NBT。猪灵、猪灵兽在猪灵不安全的维度大约 15 秒后僵尸化。炽足兽不会被换维度弄死；离开岩浆会发抖、变慢（下界里离开岩浆也一样）。缺模组或数据读不了的生物会跳过；方块已经放下，桶仍消耗，聊天栏报只数
- **放下之后。** 原版的随机刻照常走。下界疣在主世界不蔓延，冰会化，紫颂花仍要末地石。存下来的方块状态本身不改

大件文件在 `saves/<世界>/packupandgo/packs/pack-<id>.nbt`，挂在世界根上，不在 `data/`，也不在 `DIM-1` / `DIM1`。换个维度放下读的是同一份文件，文件里可以没有维度字段。

## 📄 License

MIT — see [LICENSE](LICENSE)
