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

public class ExampleModClient implements ClientModInitializer {
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

        // Native 26.1 HUD registration
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("autohotbar", "hotbar_hud"),
            (graphics, deltaTracker) -> Renderer.renderHudHotbar(graphics)
        );

        // Farm & Raid throttled tick loop
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
    // 1. ADVANCED CONFIGURATION SYSTEM
    // ==========================================================
    public static class Config {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("autohotbar_fairplay.json").toFile();

        public static class Rule {
            public String ruleKind = "Type"; // "Type", "Specific", "Empty"
            public String category = "Sword"; // Sword, Pickaxe, Axe, Shovel, Block, Food, Torches, Water Bucket
            public String variant = "Best"; // Best, Hardest, Most Count
            public String specificId = "";

            public Rule(String ruleKind, String category, String variant, String specificId) {
                this.ruleKind = ruleKind;
                this.category = category;
                this.variant = variant;
                this.specificId = specificId;
            }

            public String getDisplayText() {
                if ("Empty".equalsIgnoreCase(ruleKind)) return "Leave Slot Empty (Stop Rule)";
                if ("Specific".equalsIgnoreCase(ruleKind)) return "Item: " + (specificId.isEmpty() ? "None" : specificId);
                return category + " (" + variant + ")";
            }
        }

        public static class Data {
            public boolean enabled = true;
            public boolean glassMode = true;
            public List<List<Rule>> slots = new ArrayList<>();

            public Data() {
                for (int i = 0; i < 9; i++) {
                    slots.add(new ArrayList<>());
                }
                // Defaults
                slots.get(0).add(new Rule("Type", "Sword", "Best", ""));
                slots.get(1).add(new Rule("Type", "Pickaxe", "Best", ""));
                slots.get(2).add(new Rule("Type", "Axe", "Best", ""));
                slots.get(3).add(new Rule("Type", "Shovel", "Best", ""));
                slots.get(4).add(new Rule("Type", "Torches", "Best", ""));
                slots.get(5).add(new Rule("Type", "Block", "Hardest", ""));
                slots.get(6).add(new Rule("Type", "Block", "Most Count", ""));
                slots.get(7).add(new Rule("Type", "Food", "Best", ""));
                slots.get(8).add(new Rule("Type", "Water Bucket", "Best", ""));
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
    // 2. LAG-FREE FARM-OPTIMIZED ENGINE
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

                // Priority: lower index wins (Rule 1 has highest priority)
                for (Config.Rule rule : rules) {
                    if ("Empty".equalsIgnoreCase(rule.ruleKind)) {
                        bestSlot = -2; // Stop rule: leave empty!
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
                        break; // Top priority matched!
                    }
                }

                if (bestSlot >= 0) {
                    claimedInvSlots.add(bestSlot);
                    if (bestSlot != hotbarSlot) {
                        if (bestSlot >= 9 && bestSlot <= 35) {
                            TARGET_HOTBAR_FOR_INV_SLOT[bestSlot] = hotbarSlot;
                        }
                        HOTBAR_NEEDS_UPGRADE[hotbarSlot] = true;
                    }
                } else if (bestSlot != -2) {
                    ItemStack current = inv.getItem(hotbarSlot);
                    if (current.isEmpty()) {
                        HOTBAR_NEEDS_UPGRADE[hotbarSlot] = true;
                    }
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

            if ("Specific".equalsIgnoreCase(rule.ruleKind)) {
                if (itemId.equalsIgnoreCase(rule.specificId)) return 100.0 + stack.getCount();
                return -1.0;
            }

            switch (rule.category) {
                case "Sword":
                    if (stack.is(ItemTags.SWORDS) || itemId.contains("sword") || itemId.contains("mace")) {
                        return getTier(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "Pickaxe":
                    if (stack.is(ItemTags.PICKAXES) || itemId.contains("pickaxe")) {
                        return getTier(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "Axe":
                    if (stack.is(ItemTags.AXES) || (itemId.contains("axe") && !itemId.contains("pickaxe"))) {
                        return getTier(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "Shovel":
                    if (stack.is(ItemTags.SHOVELS) || itemId.contains("shovel")) {
                        return getTier(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "Block":
                    if (item instanceof BlockItem blockItem && !itemId.contains("torch")) {
                        if ("Hardest".equalsIgnoreCase(rule.variant)) {
                            Block block = blockItem.getBlock();
                            float destroySpeed = block.defaultBlockState().getDestroySpeed(null, null);
                            return (destroySpeed > 0 ? destroySpeed * 10.0 : 5.0) + (stack.getCount() * 0.05);
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
                            return food.nutrition() * 2.0 + food.saturation() + (stack.getCount() * 0.01);
                        }
                        return 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "Torches":
                    if (itemId.contains("torch") || itemId.contains("lantern")) return 50.0 + stack.getCount();
                    return -1.0;
                case "Water Bucket":
                    if (itemId.equals("minecraft:water_bucket") || itemId.equals("minecraft:ender_pearl") || itemId.equals("minecraft:totem_of_undying")) {
                        return 50.0 + stack.getCount();
                    }
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
    // 3. ELEGANT RENDERER (VULKAN & SODIUM COMPATIBLE)
    // ==========================================================
    public static class Renderer {
        public static final int EMERALD_GREEN = 0xFF2ECC71;
        public static final int EMERALD_TINT = 0x352ECC71;
        public static final int BADGE_BG = 0xEE1A1A1A;

        public static void renderHudHotbar(GuiGraphicsExtractor graphics) {
            if (!Config.isEnabled()) return;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            int midX = graphics.guiWidth() / 2;
            int hotbarX = midX - 90;
            int hotbarY = graphics.guiHeight() - 22;

            for (int s = 0; s < 9; s++) {
                if (Engine.slotNeedsUpgrade(s)) {
                    int x = hotbarX + s * 20 + 2;
                    int y = hotbarY + 3;

                    // Clean emerald top notch & corner accent
                    graphics.fill(x + 1, y - 2, x + 15, y, EMERALD_GREEN);
                    graphics.fill(x + 11, y - 1, x + 15, y + 3, EMERALD_GREEN);
                }
            }
        }

        public static void renderContainerSlot(GuiGraphicsExtractor graphics, Slot slot) {
            if (!Config.isEnabled()) return;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            if (!(slot.container instanceof Inventory)) return;

            int invSlot = slot.getContainerSlot();
            if (invSlot < 9 || invSlot > 35) return;

            int targetHotbarSlot = Engine.getTargetHotbarSlot(invSlot);
            if (targetHotbarSlot >= 0 && targetHotbarSlot < 9) {
                int x = slot.x;
                int y = slot.y;

                // 1. Smooth Emerald Tint & 1px crisp outline (Matching Image 4)
                graphics.fill(x, y, x + 16, y + 16, EMERALD_TINT);
                graphics.fill(x, y, x + 16, y + 1, EMERALD_GREEN);
                graphics.fill(x, y + 15, x + 16, y + 16, EMERALD_GREEN);
                graphics.fill(x, y + 1, x + 1, y + 15, EMERALD_GREEN);
                graphics.fill(x + 15, y + 1, x + 16, y + 15, EMERALD_GREEN);

                // 2. High-contrast Black & White Key Badge centered over top border
                String keyName = Engine.getKeyName(targetHotbarSlot);
                Font font = client.font;
                int textW = font.width(keyName);
                int badgeW = Math.max(textW + 4, 9);
                int badgeH = 8;
                int badgeX = x + 8 - (badgeW / 2);
                int badgeY = y - 4;

                graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, BADGE_BG);
                graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, EMERALD_GREEN);
                graphics.text(font, keyName, badgeX + (badgeW - textW) / 2, badgeY + 1, 0xFFFFFFFF, false);
            }
        }
    }

    // ==========================================================
    // 4. THE FAITHFUL COMPLEX UI (SCREENSHOTS 1, 3, 7, 8)
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

            // Glass Mode Toggle Switch (Top Right)
            this.addRenderableWidget(Button.builder(
                Component.literal("Glass " + (Config.data.glassMode ? "ON" : "OFF")),
                btn -> {
                    Config.data.glassMode = !Config.data.glassMode;
                    Config.save();
                    btn.setMessage(Component.literal("Glass " + (Config.data.glassMode ? "ON" : "OFF")));
                }
            ).bounds(cardX + cardW - 75, cardY + 12, 65, 16).build());

            // 9 Slot Buttons
            int slotStart = cardX + (cardW / 2) - 95;
            for (int i = 0; i < 9; i++) {
                final int idx = i;
                this.addRenderableWidget(Button.builder(
                    Component.literal(String.valueOf(i + 1)),
                    btn -> { this.selectedSlot = idx; this.rebuildWidgets(); }
                ).bounds(slotStart + i * 21, cardY + 54, 19, 18).build());
            }

            // Rule Cards inside the slot (with red X to delete)
            List<Config.Rule> rules = Config.getRulesForSlot(selectedSlot);
            int listY = cardY + 98;
            for (int r = 0; r < Math.min(rules.size(), 3); r++) {
                final int rIdx = r;
                this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    btn -> { Config.removeRule(selectedSlot, rIdx); this.rebuildWidgets(); }
                ).bounds(cardX + cardW - 45, listY + r * 22, 20, 18).build());
            }

            // Bottom Buttons
            this.addRenderableWidget(Button.builder(
                Component.literal("+ Add rule"),
                btn -> this.minecraft.setScreen(new AddRuleScreen(this, selectedSlot))
            ).bounds(cardX + 16, cardY + cardH - 32, 180, 20).build());

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
            super.extractRenderState(graphics, mouseX, mouseY, delta);

            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            // 1. Dark Glass Background
            int bg = Config.data.glassMode ? 0xD0131822 : 0xF5131822;
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, bg);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFF354256);
            graphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFF354256);
            graphics.fill(cardX, cardY, cardX + 1, cardY + cardH, 0xFF354256);
            graphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0xFF354256);

            // 2. Titles
            graphics.text(this.font, "AutoHotbar Fairplay", cardX + 16, cardY + 14, 0xFFFFFF, false);
            graphics.text(this.font, "Keybind opens inventory with hotbar-key labels — no auto swaps.", cardX + 16, cardY + 26, 0x88A0B0, false);

            // 3. Tabs: [Complex] [Simple]
            graphics.fill(cardX + 16, cardY + 38, cardX + 75, cardY + 52, 0xFF242C3B);
            graphics.fill(cardX + 16, cardY + 51, cardX + 75, cardY + 53, 0xFF2ECC71); // Active Green underline
            graphics.text(this.font, "Complex", cardX + 26, cardY + 41, 0xFFFFFF, false);

            graphics.fill(cardX + 80, cardY + 38, cardX + 135, cardY + 52, 0x66242C3B);
            graphics.text(this.font, "Simple", cardX + 92, cardY + 41, 0x778899, false);

            // 4. Emerald ring around selected slot
            int slotStart = cardX + (cardW / 2) - 95;
            int selX = slotStart + selectedSlot * 21;
            graphics.fill(selX - 1, cardY + 53, selX + 20, cardY + 54, 0xFF2ECC71);
            graphics.fill(selX - 1, cardY + 72, selX + 20, cardY + 73, 0xFF2ECC71);
            graphics.fill(selX - 1, cardY + 53, selX, cardY + 73, 0xFF2ECC71);
            graphics.fill(selX + 19, cardY + 53, selX + 20, cardY + 73, 0xFF2ECC71);

            // 5. Slot Info
            List<Config.Rule> rules = Config.getRulesForSlot(selectedSlot);
            graphics.text(this.font, "Slot " + (selectedSlot + 1) + "   " + rules.size() + " rules — lower # wins", cardX + 16, cardY + 80, 0xA0B0C0, false);

            // 6. Rules Container Card
            int boxY = cardY + 92;
            int boxH = cardH - 134;
            graphics.fill(cardX + 16, boxY, cardX + cardW - 16, boxY + boxH, 0xAA0D1118);
            graphics.fill(cardX + 16, boxY, cardX + cardW - 16, boxY + 1, 0xFF252E3E);

            if (rules.isEmpty()) {
                graphics.text(this.font, "No rules — click \"+ Add rule\" to start", cardX + (cardW / 2) - 80, boxY + 28, 0x778899, false);
            } else {
                for (int r = 0; r < Math.min(rules.size(), 3); r++) {
                    Config.Rule rule = rules.get(r);
                    int rY = boxY + 6 + (r * 22);
                    graphics.fill(cardX + 22, rY, cardX + cardW - 50, rY + 18, 0xFF1D2532);
                    graphics.text(this.font, (r + 1) + ". " + rule.getDisplayText(), cardX + 28, rY + 5, 0xEEEEEE, false);
                }
            }
        }

        @Override
        public void onClose() {
            Config.save();
            if (this.minecraft != null) this.minecraft.setScreen(this.parent);
        }
    }

    // ==========================================================
    // 5. THE "+ ADD RULE" SCREENSHOT CLONE (IMAGES 7 & 8)
    // ==========================================================
    public static class AddRuleScreen extends Screen {
        private final ModernConfigScreen parent;
        private final int slotIndex;

        private String currentTab = "Type"; // Specific, Type, Conditional, Empty
        private static final String[] CATEGORIES = { "Sword", "Pickaxe", "Axe", "Shovel", "Block", "Food", "Torches", "Water Bucket" };
        private int categoryIndex = 0;
        private int variantIndex = 0;

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

            // 4 Tabs: [Specific] [Type] [Conditional] [Empty]
            String[] tabs = { "Specific", "Type", "Conditional", "Empty" };
            int tabW = (cardW - 32) / 4;
            for (int t = 0; t < tabs.length; t++) {
                final String tabName = tabs[t];
                this.addRenderableWidget(Button.builder(
                    Component.literal(tabName),
                    btn -> { this.currentTab = tabName; this.rebuildWidgets(); }
                ).bounds(cardX + 16 + t * tabW, cardY + 28, tabW - 4, 18).build());
            }

            if ("Type".equals(currentTab)) {
                // Category button
                this.addRenderableWidget(Button.builder(
                    Component.literal("Category: " + CATEGORIES[categoryIndex]),
                    btn -> {
                        categoryIndex = (categoryIndex + 1) % CATEGORIES.length;
                        variantIndex = 0;
                        this.rebuildWidgets();
                    }
                ).bounds(cardX + 24, cardY + 68, 175, 20).build());

                // Variant button
                String[] variants = getVariants(CATEGORIES[categoryIndex]);
                this.addRenderableWidget(Button.builder(
                    Component.literal("Variant: " + variants[variantIndex % variants.length]),
                    btn -> {
                        variantIndex = (variantIndex + 1) % variants.length;
                        btn.setMessage(Component.literal("Variant: " + variants[variantIndex % variants.length]));
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
                    } else if ("Empty".equals(currentTab)) {
                        Config.addRule(slotIndex, new Config.Rule("Empty", "", "", ""));
                    }
                    this.minecraft.setScreen(this.parent);
                }
            ).bounds(cardX + cardW - 70, cardY + cardH - 32, 55, 20).build());
        }

        private String[] getVariants(String category) {
            if ("Block".equals(category)) return new String[]{ "Hardest", "Most Count" };
            return new String[]{ "Best" };
        }

        private String getDescription() {
            if ("Empty".equals(currentTab)) {
                return "Stop rule\nThis slot will be left empty and no later rules will be tried.\nPlace this rule below specific/type rules to short-circuit.";
            }
            if ("Specific".equals(currentTab)) return "Pick an exact item ID with required enchantments.";
            if ("Conditional".equals(currentTab)) return "Branch on whether another rule found an item.";

            String cat = CATEGORIES[categoryIndex];
            String var = getVariants(cat)[variantIndex % getVariants(cat).length];
            if ("Block".equals(cat) && "Hardest".equals(var)) return "Pick a category and the variant rule.\nBlock with the highest hardness value.";
            if ("Block".equals(cat) && "Most Count".equals(var)) return "Pick a category and the variant rule.\nBlock with the largest quantity stack in your bag.";
            if ("Sword".equals(cat)) return "Pick a category and the variant rule.\nHighest expected damage / DPS in this category.";
            if ("Food".equals(cat)) return "Pick a category and the variant rule.\nHighest nutritional and saturation value.";
            return "Pick a category and the variant rule.\nOptimal choice in this category.";
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);

            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            // Background Card
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF5131822);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFF354256);
            graphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFF354256);
            graphics.fill(cardX, cardY, cardX + 1, cardY + cardH, 0xFF354256);
            graphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0xFF354256);

            // Title & Priority Badge
            graphics.text(this.font, "Add rule", cardX + 16, cardY + 12, 0xFFFFFF, false);
            int rulesCount = Config.getRulesForSlot(slotIndex).size();
            graphics.fill(cardX + cardW - 85, cardY + 8, cardX + cardW - 16, cardY + 22, 0xFF242C3B);
            graphics.text(this.font, "Priority: " + (rulesCount + 1), cardX + cardW - 77, cardY + 11, 0xCCCCCC, false);

            // Active tab green line
            String[] tabs = { "Specific", "Type", "Conditional", "Empty" };
            int tabW = (cardW - 32) / 4;
            for (int t = 0; t < tabs.length; t++) {
                if (tabs[t].equals(currentTab)) {
                    int tX = cardX + 16 + t * tabW;
                    graphics.fill(tX, cardY + 44, tX + tabW - 4, cardY + 46, 0xFF2ECC71);
                }
            }

            // Description Box
            String[] descLines = getDescription().split("\n");
            int textY = cardY + 104;
            for (String line : descLines) {
                graphics.text(this.font, line, cardX + 24, textY, 0xA0B0C0, false);
                textY += 12;
            }
        }
    }
}