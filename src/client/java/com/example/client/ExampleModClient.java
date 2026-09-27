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

        // Native 26.1 HUD Element - Renders hotbar upgrade alerts on-screen while walking around!
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("autohotbar", "hotbar_hud"),
            (graphics, deltaTracker) -> Renderer.renderHudHotbar(graphics)
        );

        // Farm & Raid Optimized Tick loop: only runs when items actually change
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (toggleKey.consumeClick()) {
                Config.toggle();
            }

            while (openConfigKey.consumeClick()) {
                client.setScreen(new ConfigScreen(client.screen));
            }

            Engine.tick(client);
        });
    }

    // ==========================================
    // 1. CONFIGURATION
    // ==========================================
    public static class Config {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("autohotbar_fairplay.json").toFile();

        public static class SlotRule {
            public String category;
            public String specificId;

            public SlotRule(String category, String specificId) {
                this.category = category;
                this.specificId = specificId;
            }
        }

        public static class Data {
            public boolean enabled = true;
            public List<SlotRule> slotRules = new ArrayList<>();

            public Data() {
                slotRules.add(new SlotRule("SWORD", ""));
                slotRules.add(new SlotRule("PICKAXE", ""));
                slotRules.add(new SlotRule("AXE", ""));
                slotRules.add(new SlotRule("SHOVEL", ""));
                slotRules.add(new SlotRule("TORCH", ""));
                slotRules.add(new SlotRule("BLOCK", ""));
                slotRules.add(new SlotRule("BLOCK", ""));
                slotRules.add(new SlotRule("FOOD", ""));
                slotRules.add(new SlotRule("WATER_BUCKET", ""));
            }
        }

        public static Data data = new Data();

        public static void load() {
            if (FILE.exists()) {
                try (Reader reader = new FileReader(FILE)) {
                    Data loaded = GSON.fromJson(reader, Data.class);
                    if (loaded != null && loaded.slotRules != null && loaded.slotRules.size() == 9) {
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

        public static boolean isEnabled() {
            return data.enabled;
        }

        public static void toggle() {
            data.enabled = !data.enabled;
            save();
        }

        public static SlotRule getRule(int slot) {
            if (slot >= 0 && slot < data.slotRules.size()) return data.slotRules.get(slot);
            return new SlotRule("EMPTY", "");
        }

        public static void setRule(int slot, SlotRule rule) {
            if (slot >= 0 && slot < data.slotRules.size()) {
                data.slotRules.set(slot, rule);
                save();
            }
        }
    }

    // ==========================================
    // 2. ENGINE (FARM OPTIMIZED)
    // ==========================================
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
            if (tickCounter % 4 != 0) return;

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
                Config.SlotRule rule = Config.getRule(hotbarSlot);
                if ("EMPTY".equalsIgnoreCase(rule.category)) continue;

                double highestScore = -1.0;
                int bestSlot = -1;

                for (int i = 0; i < 36; i++) {
                    if (claimedInvSlots.contains(i)) continue;
                    ItemStack stack = inv.getItem(i);
                    if (stack.isEmpty()) continue;

                    double s = score(stack, rule);
                    if (s > highestScore) {
                        highestScore = s;
                        bestSlot = i;
                    }
                }

                if (bestSlot != -1) {
                    claimedInvSlots.add(bestSlot);
                    if (bestSlot != hotbarSlot) {
                        if (bestSlot >= 9 && bestSlot <= 35) {
                            TARGET_HOTBAR_FOR_INV_SLOT[bestSlot] = hotbarSlot;
                        }
                        HOTBAR_NEEDS_UPGRADE[hotbarSlot] = true;
                    }
                } else {
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

        public static double score(ItemStack stack, Config.SlotRule rule) {
            if (stack.isEmpty()) return -1.0;
            Item item = stack.getItem();
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

            if ("EMPTY".equalsIgnoreCase(rule.category)) return -1.0;

            if ("SPECIFIC".equalsIgnoreCase(rule.category)) {
                if (itemId.equalsIgnoreCase(rule.specificId)) return 100.0 + stack.getCount();
                return -1.0;
            }

            switch (rule.category.toUpperCase()) {
                case "SWORD":
                    if (stack.is(ItemTags.SWORDS) || itemId.contains("sword") || itemId.contains("mace")) {
                        return getToolTierScore(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "PICKAXE":
                    if (stack.is(ItemTags.PICKAXES) || itemId.contains("pickaxe")) {
                        return getToolTierScore(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "AXE":
                    if (stack.is(ItemTags.AXES) || (itemId.contains("axe") && !itemId.contains("pickaxe"))) {
                        return getToolTierScore(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "SHOVEL":
                    if (stack.is(ItemTags.SHOVELS) || itemId.contains("shovel")) {
                        return getToolTierScore(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "HOE":
                    if (stack.is(ItemTags.HOES) || itemId.contains("hoe")) {
                        return getToolTierScore(itemId) * 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "FOOD":
                    if (stack.has(DataComponents.FOOD)) {
                        FoodProperties food = stack.get(DataComponents.FOOD);
                        if (food != null) {
                            return food.nutrition() * 2.0 + food.saturation() + (stack.getCount() * 0.01);
                        }
                        return 10.0 + stack.getCount();
                    }
                    return -1.0;
                case "BLOCK":
                    if (item instanceof BlockItem && !itemId.contains("torch") && !itemId.contains("sapling")) {
                        return stack.getCount();
                    }
                    return -1.0;
                case "TORCH":
                    if (itemId.contains("torch") || itemId.contains("lantern")) return 50.0 + stack.getCount();
                    return -1.0;
                case "WATER_BUCKET":
                    if (itemId.equals("minecraft:water_bucket") || itemId.equals("minecraft:ender_pearl") || itemId.equals("minecraft:totem_of_undying")) {
                        return 50.0 + stack.getCount();
                    }
                    return -1.0;
                default:
                    return -1.0;
            }
        }

        private static double getToolTierScore(String itemId) {
            if (itemId.contains("netherite")) return 6.0;
            if (itemId.contains("diamond")) return 5.0;
            if (itemId.contains("iron")) return 4.0;
            if (itemId.contains("stone")) return 3.0;
            if (itemId.contains("gold")) return 2.0;
            if (itemId.contains("wood")) return 1.0;
            return 3.5;
        }
    }

    // ==========================================
    // 3. VULKAN & SODIUM SAFE RENDERER
    // ==========================================
    public static class Renderer {
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

                    graphics.fill(x, y - 2, x + 16, y, 0xFF00FF88);
                    graphics.fill(x - 1, y - 2, x, y + 16, 0x8800FF88);
                    graphics.fill(x + 16, y - 2, x + 17, y + 16, 0x8800FF88);
                    graphics.fill(x - 1, y + 16, x + 17, y + 17, 0x8800FF88);
                    graphics.fill(x + 12, y - 1, x + 15, y + 2, 0xFF00FF88);
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

                graphics.fill(x, y, x + 16, y + 1, 0xFF00FF88);
                graphics.fill(x, y + 15, x + 16, y + 16, 0xFF00FF88);
                graphics.fill(x, y + 1, x + 1, y + 15, 0xFF00FF88);
                graphics.fill(x + 15, y + 1, x + 16, y + 15, 0xFF00FF88);
                graphics.fill(x + 1, y + 1, x + 15, y + 15, 0x3300FF88);

                String keyName = Engine.getKeyName(targetHotbarSlot);
                Font font = client.font;
                int textWidth = font.width(keyName);
                int badgeW = Math.max(textWidth + 3, 8);
                int badgeH = 9;

                graphics.fill(x - 1, y - 1, x + badgeW, y + badgeH, 0xEE000000);
                graphics.fill(x - 1, y - 1, x + badgeW, y, 0xFF00FF88);
                graphics.text(font, keyName, x + 1, y, 0xFFFFFFFF, false);
            }
        }
    }

    // ==========================================
    // 4. CONFIG SCREEN
    // ==========================================
    public static class ConfigScreen extends Screen {
        private final Screen parent;
        private int selectedSlot = 0;
        private static final String[] CATEGORIES = {
            "SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE", "FOOD", "BLOCK", "TORCH", "WATER_BUCKET", "EMPTY"
        };

        public ConfigScreen(Screen parent) {
            super(Component.literal("AutoHotbar Fairplay"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int startY = 40;

            this.addRenderableWidget(Button.builder(
                Component.literal("Enabled: " + (Config.isEnabled() ? "ON" : "OFF")),
                btn -> {
                    Config.toggle();
                    btn.setMessage(Component.literal("Enabled: " + (Config.isEnabled() ? "ON" : "OFF")));
                }
            ).bounds(this.width - 110, 10, 100, 20).build());

            int slotStartX = centerX - 90;
            for (int i = 0; i < 9; i++) {
                final int slotIndex = i;
                this.addRenderableWidget(Button.builder(
                    Component.literal(String.valueOf(i + 1)),
                    btn -> this.selectedSlot = slotIndex
                ).bounds(slotStartX + i * 20, startY + 20, 18, 20).build());
            }

            this.addRenderableWidget(Button.builder(
                Component.literal("Change Rule: " + Config.getRule(selectedSlot).category),
                btn -> {
                    Config.SlotRule current = Config.getRule(selectedSlot);
                    int nextIndex = 0;
                    for (int j = 0; j < CATEGORIES.length; j++) {
                        if (CATEGORIES[j].equalsIgnoreCase(current.category)) {
                            nextIndex = (j + 1) % CATEGORIES.length;
                            break;
                        }
                    }
                    Config.setRule(selectedSlot, new Config.SlotRule(CATEGORIES[nextIndex], ""));
                    btn.setMessage(Component.literal("Change Rule: " + CATEGORIES[nextIndex]));
                }
            ).bounds(centerX - 100, startY + 70, 200, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                btn -> this.onClose()
            ).bounds(centerX - 75, this.height - 30, 150, 20).build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);

            int centerX = graphics.guiWidth() / 2;
            String title = "AutoHotbar Fairplay - Configuration";
            graphics.text(this.font, title, centerX - this.font.width(title) / 2, 15, 0xFFFFFF, false);

            String currentInfo = "Selected Slot " + (selectedSlot + 1) + ": " + Config.getRule(selectedSlot).category;
            graphics.text(this.font, currentInfo, centerX - this.font.width(currentInfo) / 2, 100, 0x00FF88, false);

            int slotStartX = centerX - 90;
            int selX = slotStartX + selectedSlot * 20;
            int selY = 60;
            graphics.fill(selX - 1, selY - 1, selX + 19, selY, 0xFF00FF88);
            graphics.fill(selX - 1, selY + 20, selX + 19, selY + 21, 0xFF00FF88);
            graphics.fill(selX - 1, selY, selX, selY + 20, 0xFF00FF88);
            graphics.fill(selX + 18, selY, selX + 19, selY + 20, 0xFF00FF88);
        }

        @Override
        public void onClose() {
            Config.save();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }
    }
}