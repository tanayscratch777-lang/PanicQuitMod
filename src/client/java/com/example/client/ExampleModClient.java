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
    public static KeyMapping panicKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        // 1. AutoHotbar Toggle (Default: None)
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.autohotbar.toggle",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
        ));

        // 2. Open Config Screen (Default: H)
        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.autohotbar.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
        ));

        // 3. Built-in Panic Quit Key (Default: I, triggers with Left Alt)
        panicKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.autohotbar.panic",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            CATEGORY
        ));

        // HUD Hotbar Indicator (renders cleanly on in-game HUD while walking around)
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("autohotbar", "hotbar_hud"),
            (graphics, deltaTracker) -> Renderer.renderHudHotbar(graphics)
        );

        // Client Tick: handles keys & farm-optimized throttled inventory evaluation
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
    // PANIC BUTTON LOGIC (INTEGRATED)
    // ==========================================================
    public static boolean checkPanic(int key, int scancode, int modifiers) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || panicKey == null) return false;

        boolean keyMatches = (panicKey.matches(key, scancode));
        boolean altHeld = (modifiers & GLFW.GLFW_MOD_ALT) != 0 
                || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_ALT);

        if (keyMatches && altHeld) {
            long window = client.getWindow().handle();
            GLFW.glfwIconifyWindow(window);

            if (client.level == null) {
                System.exit(0);
                return true;
            }

            new Thread(() -> {
                try {
                    if (client.getSingleplayerServer() != null) {
                        client.getSingleplayerServer().halt(false);
                    }
                } catch (Exception ignored) {
                } finally {
                    System.exit(0);
                }
            }, "PanicQuit-Thread").start();

            return true;
        }

        return false;
    }

    // ==========================================================
    // 1. CONFIGURATION SYSTEM
    // ==========================================================
    public static class Config {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("autohotbar_fairplay.json").toFile();

        public static class Rule {
            public String ruleKind = "Type"; // Type, Specific, Conditional, Empty
            public String category = "Sword";
            public String variant = "Best DPS";
            public String specificId = "";
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
                    String base = "Item: " + (specificId.isEmpty() ? "None" : specificId);
                    return mustBeEnchanted ? base + " (Enchanted)" : base;
                }
                if ("Conditional".equalsIgnoreCase(ruleKind)) return "Conditional Rule";
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
                // Default setup
                slots.get(0).add(new Rule("Type", "Sword", "Best DPS", ""));
                slots.get(1).add(new Rule("Type", "Pickaxe", "Best Tier", ""));
                slots.get(2).add(new Rule("Type", "Axe", "Best Weapon", ""));
                slots.get(3).add(new Rule("Type", "Shovel", "Best Tier", ""));
                slots.get(4).add(new Rule("Type", "Utility", "Torches", ""));
                slots.get(5).add(new Rule("Type", "Block", "Hardest", ""));
                slots.get(6).add(new Rule("Type", "Block", "Most Count", ""));
                slots.get(7).add(new Rule("Type", "Food", "Best Saturation", ""));
                slots.get(8).add(new Rule("Type", "Utility", "Water Bucket", ""));
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
    // 2. ENGINE (FARM OPTIMIZED, NO GHOST MARKS)
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
                        bestSlot = -2; // Stop rule: slot remains empty
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

                // ONLY trigger upgrade if an item is found AND is not already in the hotbar slot!
                if (bestSlot >= 0) {
                    claimedInvSlots.add(bestSlot);
                    if (bestSlot != hotbarSlot) {
                        TARGET_HOTBAR_FOR_INV_SLOT[bestSlot] = hotbarSlot;
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
                if (!itemId.equalsIgnoreCase(rule.specificId)) return -1.0;
                if (rule.mustBeEnchanted && !stack.isEnchanted()) return -1.0;
                return 100.0 + stack.getCount();
            }

            switch (rule.category) {
                case "Sword":
                    if (stack.is(ItemTags.SWORDS) || itemId.contains("sword")) {
                        double tier = getTier(itemId);
                        if ("Netherite Only".equals(rule.variant) && tier < 6.0) return -1.0;
                        if ("Diamond+".equals(rule.variant) && tier < 5.0) return -1.0;
                        return tier * 10.0 + stack.getCount();
                    }
                    return -1.0;

                case "Pickaxe":
                    if (stack.is(ItemTags.PICKAXES) || itemId.contains("pickaxe")) {
                        double s = getTier(itemId) * 10.0;
                        if ("Silk Touch Preferred".equals(rule.variant) && stack.isEnchanted()) s += 15.0;
                        if ("Fortune Preferred".equals(rule.variant) && stack.isEnchanted()) s += 15.0;
                        return s + stack.getCount();
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
                        } else if ("Soft Utility".equalsIgnoreCase(rule.variant)) {
                            if (itemId.contains("dirt") || itemId.contains("cobble") || itemId.contains("netherrack")) {
                                return 50.0 + stack.getCount();
                            }
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
                            return food.nutrition() * 2.0 + food.saturation() + (stack.getCount() * 0.01);
                        }
                        return 10.0 + stack.getCount();
                    }
                    return -1.0;

                case "Ranged / Weapons":
                    if (rule.variant.equalsIgnoreCase("Bow") && itemId.contains("bow") && !itemId.contains("crossbow")) return 50.0;
                    if (rule.variant.equalsIgnoreCase("Crossbow") && itemId.contains("crossbow")) return 50.0;
                    if (rule.variant.equalsIgnoreCase("Trident") && itemId.contains("trident")) return 50.0;
                    if (rule.variant.equalsIgnoreCase("Mace") && itemId.contains("mace")) return 50.0;
                    return -1.0;

                case "Utility":
                    if (rule.variant.equalsIgnoreCase("Torches") && (itemId.contains("torch") || itemId.contains("lantern"))) return 50.0 + stack.getCount();
                    if (rule.variant.equalsIgnoreCase("Water Bucket") && itemId.equals("minecraft:water_bucket")) return 100.0;
                    if (rule.variant.equalsIgnoreCase("Ender Pearl") && itemId.equals("minecraft:ender_pearl")) return 50.0 + stack.getCount();
                    if (rule.variant.equalsIgnoreCase("Golden Apple") && itemId.contains("golden_apple")) return 50.0 + stack.getCount();
                    if (rule.variant.equalsIgnoreCase("Totem of Undying") && itemId.equals("minecraft:totem_of_undying")) return 100.0;
                    if (rule.variant.equalsIgnoreCase("Shield") && itemId.equals("minecraft:shield")) return 50.0;
                    if (rule.variant.equalsIgnoreCase("Firework Rocket") && itemId.equals("minecraft:firework_rocket")) return 50.0 + stack.getCount();
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
    // 3. CLEAN RENDERER (VULKAN & SODIUM PROOF)
    // ==========================================================
    public static class Renderer {
        public static final int EMERALD_GREEN = 0xFF10B981;
        public static final int EMERALD_TINT = 0x3510B981;
        public static final int BADGE_BG = 0xEE111827;

        public static void renderHudHotbar(GuiGraphicsExtractor graphics) {
            if (!Config.isEnabled()) return;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            int midX = graphics.guiWidth() / 2;
            int hotbarX = midX - 90;
            int hotbarY = graphics.guiHeight() - 22;

            for (int s = 0; s < 9; s++) {
                if (Engine.slotNeedsUpgrade(s)) {
                    int x = hotbarX + s * 20 + 3;
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

            // 1. Highlight source item in main inventory
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

            // 2. Also subtly highlight the target hotbar slot in the inventory screen
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

            // Glass ON/OFF button
            this.addRenderableWidget(Button.builder(
                Component.literal("Glass " + (Config.data.glassMode ? "ON" : "OFF")),
                btn -> {
                    Config.data.glassMode = !Config.data.glassMode;
                    Config.save();
                    btn.setMessage(Component.literal("Glass " + (Config.data.glassMode ? "ON" : "OFF")));
                }
            ).bounds(cardX + cardW - 80, cardY + 12, 70, 18).build());

            // 9 Slot Buttons
            int slotStart = cardX + (cardW / 2) - 95;
            for (int i = 0; i < 9; i++) {
                final int idx = i;
                this.addRenderableWidget(Button.builder(
                    Component.literal(String.valueOf(i + 1)),
                    btn -> { this.selectedSlot = idx; this.rebuildWidgets(); }
                ).bounds(slotStart + i * 21, cardY + 54, 19, 18).build());
            }

            // Delete buttons for rules
            List<Config.Rule> rules = Config.getRulesForSlot(selectedSlot);
            int listY = cardY + 98;
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
            int bg = Config.data.glassMode ? 0xB80D111A : 0xFA0D111A;
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, bg);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0x605B708B);
            graphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0x605B708B);
            graphics.fill(cardX, cardY, cardX + 1, cardY + cardH, 0x605B708B);
            graphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0x605B708B);

            // Title & Subtitle
            graphics.text(this.font, "AutoHotbar Fairplay", cardX + 16, cardY + 14, 0xFFFFFFFF, false);
            graphics.text(this.font, "Keybind opens inventory with hotbar-key labels — no auto swaps.", cardX + 16, cardY + 26, 0xFF94A3B8, false);

            // Tabs: [Complex] [Simple]
            graphics.fill(cardX + 16, cardY + 38, cardX + 75, cardY + 52, 0xFF1E293B);
            graphics.fill(cardX + 16, cardY + 51, cardX + 75, cardY + 53, 0xFF10B981);
            graphics.text(this.font, "Complex", cardX + 26, cardY + 41, 0xFFFFFFFF, false);

            graphics.fill(cardX + 80, cardY + 38, cardX + 135, cardY + 52, 0x501E293B);
            graphics.text(this.font, "Simple", cardX + 92, cardY + 41, 0xFF64748B, false);

            // Emerald Ring around Selected Hotbar Slot
            int slotStart = cardX + (cardW / 2) - 95;
            int selX = slotStart + selectedSlot * 21;
            graphics.fill(selX - 1, cardY + 53, selX + 20, cardY + 54, 0xFF10B981);
            graphics.fill(selX - 1, cardY + 72, selX + 20, cardY + 73, 0xFF10B981);
            graphics.fill(selX - 1, cardY + 53, selX, cardY + 73, 0xFF10B981);
            graphics.fill(selX + 19, cardY + 53, selX + 20, cardY + 73, 0xFF10B981);

            List<Config.Rule> rules = Config.getRulesForSlot(selectedSlot);
            graphics.text(this.font, "Slot " + (selectedSlot + 1) + "   " + rules.size() + " rules — lower # wins", cardX + 16, cardY + 80, 0xFF94A3B8, false);

            int boxY = cardY + 92;
            int boxH = cardH - 134;
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
    // 5. ALL CATEGORIES & VARIANTS "ADD RULE" SCREEN
    // ==========================================================
    public static class AddRuleScreen extends Screen {
        private final ModernConfigScreen parent;
        private final int slotIndex;

        private String currentTab = "Type"; // Specific, Type, Conditional, Empty
        private static final String[] CATEGORIES = {
            "Sword", "Pickaxe", "Axe", "Shovel", "Block", "Food", "Ranged / Weapons", "Utility"
        };
        private int categoryIndex = 0;
        private int variantIndex = 0;

        // Specific Item Choice
        private static final String[] COMMON_SPECIFICS = {
            "minecraft:water_bucket", "minecraft:ender_pearl", "minecraft:golden_apple",
            "minecraft:totem_of_undying", "minecraft:shield", "minecraft:cobblestone",
            "minecraft:firework_rocket", "minecraft:torch"
        };
        private int specificPickIndex = 0;
        private boolean mustBeEnchanted = false;

        // Conditional Choice
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
                        this.rebuildWidgets();
                    }
                ).bounds(cardX + cardW - 200, cardY + 68, 175, 20).build());
            }

            // CONTROLS FOR "Specific"
            if ("Specific".equals(currentTab)) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("Item: " + COMMON_SPECIFICS[specificPickIndex].replace("minecraft:", "")),
                    btn -> {
                        specificPickIndex = (specificPickIndex + 1) % COMMON_SPECIFICS.length;
                        btn.setMessage(Component.literal("Item: " + COMMON_SPECIFICS[specificPickIndex].replace("minecraft:", "")));
                    }
                ).bounds(cardX + 24, cardY + 68, 200, 20).build());

                this.addRenderableWidget(Button.builder(
                    Component.literal("Enchanted: " + (mustBeEnchanted ? "YES" : "NO")),
                    btn -> {
                        mustBeEnchanted = !mustBeEnchanted;
                        btn.setMessage(Component.literal("Enchanted: " + (mustBeEnchanted ? "YES" : "NO")));
                    }
                ).bounds(cardX + cardW - 140, cardY + 68, 115, 20).build());
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
                        Config.Rule r = new Config.Rule("Specific", "", "", COMMON_SPECIFICS[specificPickIndex]);
                        r.mustBeEnchanted = this.mustBeEnchanted;
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
                case "Sword" -> new String[]{ "Best DPS", "Fastest Attack", "Most Durable", "Netherite Only", "Diamond+" };
                case "Pickaxe" -> new String[]{ "Best Tier", "Most Durable", "Silk Touch Preferred", "Fortune Preferred" };
                case "Axe" -> new String[]{ "Best Weapon", "Best Tool" };
                case "Shovel" -> new String[]{ "Best Tier", "Silk Touch Preferred" };
                case "Block" -> new String[]{ "Hardest", "Most Count", "Soft Utility" };
                case "Food" -> new String[]{ "Best Saturation", "Highest Nutrition", "Fast Eating" };
                case "Ranged / Weapons" -> new String[]{ "Bow", "Crossbow", "Trident", "Mace" };
                case "Utility" -> new String[]{ "Torches", "Water Bucket", "Ender Pearl", "Golden Apple", "Totem of Undying", "Shield", "Firework Rocket" };
                default -> new String[]{ "Best" };
            };
        }

        private String getDescription() {
            if ("Empty".equals(currentTab)) {
                return "Stop rule\nThis slot will be left empty and no later rules will be tried.\nPlace this rule below specific/type rules to short-circuit.";
            }
            if ("Specific".equals(currentTab)) {
                return "Pick an exact item ID.\nItem will be prioritized for this slot regardless of category.";
            }
            if ("Conditional".equals(currentTab)) {
                return "Branch on whether another rule found an item.\nUseful for dynamic hotbars (e.g. Shield if holding 1-handed weapon).";
            }

            String cat = CATEGORIES[categoryIndex];
            String var = getVariants(cat)[variantIndex % getVariants(cat).length];

            if ("Block".equals(cat)) {
                if ("Hardest".equals(var)) return "Block with highest blast resistance and hardness (Obsidian, Deepslate, Stone).";
                if ("Most Count".equals(var)) return "Block with the largest quantity stack in your bag.";
                return "Soft utility blocks like dirt, netherrack, cobblestone for pillaring.";
            }
            if ("Sword".equals(cat)) {
                if ("Best DPS".equals(var)) return "Highest expected damage and weapon tier.";
                if ("Fastest Attack".equals(var)) return "Sword with maximum attack speed recovery.";
                return "Swords filtered by durability or tier constraints.";
            }
            if ("Food".equals(cat)) {
                if ("Best Saturation".equals(var)) return "Foods that keep hunger full longest (Golden Carrot, Steak, Porkchop).";
                return "Highest nutritional restoration or fast-eating items.";
            }
            return "Optimal items in the " + cat + " category filtered by " + var + ".";
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            // Dark Glass Panel
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xFA0D111A);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0x605B708B);
            graphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0x605B708B);
            graphics.fill(cardX, cardY, cardX + 1, cardY + cardH, 0x605B708B);
            graphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0x605B708B);

            // Title & Priority Badge
            graphics.text(this.font, "Add rule", cardX + 16, cardY + 12, 0xFFFFFFFF, false);
            int rulesCount = Config.getRulesForSlot(slotIndex).size();
            graphics.fill(cardX + cardW - 85, cardY + 8, cardX + cardW - 16, cardY + 22, 0xFF1E293B);
            graphics.text(this.font, "Priority: " + (rulesCount + 1), cardX + cardW - 77, cardY + 11, 0xFFCBD5E1, false);

            // Emerald Active Tab Underline
            String[] tabs = { "Specific", "Type", "Conditional", "Empty" };
            int tabW = (cardW - 32) / 4;
            for (int t = 0; t < tabs.length; t++) {
                if (tabs[t].equals(currentTab)) {
                    int tX = cardX + 16 + t * tabW;
                    graphics.fill(tX, cardY + 44, tX + tabW - 4, cardY + 46, 0xFF10B981);
                }
            }

            // Description Lines
            String[] descLines = getDescription().split("\n");
            int textY = cardY + 104;
            for (String line : descLines) {
                graphics.text(this.font, line, cardX + 24, textY, 0xFF94A3B8, false);
                textY += 12;
            }

            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }
}