package com.example.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoHotbarClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("autohotbar", "main")
    );

    public static KeyMapping toggleKey;
    public static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.autohotbar.toggle",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
        ));

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.autohotbar.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
        ));

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("autohotbar", "hotbar_hud"),
            (graphics, deltaTracker) -> Renderer.renderHudHotbar(graphics)
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (toggleKey.consumeClick()) {
                Config.toggle();
            }

            while (openConfigKey.consumeClick()) {
                client.setScreen(new ModernConfigScreen(client.screen));
            }

            Engine.tick(client);
        });
    }

    // ==========================================================
    // 1. CONFIGURATION SYSTEM
    // ==========================================================
    public static class Config {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("autohotbar_fairplay.json").toFile();

        public static class Rule {
            public String ruleKind = "Type";
            public String category = "Sword";
            public String variant = "Best DPS";
            public String specificId = "";
            public String requiredEnchant = "";
            public int enchantLevel = 1;
            public boolean mustBeEnchanted = false;

            public Rule(String ruleKind, String category, String variant, String specificId) {
                this.ruleKind = ruleKind;
                this.category = category;
                this.variant = variant;
                this.specificId = specificId;
            }

            public String getDisplayText() {
                if ("Empty".equalsIgnoreCase(ruleKind)) return "Leave Slot Empty (Stop Rule)";
                if ("Specific".equalsIgnoreCase(ruleKind)) {
                    String base = "Item: " + (specificId.isEmpty() ? "None" : specificId.replace("minecraft:", ""));
                    if (!requiredEnchant.isEmpty()) base += " [" + requiredEnchant + " " + enchantLevel + "]";
                    return mustBeEnchanted ? base + " (Enchanted)" : base;
                }
                if ("Conditional".equalsIgnoreCase(ruleKind)) return "Conditional Branch";
                return category + " (" + variant + ")";
            }
        }

        public static class Data {
            public boolean enabled = true;
            public List<List<Rule>> slots = new ArrayList<>();

            public Data() {
                for (int i = 0; i < 9; i++) {
                    slots.add(new ArrayList<>());
                }
                slots.get(0).add(new Rule("Type", "Sword", "Best DPS", ""));
                slots.get(1).add(new Rule("Type", "Pickaxe", "Best Tier", ""));
                slots.get(2).add(new Rule("Type", "Axe", "Best Weapon (Attack)", ""));
                slots.get(3).add(new Rule("Type", "Shovel", "Best Tier", ""));
                slots.get(4).add(new Rule("Type", "Utility & Projectiles", "Torches / Light", ""));
                slots.get(5).add(new Rule("Type", "Block", "Hardest", ""));
                slots.get(6).add(new Rule("Type", "Block", "Most Count", ""));
                slots.get(7).add(new Rule("Type", "Food", "Best Saturation", ""));
                slots.get(8).add(new Rule("Type", "Utility & Projectiles", "Water Bucket", ""));
            }
        }

        public static Data data = new Data();

        public static void load() {
            if (FILE.exists()) {
                try (Reader reader = new FileReader(FILE)) {
                    Data loaded = GSON.fromJson(reader, Data.class);
                    if (loaded != null && loaded.slots != null && loaded.slots.size() == 9) {
                        data = loaded;
                        return;
                    }
                } catch (Exception ignored) {}
            }
            data = new Data();
            save();
        }

        public static void save() {
            try (Writer writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            } catch (Exception ignored) {}
        }

        public static boolean isEnabled() { return data.enabled; }
        public static void toggle() { data.enabled = !data.enabled; save(); }

        public static List<Rule> getRulesForSlot(int slot) {
            if (slot >= 0 && slot < data.slots.size()) return data.slots.get(slot);
            return new ArrayList<>();
        }

        public static void addRule(int slot, Rule rule) {
            if (slot >= 0 && slot < data.slots.size()) {
                data.slots.get(slot).add(rule);
                save();
            }
        }

        public static void removeRule(int slot, int ruleIndex) {
            if (slot >= 0 && slot < data.slots.size()) {
                List<Rule> list = data.slots.get(slot);
                if (ruleIndex >= 0 && ruleIndex < list.size()) {
                    list.remove(ruleIndex);
                    save();
                }
            }
        }
    }

    // ==========================================================
    // 2. ENGINE (FARM OPTIMIZED, STRICT DUAL HIGHLIGHTING)
    // ==========================================================
    public static class Engine {
        private static final int[] TARGET_HOTBAR_FOR_INV_SLOT = new int[36];
        private static final boolean[] HOTBAR_NEEDS_UPGRADE = new boolean[9];
        private static int tickCounter = 0;
        private static int lastInventoryHash = 0;

        public static void tick(Minecraft client) {
            if (!Config.isEnabled() || client.player == null) {
                Arrays.fill(TARGET_HOTBAR_FOR_INV_SLOT, -1);
                Arrays.fill(HOTBAR_NEEDS_UPGRADE, false);
                return;
            }

            tickCounter++;
            if (tickCounter % 4 != 0) return; // Evaluates at most 4x a second

            Inventory inv = client.player.getInventory();
            int hash = 1;
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                hash = 31 * hash + s.getItem().hashCode() + s.getCount();
            }

            if (hash == lastInventoryHash) return;
            lastInventoryHash = hash;

            evaluate(inv);
        }

        private static void evaluate(Inventory inv) {
            Arrays.fill(TARGET_HOTBAR_FOR_INV_SLOT, -1);
            Arrays.fill(HOTBAR_NEEDS_UPGRADE, false);
            Set<Integer> claimedInvSlots = new HashSet<>();

            for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                List<Config.Rule> rules = Config.getRulesForSlot(hotbarSlot);
                if (rules.isEmpty()) continue;

                int bestSlot = -1;

                for (Config.Rule rule : rules) {
                    if ("Empty".equalsIgnoreCase(rule.ruleKind)) {
                        bestSlot = -2;
                        break;
                    }

                    double highestScore = -1.0;
                    int candidate = -1;

                    for (int i = 0; i < 36; i++) {
                        if (claimedInvSlots.contains(i)) continue;
                        ItemStack stack = inv.getItem(i);
                        if (stack.isEmpty()) continue;

                        double s = score(stack, rule);
                        if (s > highestScore) {
                            highestScore = s;
                            candidate = i;
                        }
                    }

                    if (candidate != -1) {
                        bestSlot = candidate;
                        break;
                    }
                }

                // ONLY trigger if the upgrade is in MAIN INVENTORY (slots 9-35) ready to move!
                if (bestSlot >= 9 && bestSlot <= 35) {
                    claimedInvSlots.add(bestSlot);
                    TARGET_HOTBAR_FOR_INV_SLOT[bestSlot] = hotbarSlot;
                    HOTBAR_NEEDS_UPGRADE[hotbarSlot] = true;
                } else if (bestSlot >= 0 && bestSlot <= 8) {
                    claimedInvSlots.add(bestSlot);
                    // Already in hotbar, no alert!
                }
            }
        }

        public static int getTargetHotbarSlot(int invSlot) {
            if (invSlot >= 0 && invSlot < TARGET_HOTBAR_FOR_INV_SLOT.length) {
                return TARGET_HOTBAR_FOR_INV_SLOT[invSlot];
            }
            return -1;
        }

        public static boolean slotNeedsUpgrade(int hotbarSlot) {
            if (hotbarSlot >= 0 && hotbarSlot < HOTBAR_NEEDS_UPGRADE.length) {
                return HOTBAR_NEEDS_UPGRADE[hotbarSlot];
            }
            return false;
        }

        public static String getKeyName(int hotbarSlot) {
            Minecraft client = Minecraft.getInstance();
            if (client.options != null && hotbarSlot >= 0 && hotbarSlot < client.options.keyHotbarSlots.length) {
                return client.options.keyHotbarSlots[hotbarSlot].getTranslatedKeyMessage().getString();
            }
            return String.valueOf(hotbarSlot + 1);
        }

        public static double score(ItemStack stack, Config.Rule rule) {
            if (stack.isEmpty()) return -1.0;
            Item item = stack.getItem();
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            String enchantData = stack.has(DataComponents.ENCHANTMENTS) ? stack.get(DataComponents.ENCHANTMENTS).toString().toLowerCase() : "";

            if ("Specific".equalsIgnoreCase(rule.ruleKind)) {
                if (!itemId.equalsIgnoreCase(rule.specificId)) return -1.0;
                if (rule.mustBeEnchanted && !stack.isEnchanted()) return -1.0;
                if (!rule.requiredEnchant.isEmpty() && !enchantData.contains(rule.requiredEnchant.toLowerCase())) return -1.0;
                return 100.0 + stack.getCount();
            }

            switch (rule.category) {
                case "Sword":
                    if (stack.is(ItemTags.SWORDS) || itemId.contains("sword")) {
                        double tier = getTier(itemId);
                        if ("Netherite Only".equals(rule.variant) && tier < 6.0) return -1.0;
                        if ("Diamond+".equals(rule.variant) && tier < 5.0) return -1.0;
                        if ("With Fire Aspect".equals(rule.variant) && !enchantData.contains("fire_aspect")) return -1.0;
                        if ("Without Fire Aspect".equals(rule.variant) && enchantData.contains("fire_aspect")) return -1.0;
                        if ("With Looting".equals(rule.variant) && !enchantData.contains("looting")) return -1.0;
                        return tier * 10.0 + stack.getCount();
                    }
                    return -1.0;

                case "Bow":
                    if (itemId.contains("bow") && !itemId.contains("crossbow")) {
                        if ("With Infinity".equals(rule.variant) && !enchantData.contains("infinity")) return -1.0;
                        if ("With Flame".equals(rule.variant) && !enchantData.contains("flame")) return -1.0;
                        if ("With Punch".equals(rule.variant) && !enchantData.contains("punch")) return -1.0;
                        if ("With Mending".equals(rule.variant) && !enchantData.contains("mending")) return -1.0;
                        return 50.0 + (enchantData.contains("power") ? 20.0 : 0.0);
                    }
                    return -1.0;

                case "Crossbow":
                    if (itemId.contains("crossbow")) {
                        if ("With Multishot".equals(rule.variant) && !enchantData.contains("multishot")) return -1.0;
                        if ("With Piercing".equals(rule.variant) && !enchantData.contains("piercing")) return -1.0;
                        if ("With Quick Charge".equals(rule.variant) && !enchantData.contains("quick_charge")) return -1.0;
                        return 50.0;
                    }
                    return -1.0;

                case "Trident":
                    if (itemId.contains("trident")) {
                        if ("With Riptide".equals(rule.variant) && !enchantData.contains("riptide")) return -1.0;
                        if ("With Loyalty".equals(rule.variant) && !enchantData.contains("loyalty")) return -1.0;
                        if ("With Channeling".equals(rule.variant) && !enchantData.contains("channeling")) return -1.0;
                        return 50.0;
                    }
                    return -1.0;

                case "Mace":
                    if (itemId.contains("mace")) {
                        if ("With Density".equals(rule.variant) && !enchantData.contains("density")) return -1.0;
                        if ("With Breach".equals(rule.variant) && !enchantData.contains("breach")) return -1.0;
                        if ("With Wind Burst".equals(rule.variant) && !enchantData.contains("wind_burst")) return -1.0;
                        return 50.0;
                    }
                    return -1.0;

                case "Pickaxe":
                    if (stack.is(ItemTags.PICKAXES) || itemId.contains("pickaxe")) {
                        double s = getTier(itemId) * 10.0;
                        if ("With Silk Touch".equals(rule.variant) && !enchantData.contains("silk_touch")) return -1.0;
                        if ("With Fortune".equals(rule.variant) && !enchantData.contains("fortune")) return -1.0;
                        return s + stack.getCount();
                    }
                    return -1.0;

                case "Axe":
                    if (stack.is(ItemTags.AXES) || (itemId.contains("axe") && !itemId.contains("pickaxe"))) {
                        double s = getTier(itemId) * 10.0;
                        if ("With Silk Touch".equals(rule.variant) && !enchantData.contains("silk_touch")) return -1.0;
                        if ("With Fortune".equals(rule.variant) && !enchantData.contains("fortune")) return -1.0;
                        return s + stack.getCount();
                    }
                    return -1.0;

                case "Shovel":
                    if (stack.is(ItemTags.SHOVELS) || itemId.contains("shovel")) {
                        double s = getTier(itemId) * 10.0;
                        if ("With Silk Touch".equals(rule.variant) && !enchantData.contains("silk_touch")) return -1.0;
                        if ("With Fortune".equals(rule.variant) && !enchantData.contains("fortune")) return -1.0;
                        return s + stack.getCount();
                    }
                    return -1.0;

                case "Hoe":
                    if (stack.is(ItemTags.HOES) || itemId.contains("hoe")) {
                        double s = getTier(itemId) * 10.0;
                        if ("With Silk Touch".equals(rule.variant) && !enchantData.contains("silk_touch")) return -1.0;
                        if ("With Fortune".equals(rule.variant) && !enchantData.contains("fortune")) return -1.0;
                        return s + stack.getCount();
                    }
                    return -1.0;

                case "Block":
                    if (item instanceof BlockItem blockItem && !itemId.contains("torch")) {
                        if ("Hardest".equalsIgnoreCase(rule.variant)) {
                            Block block = blockItem.getBlock();
                            return block.getExplosionResistance() * 10.0 + (stack.getCount() * 0.05);
                        } else if ("Soft Utility".equalsIgnoreCase(rule.variant)) {
                            if (itemId.contains("dirt") || itemId.contains("cobble") || itemId.contains("netherrack")) {
                                return 50.0 + stack.getCount();
                            }
                            return 1.0;
                        } else if ("Building Stone".equalsIgnoreCase(rule.variant)) {
                            if (itemId.contains("stone") || itemId.contains("deepslate") || itemId.contains("brick")) {
                                return 50.0 + stack.getCount();
                            }
                            return 1.0;
                        } else if ("Wood Planks".equalsIgnoreCase(rule.variant)) {
                            if (itemId.contains("planks")) return 50.0 + stack.getCount();
                            return 1.0;
                        } else {
                            // "Most Count"
                            return stack.getCount();
                        }
                    }
                    return -1.0;

                case "Food":
                    if (stack.has(DataComponents.FOOD)) {
                        FoodProperties food = stack.get(DataComponents.FOOD);
                        if (food != null) {
                            if ("Fast Eating".equals(rule.variant) && (itemId.contains("kelp") || itemId.contains("berry"))) {
                                return 50.0 + stack.getCount();
                            }
                            if ("Emergency Health".equals(rule.variant) && itemId.contains("golden_apple")) {
                                return 100.0 + stack.getCount();
                            }
                            if ("Most Count Food".equals(rule.variant)) {
                                return stack.getCount();
                            }
                            return food.nutrition() * 2.0 + food.saturation() + (stack.getCount() * 0.01);
                        }
                        return 10.0 + stack.getCount();
                    }
                    return -1.0;

                case "Utility & Projectiles":
                    if ("Snowball".equals(rule.variant) && itemId.equals("minecraft:snowball")) return 100.0 + stack.getCount();
                    if ("Ender Pearl".equals(rule.variant) && itemId.equals("minecraft:ender_pearl")) return 100.0 + stack.getCount();
                    if ("Water Bucket".equals(rule.variant) && itemId.equals("minecraft:water_bucket")) return 100.0;
                    if ("Torches / Light".equals(rule.variant) && (itemId.contains("torch") || itemId.contains("lantern"))) return 50.0 + stack.getCount();
                    if ("Totem of Undying".equals(rule.variant) && itemId.equals("minecraft:totem_of_undying")) return 100.0;
                    if ("Shield".equals(rule.variant) && itemId.equals("minecraft:shield")) return 50.0;
                    if ("Firework Rocket".equals(rule.variant) && itemId.equals("minecraft:firework_rocket")) return 50.0 + stack.getCount();
                    if ("Splash Potion".equals(rule.variant) && itemId.contains("splash_potion")) return 50.0;
                    if ("Wind Charge".equals(rule.variant) && itemId.contains("wind_charge")) return 100.0 + stack.getCount();
                    return -1.0;

                default:
                    return -1.0;
            }
        }

        private static double getTier(String id) {
            if (id.contains("netherite")) return 6.0;
            if (id.contains("diamond")) return 5.0;
            if (id.contains("iron")) return 4.0;
            if (id.contains("stone")) return 3.0;
            if (id.contains("gold")) return 2.0;
            if (id.contains("wood")) return 1.0;
            return 3.5;
        }
    }

    // ==========================================================
    // 3. CLEAN RENDERER (VULKAN & SODIUM SAFE)
    // ==========================================================
    public static class Renderer {
        public static final int EMERALD_GREEN = 0xFF10B981;
        public static final int EMERALD_TINT = 0x3510B981;
        public static final int BADGE_BG = 0xF0111827;

        public static void renderHudHotbar(GuiGraphicsExtractor graphics) {
            if (!Config.isEnabled()) return;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            int hotbarX = (graphics.guiWidth() - 182) / 2;
            int hotbarY = graphics.guiHeight() - 22;

            for (int s = 0; s < 9; s++) {
                if (Engine.slotNeedsUpgrade(s)) {
                    int slotX = hotbarX + s * 20;
                    int slotY = hotbarY;

                    // Clean top accent line
                    graphics.fill(slotX + 1, slotY + 1, slotX + 21, slotY + 3, EMERALD_GREEN);

                    // Badge showing hotkey
                    String key = Engine.getKeyName(s);
                    int textW = client.font.width(key);
                    int bW = Math.max(textW + 4, 9);
                    int bX = slotX + 20 - bW;
                    int bY = slotY - 4;

                    graphics.fill(bX, bY, bX + bW, bY + 8, BADGE_BG);
                    graphics.fill(bX, bY, bX + bW, bY + 1, EMERALD_GREEN);
                    graphics.text(client.font, key, bX + (bW - textW) / 2, bY + 1, 0xFFFFFFFF, false);
                }
            }
        }

        public static void renderContainerSlot(GuiGraphicsExtractor graphics, Slot slot) {
            if (!Config.isEnabled()) return;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            if (!(slot.container instanceof Inventory)) return;

            int invSlot = slot.getContainerSlot();

            // STRICTLY ignore armor (36..39) and offhand (40)
            if (invSlot < 0 || invSlot > 35) return;

            // 1. Highlight source upgrade item in main inventory (9..35)
            int targetHotbarSlot = Engine.getTargetHotbarSlot(invSlot);
            if (invSlot >= 9 && invSlot <= 35 && targetHotbarSlot >= 0 && targetHotbarSlot < 9) {
                int x = slot.x;
                int y = slot.y;

                graphics.fill(x, y, x + 16, y + 16, EMERALD_TINT);
                graphics.fill(x, y, x + 16, y + 1, EMERALD_GREEN);
                graphics.fill(x, y + 15, x + 16, y + 16, EMERALD_GREEN);
                graphics.fill(x, y + 1, x + 1, y + 15, EMERALD_GREEN);
                graphics.fill(x + 15, y + 1, x + 16, y + 15, EMERALD_GREEN);

                String keyName = Engine.getKeyName(targetHotbarSlot);
                Font font = client.font;
                String badgeText = "->" + keyName;
                int textW = font.width(badgeText);
                int badgeW = Math.max(textW + 4, 14);
                int badgeH = 8;
                int badgeX = x + 8 - (badgeW / 2);
                int badgeY = y - 4;

                graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, BADGE_BG);
                graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, EMERALD_GREEN);
                graphics.text(font, badgeText, badgeX + (badgeW - textW) / 2, badgeY + 1, 0xFFFFFFFF, false);
            }

            // 2. Also outline the target hotbar slot (0..8) in the inventory screen
            if (invSlot >= 0 && invSlot <= 8 && Engine.slotNeedsUpgrade(invSlot)) {
                int x = slot.x;
                int y = slot.y;
                graphics.fill(x, y, x + 16, y + 1, EMERALD_GREEN);
                graphics.fill(x, y + 15, x + 16, y + 16, EMERALD_GREEN);
                graphics.fill(x, y + 1, x + 1, y + 15, EMERALD_GREEN);
                graphics.fill(x + 15, y + 1, x + 16, y + 15, EMERALD_GREEN);
            }
        }
    }

    // ==========================================================
    // 4. MAIN MODERN GLASS CONFIG SCREEN
    // ==========================================================
    public static class ModernConfigScreen extends Screen {
        private final Screen parent;
        private int selectedSlot = 0;

        public ModernConfigScreen(Screen parent) {
            super(Component.literal("AutoHotbar Fairplay"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            int slotStart = cardX + (cardW / 2) - 95;
            for (int i = 0; i < 9; i++) {
                final int idx = i;
                this.addRenderableWidget(Button.builder(
                    Component.literal(String.valueOf(i + 1)),
                    btn -> { this.selectedSlot = idx; this.rebuildWidgets(); }
                ).bounds(slotStart + i * 21, cardY + 48, 19, 18).build());
            }

            List<Config.Rule> rules = Config.getRulesForSlot(selectedSlot);
            int listY = cardY + 92;
            for (int r = 0; r < Math.min(rules.size(), 3); r++) {
                final int rIdx = r;
                this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    btn -> { Config.removeRule(selectedSlot, rIdx); this.rebuildWidgets(); }
                ).bounds(cardX + cardW - 45, listY + r * 22, 20, 18).build());
            }

            this.addRenderableWidget(Button.builder(
                Component.literal("+ Add rule"),
                btn -> this.minecraft.setScreen(new AddRuleScreen(this, selectedSlot))
            ).bounds(cardX + 16, cardY + cardH - 32, 160, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> this.onClose()
            ).bounds(cardX + cardW - 130, cardY + cardH - 32, 55, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("Save"),
                btn -> { Config.save(); this.onClose(); }
            ).bounds(cardX + cardW - 70, cardY + cardH - 32, 55, 20).build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            // Translucent Glass Canvas
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xB80D111A);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0x605B708B);
            graphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0x605B708B);
            graphics.fill(cardX, cardY, cardX + 1, cardY + cardH, 0x605B708B);
            graphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0x605B708B);

            // Title & Subtitle
            graphics.text(this.font, "AutoHotbar Fairplay", cardX + 16, cardY + 14, 0xFFFFFFFF, false);
            graphics.text(this.font, "Keybind opens inventory with hotbar-key labels — no auto swaps.", cardX + 16, cardY + 26, 0xFF94A3B8, false);

            // Emerald Active Ring around selected slot
            int slotStart = cardX + (cardW / 2) - 95;
            int selX = slotStart + selectedSlot * 21;
            graphics.fill(selX - 1, cardY + 47, selX + 20, cardY + 48, 0xFF10B981);
            graphics.fill(selX - 1, cardY + 66, selX + 20, cardY + 67, 0xFF10B981);
            graphics.fill(selX - 1, cardY + 47, selX, cardY + 67, 0xFF10B981);
            graphics.fill(selX + 19, cardY + 47, selX + 20, cardY + 67, 0xFF10B981);

            List<Config.Rule> rules = Config.getRulesForSlot(selectedSlot);
            graphics.text(this.font, "Slot " + (selectedSlot + 1) + "   " + rules.size() + " rules — lower # wins", cardX + 16, cardY + 74, 0xFF94A3B8, false);

            int boxY = cardY + 86;
            int boxH = cardH - 128;
            graphics.fill(cardX + 16, boxY, cardX + cardW - 16, boxY + boxH, 0x90080B12);
            graphics.fill(cardX + 16, boxY, cardX + cardW - 16, boxY + 1, 0x50334155);

            if (rules.isEmpty()) {
                graphics.text(this.font, "No rules — click \"+ Add rule\" to start", cardX + (cardW / 2) - 80, boxY + 28, 0xFF64748B, false);
            } else {
                for (int r = 0; r < Math.min(rules.size(), 3); r++) {
                    Config.Rule rule = rules.get(r);
                    int rY = boxY + 6 + (r * 22);
                    graphics.fill(cardX + 22, rY, cardX + cardW - 50, rY + 18, 0xAA1E293B);
                    graphics.text(this.font, (r + 1) + ". " + rule.getDisplayText(), cardX + 28, rY + 5, 0xFFF1F5F9, false);
                }
            }

            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        @Override
        public void onClose() {
            Config.save();
            if (this.minecraft != null) this.minecraft.setScreen(this.parent);
        }
    }

    // ==========================================================
    // 5. THE ULTIMATE "+ ADD RULE" SCREEN
    // ==========================================================
    public static class AddRuleScreen extends Screen {
        private final ModernConfigScreen parent;
        private final int slotIndex;

        private String currentTab = "Type";
        private static final String[] CATEGORIES = {
            "Sword", "Bow", "Crossbow", "Trident", "Mace", "Pickaxe", "Axe", "Shovel", "Hoe", "Block", "Food", "Utility & Projectiles"
        };
        private int categoryIndex = 0;
        private int variantIndex = 0;

        // Specific Item & Enchantment selections
        public String chosenItemId = "minecraft:water_bucket";
        public String chosenEnchant = "";
        public int chosenEnchantLevel = 1;
        public boolean mustBeEnchanted = false;

        // Conditional controls
        private int ifSlot = 1;
        private int thenUseSlot = 1;

        public AddRuleScreen(ModernConfigScreen parent, int slotIndex) {
            super(Component.literal("Add rule"));
            this.parent = parent;
            this.slotIndex = slotIndex;
        }

        @Override
        protected void init() {
            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            String[] tabs = { "Specific", "Type", "Conditional", "Empty" };
            int tabW = (cardW - 32) / 4;
            for (int t = 0; t < tabs.length; t++) {
                final String tabName = tabs[t];
                this.addRenderableWidget(Button.builder(
                    Component.literal(tabName),
                    btn -> { this.currentTab = tabName; this.rebuildWidgets(); }
                ).bounds(cardX + 16 + t * tabW, cardY + 28, tabW - 4, 18).build());
            }

            // CONTROLS FOR "Type"
            if ("Type".equals(currentTab)) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("Category: " + CATEGORIES[categoryIndex]),
                    btn -> {
                        categoryIndex = (categoryIndex + 1) % CATEGORIES.length;
                        variantIndex = 0;
                        this.rebuildWidgets();
                    }
                ).bounds(cardX + 24, cardY + 68, 175, 20).build());

                String[] variants = getVariants(CATEGORIES[categoryIndex]);
                this.addRenderableWidget(Button.builder(
                    Component.literal("Variant: " + variants[variantIndex % variants.length]),
                    btn -> {
                        variantIndex = (variantIndex + 1) % variants.length;
                        btn.setMessage(Component.literal("Variant: " + variants[variantIndex % variants.length]));
                    }
                ).bounds(cardX + cardW - 200, cardY + 68, 175, 20).build());
            }

            // CONTROLS FOR "Specific"
            if ("Specific".equals(currentTab)) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("Pick Item: " + chosenItemId.replace("minecraft:", "")),
                    btn -> this.minecraft.setScreen(new ItemPickerScreen(this))
                ).bounds(cardX + 24, cardY + 60, 200, 20).build());

                this.addRenderableWidget(Button.builder(
                    Component.literal("Enchants: " + (chosenEnchant.isEmpty() ? "Any" : chosenEnchant + " " + chosenEnchantLevel)),
                    btn -> this.minecraft.setScreen(new EnchantPickerScreen(this))
                ).bounds(cardX + cardW - 170, cardY + 60, 145, 20).build());

                this.addRenderableWidget(Button.builder(
                    Component.literal("Must be Enchanted: " + (mustBeEnchanted ? "YES" : "NO")),
                    btn -> {
                        mustBeEnchanted = !mustBeEnchanted;
                        btn.setMessage(Component.literal("Must be Enchanted: " + (mustBeEnchanted ? "YES" : "NO")));
                    }
                ).bounds(cardX + 24, cardY + 86, 200, 18).build());
            }

            // CONTROLS FOR "Conditional"
            if ("Conditional".equals(currentTab)) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("IF Slot " + ifSlot + " has item"),
                    btn -> {
                        ifSlot = (ifSlot % 9) + 1;
                        btn.setMessage(Component.literal("IF Slot " + ifSlot + " has item"));
                    }
                ).bounds(cardX + 24, cardY + 68, 175, 20).build());

                this.addRenderableWidget(Button.builder(
                    Component.literal("THEN mirror Slot " + thenUseSlot),
                    btn -> {
                        thenUseSlot = (thenUseSlot % 9) + 1;
                        btn.setMessage(Component.literal("THEN mirror Slot " + thenUseSlot));
                    }
                ).bounds(cardX + cardW - 200, cardY + 68, 175, 20).build());
            }

            // Bottom Buttons
            this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> this.minecraft.setScreen(this.parent)
            ).bounds(cardX + cardW - 130, cardY + cardH - 32, 55, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("Save"),
                btn -> {
                    if ("Type".equals(currentTab)) {
                        String cat = CATEGORIES[categoryIndex];
                        String var = getVariants(cat)[variantIndex % getVariants(cat).length];
                        Config.addRule(slotIndex, new Config.Rule("Type", cat, var, ""));
                    } else if ("Specific".equals(currentTab)) {
                        Config.Rule r = new Config.Rule("Specific", "", "", chosenItemId);
                        r.mustBeEnchanted = this.mustBeEnchanted;
                        r.requiredEnchant = this.chosenEnchant;
                        r.enchantLevel = this.chosenEnchantLevel;
                        Config.addRule(slotIndex, r);
                    } else if ("Empty".equals(currentTab)) {
                        Config.addRule(slotIndex, new Config.Rule("Empty", "", "", ""));
                    }
                    this.minecraft.setScreen(this.parent);
                }
            ).bounds(cardX + cardW - 70, cardY + cardH - 32, 55, 20).build());
        }

        private String[] getVariants(String category) {
            return switch (category) {
                case "Sword" -> new String[]{ "Best DPS", "Fastest Attack", "Most Durable", "With Fire Aspect", "Without Fire Aspect", "With Looting" };
                case "Bow" -> new String[]{ "Best Power", "With Infinity", "With Flame", "With Punch", "Most Durable" };
                case "Crossbow" -> new String[]{ "With Multishot", "With Piercing", "With Quick Charge", "Best Overall", "Most Durable" };
                case "Trident" -> new String[]{ "With Riptide", "With Loyalty", "With Channeling", "Best Melee", "Most Durable" };
                case "Mace" -> new String[]{ "With Density", "With Breach", "With Wind Burst", "Best Overall", "Most Durable" };
                case "Pickaxe" -> new String[]{ "Best Tier", "With Silk Touch", "With Fortune", "Most Durable", "Fastest Mining" };
                case "Axe" -> new String[]{ "Best Weapon (Attack)", "Best Tool (Woodcutting)", "With Silk Touch", "With Fortune", "Most Durable" };
                case "Shovel" -> new String[]{ "Best Tier", "With Silk Touch", "With Fortune", "Most Durable", "Fastest Digging" };
                case "Hoe" -> new String[]{ "Best Tier", "With Silk Touch", "With Fortune", "Most Durable", "Farming Ready" };
                case "Block" -> new String[]{ "Hardest", "Most Count", "Soft Utility", "Building Stone", "Wood Planks" };
                case "Food" -> new String[]{ "Best Saturation", "Highest Nutrition", "Fast Eating", "Emergency Health", "Most Count Food" };
                case "Utility & Projectiles" -> new String[]{ "Snowball", "Ender Pearl", "Water Bucket", "Torches / Light", "Totem of Undying", "Shield", "Firework Rocket", "Splash Potion", "Wind Charge" };
                default -> new String[]{ "Best" };
            };
        }

        private String getDescription() {
            if ("Empty".equals(currentTab)) {
                return "Stop rule\nThis slot will be left empty and no later rules will be tried.\nPlace this rule below specific/type rules to short-circuit.";
            }
            if ("Specific".equals(currentTab)) {
                return "Pick any item in Minecraft and assign enchantment filters.\nItem will be prioritized for this slot regardless of category.";
            }
            if ("Conditional".equals(currentTab)) {
                return "Branch on whether another rule found an item.\nUseful for dynamic hotbars (e.g. Shield if holding 1-handed weapon).";
            }

            String cat = CATEGORIES[categoryIndex];
            String var = getVariants(cat)[variantIndex % getVariants(cat).length];

            if ("Block".equals(cat)) {
                if ("Hardest".equals(var)) return "Block with highest blast resistance and hardness (Obsidian, Deepslate, Stone).";
                if ("Most Count".equals(var)) return "Block with the largest quantity stack in your bag.";
                return "Blocks selected by building type or soft utility (dirt/netherrack/cobble).";
            }
            if ("Sword".equals(cat)) {
                if ("With Fire Aspect".equals(var)) return "Prioritizes swords enchanted with Fire Aspect.";
                if ("Without Fire Aspect".equals(var)) return "Selects your best sword, strictly excluding Fire Aspect (safe for Endermen/Piglins).";
                return "Swords filtered by DPS, tier, attack speed, or looting.";
            }
            if ("Food".equals(cat)) {
                if ("Best Saturation".equals(var)) return "Foods that keep hunger full longest (Golden Carrot, Steak, Porkchop).";
                if ("Fast Eating".equals(var)) return "Fast-consumption items like Dried Kelp and Sweet Berries.";
                return "Highest nutritional restoration or bulk food stacks.";
            }
            if ("Utility & Projectiles".equals(cat)) {
                if ("Snowball".equals(var)) return "Snowballs ready for rapid knockback and blaze combat.";
                if ("Water Bucket".equals(var)) return "Water bucket ready for clutch / MLG landings and fire extinguishing.";
                return "Essential utility items and projectiles.";
            }
            return "Optimal items in the " + cat + " category filtered by " + var + ".";
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xFA0D111A);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0x605B708B);
            graphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0x605B708B);
            graphics.fill(cardX, cardY, cardX + 1, cardY + cardH, 0x605B708B);
            graphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0x605B708B);

            graphics.text(this.font, "Add rule", cardX + 16, cardY + 12, 0xFFFFFFFF, false);
            int rulesCount = Config.getRulesForSlot(slotIndex).size();
            graphics.fill(cardX + cardW - 85, cardY + 8, cardX + cardW - 16, cardY + 22, 0xFF1E293B);
            graphics.text(this.font, "Priority: " + (rulesCount + 1), cardX + cardW - 77, cardY + 11, 0xFFCBD5E1, false);

            String[] tabs = { "Specific", "Type", "Conditional", "Empty" };
            int tabW = (cardW - 32) / 4;
            for (int t = 0; t < tabs.length; t++) {
                if (tabs[t].equals(currentTab)) {
                    int tX = cardX + 16 + t * tabW;
                    graphics.fill(tX, cardY + 44, tX + tabW - 4, cardY + 46, 0xFF10B981);
                }
            }

            String[] descLines = getDescription().split("\n");
            int textY = cardY + 110;
            for (String line : descLines) {
                graphics.text(this.font, line, cardX + 24, textY, 0xFF94A3B8, false);
                textY += 12;
            }

            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    // ==========================================================
    // 6. SEARCHABLE ITEM PICKER SCREEN (ALL 1,500+ ITEMS)
    // ==========================================================
    public static class ItemPickerScreen extends Screen {
        private final AddRuleScreen parent;
        private EditBox searchBox;
        private final List<Item> allItems = new ArrayList<>();
        private List<Item> filteredItems = new ArrayList<>();
        private int page = 0;
        private static final int ITEMS_PER_PAGE = 40; // 8x5 grid

        public ItemPickerScreen(AddRuleScreen parent) {
            super(Component.literal("Pick an item"));
            this.parent = parent;
            for (Item item : BuiltInRegistries.ITEM) {
                allItems.add(item);
            }
            this.filteredItems = new ArrayList<>(allItems);
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            searchBox = new EditBox(this.font, centerX - 100, 25, 200, 18, Component.literal("Search"));
            searchBox.setResponder(this::updateSearch);
            this.addRenderableWidget(searchBox);

            // Pagination Buttons
            this.addRenderableWidget(Button.builder(
                Component.literal("< Prev"),
                btn -> { if (page > 0) page--; }
            ).bounds(centerX - 100, this.height - 28, 60, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("Next >"),
                btn -> { if ((page + 1) * ITEMS_PER_PAGE < filteredItems.size()) page++; }
            ).bounds(centerX + 40, this.height - 28, 60, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(this.parent)
            ).bounds(centerX - 30, this.height - 28, 60, 20).build());
        }

        private void updateSearch(String text) {
            page = 0;
            String query = text.toLowerCase().trim();
            if (query.isEmpty()) {
                filteredItems = new ArrayList<>(allItems);
            } else {
                filteredItems = allItems.stream()
                    .filter(i -> BuiltInRegistries.ITEM.getKey(i).getPath().toLowerCase().contains(query))
                    .toList();
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int centerX = this.width / 2;
            int gridStartX = centerX - 80;
            int gridStartY = 50;

            int startIdx = page * ITEMS_PER_PAGE;
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 8; col++) {
                    int idx = startIdx + (row * 8 + col);
                    if (idx >= filteredItems.size()) break;

                    int x = gridStartX + col * 20;
                    int y = gridStartY + row * 20;

                    if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                        parent.chosenItemId = BuiltInRegistries.ITEM.getKey(filteredItems.get(idx)).toString();
                        this.minecraft.setScreen(this.parent);
                        return true;
                    }
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, this.width, this.height, 0xD80D111A);

            int centerX = this.width / 2;
            graphics.drawCenteredString(this.font, "Pick an Item (" + filteredItems.size() + " available)", centerX, 10, 0xFFFFFFFF);

            int gridStartX = centerX - 80;
            int gridStartY = 50;

            int startIdx = page * ITEMS_PER_PAGE;
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 8; col++) {
                    int idx = startIdx + (row * 8 + col);
                    if (idx >= filteredItems.size()) break;

                    int x = gridStartX + col * 20;
                    int y = gridStartY + row * 20;

                    boolean hovered = (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18);
                    graphics.fill(x, y, x + 18, y + 18, hovered ? 0x9010B981 : 0x601E293B);

                    Item item = filteredItems.get(idx);
                    String name = BuiltInRegistries.ITEM.getKey(item).getPath();
                    graphics.text(this.font, name.substring(0, Math.min(2, name.length())), x + 2, y + 5, 0xFFF1F5F9, false);
                }
            }

            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    // ==========================================================
    // 7. SEARCHABLE ENCHANTMENT PICKER SCREEN (ALL 43 ENCHANTS)
    // ==========================================================
    public static class EnchantPickerScreen extends Screen {
        private final AddRuleScreen parent;
        private EditBox searchBox;
        private static final String[] ALL_ENCHANTS = {
            "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection",
            "respiration", "aqua_affinity", "thorns", "depth_strider", "frost_walker", "soul_speed", "swift_sneak",
            "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping_edge",
            "efficiency", "silk_touch", "unbreaking", "fortune",
            "power", "punch", "flame", "infinity",
            "luck_of_the_sea", "lure",
            "loyalty", "impaling", "riptide", "channeling",
            "multishot", "quick_charge", "piercing",
            "mending", "vanishing_curse", "binding_curse",
            "density", "breach", "wind_burst"
        };
        private List<String> filtered = new ArrayList<>();
        private int selectedLevel = 1;

        public EnchantPickerScreen(AddRuleScreen parent) {
            super(Component.literal("Pick an enchantment"));
            this.parent = parent;
            this.filtered = new ArrayList<>(Arrays.asList(ALL_ENCHANTS));
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            searchBox = new EditBox(this.font, centerX - 110, 25, 220, 18, Component.literal("Search"));
            searchBox.setResponder(this::updateSearch);
            this.addRenderableWidget(searchBox);

            // Level adjuster
            this.addRenderableWidget(Button.builder(
                Component.literal("-"),
                btn -> { if (selectedLevel > 1) selectedLevel--; }
            ).bounds(centerX - 110, this.height - 28, 25, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("+"),
                btn -> { if (selectedLevel < 5) selectedLevel++; }
            ).bounds(centerX - 40, this.height - 28, 25, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(this.parent)
            ).bounds(centerX + 30, this.height - 28, 80, 20).build());
        }

        private void updateSearch(String query) {
            String q = query.toLowerCase().trim();
            if (q.isEmpty()) {
                filtered = new ArrayList<>(Arrays.asList(ALL_ENCHANTS));
            } else {
                filtered = Arrays.stream(ALL_ENCHANTS).filter(e -> e.contains(q)).toList();
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int centerX = this.width / 2;
            int startY = 50;

            for (int i = 0; i < Math.min(filtered.size(), 7); i++) {
                int y = startY + i * 20;
                if (mouseX >= centerX - 110 && mouseX < centerX + 110 && mouseY >= y && mouseY < y + 18) {
                    parent.chosenEnchant = filtered.get(i);
                    parent.chosenEnchantLevel = selectedLevel;
                    this.minecraft.setScreen(this.parent);
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, this.width, this.height, 0xD80D111A);

            int centerX = this.width / 2;
            graphics.drawCenteredString(this.font, "Pick an Enchantment (" + filtered.size() + " available)", centerX, 10, 0xFFFFFFFF);

            int startY = 50;
            for (int i = 0; i < Math.min(filtered.size(), 7); i++) {
                int y = startY + i * 20;
                boolean hovered = (mouseX >= centerX - 110 && mouseX < centerX + 110 && mouseY >= y && mouseY < y + 18);
                graphics.fill(centerX - 110, y, centerX + 110, y + 18, hovered ? 0x9010B981 : 0x601E293B);

                String name = filtered.get(i).replace("_", " ");
                graphics.text(this.font, name, centerX - 100, y + 5, 0xFFF1F5F9, false);
            }

            graphics.text(this.font, "Level: " + selectedLevel, centerX - 80, this.height - 22, 0xFFFFFFFF, false);

            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }
}