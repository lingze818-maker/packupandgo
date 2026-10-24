# Pack Up & Go (提桶跑路)

![License](https://img.shields.io/badge/License-MIT-green)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-blue)
![Forge](https://img.shields.io/badge/Forge-47.x-orange)

**Put your home in a bucket. Take it anywhere. Put it back down — exactly as it was.**

Pack Up & Go lets you capture a region of your world into a bucket item — blocks, block entities, **container contents, and even your tamed pets** — then release it somewhere else, fully intact. Move your house across the map, or across dimensions*.

> \* Cross-dimension placement planned for v1.1

[中文说明见下方](#中文说明)

---

## ✨ Features

- **框选 → 装桶 → 展开** | Select → Capture → Place back
  Sneak-right-click two corners with the **Selection Wrench**, then right-click with a bucket. Your home is serialized into the bucket — including **chest contents, furnace fuel, sign text, crop growth stages**.
- **动物随迁** | **Tamed pets come along**
  Tamed wolves, cats, parrots, horses (named or not) and farm animals inside the region are packed with the house and respawn at the new location. Copy mode gives them fresh UUIDs; Move mode keeps their identity.
- **复制/移动双模式** | Copy / Move modes
  Default **Copy**: your original base stays untouched. **Shift+capture (Move)**: the region is cleared — bucket it and go.
- **四档桶** | Four bucket tiers
  | Tier | Max size | Recipe |
  |------|----------|--------|
  | Wooden | 16³ | planks + bucket |
  | Iron | 32³ | iron ingot + bucket |
  | Golden | 48³ | gold ingot + bucket |
  | Star | 64³ | nether star + bucket |
- **加载状态一眼可辨** | Loaded state at a glance
  A loaded bucket shows a tiny house peeking out of the rim (Golden/Star tiers also glow enchanted).

## 📦 Installation

- Minecraft **1.20.1** + Forge **47.x**
- Drop the jar into your `mods` folder. That's it.

## 🛠️ Usage

1. Craft a **Selection Wrench** (iron ingot + stick)
2. **Sneak + right-click** two corners of the region you want to pack
3. Right-click with a bucket tier that fits the size
4. Travel to the destination, right-click the ground — **your home unfolds**

## 🧪 Compatibility

- Works in singleplayer and LAN
- No client-side requirement (server-side mod)
- Plays nice alongside most mods (vanilla serialization — modded machines are best-effort via NBT)

## 中文说明

**提桶跑路**——把家框选装进提桶，异地原样展开。

- 框选扳手潜行右键定两角，提桶右键装桶（方块+容器内容+作物状态+驯服动物全保真）
- 默认**复制模式**（原地保留），Shift+装桶为**移动模式**（原地清空，真·提桶跑路）
- 四档桶容量：木 16³ / 铁 32³ / 金 48³ / 星 64³
- 装载状态：金/星桶泛附魔光，桶口探出小屋剪影

## 📄 License

MIT — see [LICENSE](LICENSE)
