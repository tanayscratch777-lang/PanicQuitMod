package com.example.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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

public class AutoHotbarClient implements ClientModInitializer, ModMenuApi {
    public static KeyBinding toggleKey;
    public static KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.autohotbar.toggle",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            "key.categories.autohotbar"
        ));

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.autohotbar.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.autohotbar"
        ));

        HudRenderCallback.EVENT.register((context, tickCounter) -> Renderer.renderHudHotbar(context));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (toggleKey.wasPressed()) Config.toggle();
            while (openConfigKey.wasPressed()) client.setScreen(new LiquidGlassConfigScreen(client.currentScreen));
            Engine.tick(client);
        });
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LiquidGlassConfigScreen::new;
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
            public String specificId = "minecraft:water_bucket";
            public List<String> requiredEnchants = new ArrayList<>();
            public List<String> blacklistedEnchants = new ArrayList<>();
            public boolean mustBeEnchanted = false;

            public Rule(String ruleKind, String category, String variant, String specificId) {
                this.ruleKind = ruleKind;
                this.category = category;
                this.variant = variant;
                this.specificId = specificId;
            }

            public String getDisplayText() {
                if ("Specific".equalsIgnoreCase(ruleKind)) {
                    String clean = formatName(specificId);
                    if (!requiredEnchants.isEmpty()) clean += " [+" + requiredEnchants.size() + " enchants]";
                    return mustBeEnchanted ? clean + " (Enchanted)" : clean;
                }
                String desc = category + " (" + variant + ")";
                if (!requiredEnchants.isEmpty()) desc += " [+" + formatName(requiredEnchants.get(0)) + "]";
                if (!blacklistedEnchants.isEmpty()) desc += " [-" + formatName(blacklistedEnchants.get(0)) + "]";
                return desc;
            }
        }

        public static class Data {
            public boolean enabled = true;
            public boolean glassMode = true;
            public List<List<Rule>> slots = new ArrayList<>();

            public Data() {
                for (int i = 0; i < 9; i++) slots.add(new ArrayList<>());
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

    public static String formatName(String id) {
        String clean = id.replace("minecraft:", "").replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : clean.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    // ==========================================================
    // 2. ENGINE (FARM OPTIMIZED)
    // ==========================================================
    public static class Engine {
        private static final int[] TARGET_HOTBAR_FOR_INV_SLOT = new int[36];
        private static final boolean[] HOTBAR_NEEDS_UPGRADE = new boolean[9];
        private static int tickCounter = 0;

        public static void tick(MinecraftClient client) {
            if (!Config.isEnabled() || client.player == null) {
                Arrays.fill(TARGET_HOTBAR_FOR_INV_SLOT, -1);
                Arrays.fill(HOTBAR_NEEDS_UPGRADE, false);
                return;
            }

            tickCounter++;
            if (tickCounter % 5 != 0) return;

            evaluate(client.player.getInventory());
        }

        private static void evaluate(PlayerInventory inv) {
            Arrays.fill(TARGET_HOTBAR_FOR_INV_SLOT, -1);
            Arrays.fill(HOTBAR_NEEDS_UPGRADE, false);
            Set<Integer> claimedInvSlots = new HashSet<>();

            for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                List<Config.Rule> rules = Config.getRulesForSlot(hotbarSlot);
                if (rules.isEmpty()) continue;

                int bestSlot = -1;

                for (Config.Rule rule : rules) {
                    double highestScore = -1.0;
                    int candidate = -1;

                    for (int i = 0; i < 36; i++) {
                        if (claimedInvSlots.contains(i)) continue;
                        ItemStack stack = inv.getStack(i);
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
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options != null && hotbarSlot >= 0 && hotbarSlot < client.options.hotbarKeys.length) {
                return client.options.hotbarKeys[hotbarSlot].getBoundKeyLocalizedText().getString();
            }
            return String.valueOf(hotbarSlot + 1);
        }

        public static double score(ItemStack stack, Config.Rule rule) {
            if (stack.isEmpty()) return -1.0;
            Item item = stack.getItem();
            String itemId = Registries.ITEM.getId(item).toString();
            String enchantData = stack.contains(DataComponentTypes.ENCHANTMENTS) ? stack.get(DataComponentTypes.ENCHANTMENTS).toString().toLowerCase() : "";

            for (String blacklisted : rule.blacklistedEnchants) {
                if (enchantData.contains(blacklisted.toLowerCase())) return -1.0;
            }
            for (String required : rule.requiredEnchants) {
                if (!enchantData.contains(required.toLowerCase())) return -1.0;
            }

            if ("Specific".equalsIgnoreCase(rule.ruleKind)) {
                if (!itemId.equalsIgnoreCase(rule.specificId)) return -1.0;
                if (rule.mustBeEnchanted && !stack.hasEnchantments()) return -1.0;
                return 100.0 + stack.getCount();
            }

            switch (rule.category) {
                case "Sword":
                    if (stack.isIn(ItemTags.SWORDS) || itemId.contains("sword")) {
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
                    if (stack.isIn(ItemTags.PICKAXES) || itemId.contains("pickaxe")) {
                        double s = getTier(itemId) * 10.0;
                        if ("With Silk Touch".equals(rule.variant) && !enchantData.contains("silk_touch")) return -1.0;
                        if ("With Fortune".equals(rule.variant) && !enchantData.contains("fortune")) return -1.0;
                        return s + stack.getCount();
                    }
                    return -1.0;

                case "Axe":
                    if (stack.isIn(ItemTags.AXES) || (itemId.contains("axe") && !itemId.contains("pickaxe"))) {
                        double s = getTier(itemId) * 10.0;
                        if ("With Silk Touch".equals(rule.variant) && !enchantData.contains("silk_touch")) return -1.0;
                        if ("With Fortune".equals(rule.variant) && !enchantData.contains("fortune")) return -1.0;
                        return s + stack.getCount();
                    }
                    return -1.0;

                case "Shovel":
                    if (stack.isIn(ItemTags.SHOVELS) || itemId.contains("shovel")) {
                        double s = getTier(itemId) * 10.0;
                        if ("With Silk Touch".equals(rule.variant) && !enchantData.contains("silk_touch")) return -1.0;
                        if ("With Fortune".equals(rule.variant) && !enchantData.contains("fortune")) return -1.0;
                        return s + stack.getCount();
                    }
                    return -1.0;

                case "Hoe":
                    if (stack.isIn(ItemTags.HOES) || itemId.contains("hoe")) {
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
                            return block.getBlastResistance() * 10.0 + (stack.getCount() * 0.05);
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
                            return stack.getCount();
                        }
                    }
                    return -1.0;

                case "Food":
                    if (stack.contains(DataComponentTypes.FOOD)) {
                        FoodComponent food = stack.get(DataComponentTypes.FOOD);
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
                            return food.nutrition() * 2.0 + (stack.getCount() * 0.01);
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
    // 3. CLEAN RENDERER (VULKAN & SODIUM PROOF)
    // ==========================================================
    public static class Renderer {
        public static final int EMERALD_GREEN = 0xFF10B981;
        public static final int EMERALD_TINT = 0x4010B981;
        public static final int BADGE_BG = 0xF0111827;

        public static void renderHudHotbar(DrawContext context) {
            if (!Config.isEnabled()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            int hotbarX = (context.getScaledWindowWidth() - 182) / 2;
            int hotbarY = context.getScaledWindowHeight() - 22;

            for (int s = 0; s < 9; s++) {
                if (Engine.slotNeedsUpgrade(s)) {
                    int slotX = hotbarX + s * 20;
                    int slotY = hotbarY;

                    context.fill(slotX + 1, slotY + 1, slotX + 21, slotY + 3, EMERALD_GREEN);

                    String key = Engine.getKeyName(s);
                    int textW = client.textRenderer.getWidth(key);
                    int bW = Math.max(textW + 4, 9);
                    int bX = slotX + 20 - bW;
                    int bY = slotY - 4;

                    context.fill(bX, bY, bX + bW, bY + 8, BADGE_BG);
                    context.fill(bX, bY, bX + bW, bY + 1, EMERALD_GREEN);
                    context.drawText(client.textRenderer, key, bX + (bW - textW) / 2, bY + 1, 0xFFFFFFFF, false);
                }
            }
        }

        public static void renderContainerSlot(DrawContext context, Slot slot) {
            if (!Config.isEnabled()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            if (!(slot.inventory instanceof PlayerInventory)) return;
            int invSlot = slot.getIndex();

            if (invSlot < 0 || invSlot > 35) return;

            int targetHotbarSlot = Engine.getTargetHotbarSlot(invSlot);
            if (targetHotbarSlot >= 0 && targetHotbarSlot < 9) {
                int x = slot.x;
                int y = slot.y;

                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 300.0F);

                context.fill(x, y, x + 16, y + 16, EMERALD_TINT);
                context.fill(x, y, x + 16, y + 1, EMERALD_GREEN);
                context.fill(x, y + 15, x + 16, y + 16, EMERALD_GREEN);
                context.fill(x, y + 1, x + 1, y + 15, EMERALD_GREEN);
                context.fill(x + 15, y + 1, x + 16, y + 15, EMERALD_GREEN);

                String keyName = Engine.getKeyName(targetHotbarSlot);
                TextRenderer font = client.textRenderer;
                String badgeText = "->" + keyName;
                int textW = font.getWidth(badgeText);
                int badgeW = Math.max(textW + 4, 14);
                int badgeH = 8;
                int badgeX = x + 8 - (badgeW / 2);
                int badgeY = y - 4;

                context.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, BADGE_BG);
                context.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, EMERALD_GREEN);
                context.drawText(font, badgeText, badgeX + (badgeW - textW) / 2, badgeY + 1, 0xFFFFFFFF, false);

                context.getMatrices().pop();
            }

            if (invSlot >= 0 && invSlot <= 8 && Engine.slotNeedsUpgrade(invSlot)) {
                int x = slot.x;
                int y = slot.y;
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 300.0F);
                context.fill(x, y, x + 16, y + 1, EMERALD_GREEN);
                context.fill(x, y + 15, x + 16, y + 16, EMERALD_GREEN);
                context.fill(x, y + 1, x + 1, y + 15, EMERALD_GREEN);
                context.fill(x + 15, y + 1, x + 16, y + 15, EMERALD_GREEN);
                context.getMatrices().pop();
            }
        }
    }

    // ==========================================================
    // 4. TRUE LIQUID GLASS CONFIG SCREEN
    // ==========================================================
    public static class LiquidGlassConfigScreen extends Screen {
        private final Screen parent;
        private int selectedSlot = 0;

        public LiquidGlassConfigScreen(Screen parent) {
            super(Text.literal("AutoHotbar Fairplay"));
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
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(String.valueOf(i + 1)),
                    btn -> { this.selectedSlot = idx; this.clearAndInit(); }
                ).dimensions(slotStart + i * 21, cardY + 48, 19, 18).build());
            }

            List<Config.Rule> rules = Config.getRulesForSlot(selectedSlot);
            int listY = cardY + 92;
            for (int r = 0; r < Math.min(rules.size(), 3); r++) {
                final int rIdx = r;
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("✕"),
                    btn -> { Config.removeRule(selectedSlot, rIdx); this.clearAndInit(); }
                ).dimensions(cardX + cardW - 45, listY + r * 22, 20, 18).build());
            }

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("+ Add rule"),
                btn -> this.client.setScreen(new AddRuleScreen(this, selectedSlot))
            ).dimensions(cardX + 16, cardY + cardH - 32, 160, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                btn -> this.close()
            ).dimensions(cardX + cardW - 130, cardY + cardH - 32, 55, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"),
                btn -> { Config.save(); this.close(); }
            ).dimensions(cardX + cardW - 70, cardY + cardH - 32, 55, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            context.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xCC0A0E17);
            context.fill(cardX, cardY, cardX + cardW, cardY + 1, 0x904F6B90);
            context.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0x304F6B90);
            context.fill(cardX, cardY, cardX + 1, cardY + cardH, 0x504F6B90);
            context.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0x504F6B90);

            context.drawText(this.textRenderer, "AutoHotbar Fairplay", cardX + 16, cardY + 14, 0xFFFFFFFF, false);
            context.drawText(this.textRenderer, "Smart, anticheat-safe hotbar manager with instant overlay.", cardX + 16, cardY + 26, 0xFFAAB8C8, false);

            int slotStart = cardX + (cardW / 2) - 95;
            int selX = slotStart + selectedSlot * 21;
            context.fill(selX - 1, cardY + 47, selX + 20, cardY + 48, 0xFF10B981);
            context.fill(selX - 1, cardY + 66, selX + 20, cardY + 67, 0xFF10B981);
            context.fill(selX - 1, cardY + 47, selX, cardY + 67, 0xFF10B981);
            context.fill(selX + 19, cardY + 47, selX + 20, cardY + 67, 0xFF10B981);

            List<Config.Rule> rules = Config.getRulesForSlot(selectedSlot);
            context.drawText(this.textRenderer, "Slot " + (selectedSlot + 1) + "   " + rules.size() + " rules — lower # wins", cardX + 16, cardY + 74, 0xFFAAB8C8, false);

            int boxY = cardY + 86;
            int boxH = cardH - 128;
            context.fill(cardX + 16, boxY, cardX + cardW - 16, boxY + boxH, 0x80080B12);
            context.fill(cardX + 16, boxY, cardX + cardW - 16, boxY + 1, 0x50334155);

            if (rules.isEmpty()) {
                String emptyMsg = "No rules — click \"+ Add rule\" to start";
                context.drawText(this.textRenderer, emptyMsg, cardX + (cardW / 2) - this.textRenderer.getWidth(emptyMsg) / 2, boxY + 28, 0xFF708090, false);
            } else {
                for (int r = 0; r < Math.min(rules.size(), 3); r++) {
                    Config.Rule rule = rules.get(r);
                    int rY = boxY + 6 + (r * 22);
                    context.fill(cardX + 22, rY, cardX + cardW - 50, rY + 18, 0xAA1E293B);
                    context.drawText(this.textRenderer, (r + 1) + ". " + rule.getDisplayText(), cardX + 28, rY + 5, 0xFFFFFFFF, false);
                }
            }

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            Config.save();
            if (this.client != null) this.client.setScreen(this.parent);
        }
    }

    // ==========================================================
    // 5. ADD RULE SCREEN (ONLY TYPE & SPECIFIC TABS)
    // ==========================================================
    public static class AddRuleScreen extends Screen {
        private final LiquidGlassConfigScreen parent;
        private final int slotIndex;

        private String currentTab = "Type";
        private static final String[] CATEGORIES = {
            "Sword", "Bow", "Crossbow", "Trident", "Mace", "Pickaxe", "Axe", "Shovel", "Hoe", "Block", "Food", "Utility & Projectiles"
        };
        private int categoryIndex = 0;
        private int variantIndex = 0;

        public String chosenItemId = "minecraft:water_bucket";
        public List<String> requiredEnchants = new ArrayList<>();
        public List<String> blacklistedEnchants = new ArrayList<>();
        public boolean mustBeEnchanted = false;

        public AddRuleScreen(LiquidGlassConfigScreen parent, int slotIndex) {
            super(Text.literal("Add rule"));
            this.parent = parent;
            this.slotIndex = slotIndex;
        }

        @Override
        protected void init() {
            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            String[] tabs = { "Type", "Specific" };
            int tabW = (cardW - 32) / 2;
            for (int t = 0; t < tabs.length; t++) {
                final String tabName = tabs[t];
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(tabName),
                    btn -> { this.currentTab = tabName; this.clearAndInit(); }
                ).dimensions(cardX + 16 + t * tabW, cardY + 28, tabW - 4, 18).build());
            }

            if ("Type".equals(currentTab)) {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Category: " + CATEGORIES[categoryIndex]),
                    btn -> {
                        categoryIndex = (categoryIndex + 1) % CATEGORIES.length;
                        variantIndex = 0;
                        this.clearAndInit();
                    }
                ).dimensions(cardX + 24, cardY + 68, 175, 20).build());

                String[] variants = getVariants(CATEGORIES[categoryIndex]);
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Variant: " + variants[variantIndex % variants.length]),
                    btn -> {
                        variantIndex = (variantIndex + 1) % variants.length;
                        btn.setMessage(Text.literal("Variant: " + variants[variantIndex % variants.length]));
                    }
                ).dimensions(cardX + cardW - 200, cardY + 68, 175, 20).build());

                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Enchants (" + requiredEnchants.size() + " req, " + blacklistedEnchants.size() + " ban)"),
                    btn -> this.client.setScreen(new EnchantPickerScreen(this))
                ).dimensions(cardX + 24, cardY + 92, 200, 18).build());
            }

            if ("Specific".equals(currentTab)) {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Pick Item... (" + formatName(chosenItemId) + ")"),
                    btn -> this.client.setScreen(new ItemPickerScreen(this))
                ).dimensions(cardX + 48, cardY + 60, 200, 20).build());

                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Enchants (" + requiredEnchants.size() + ")"),
                    btn -> this.client.setScreen(new EnchantPickerScreen(this))
                ).dimensions(cardX + cardW - 160, cardY + 60, 135, 20).build());

                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Must be Enchanted: " + (mustBeEnchanted ? "YES" : "NO")),
                    btn -> {
                        mustBeEnchanted = !mustBeEnchanted;
                        btn.setMessage(Text.literal("Must be Enchanted: " + (mustBeEnchanted ? "YES" : "NO")));
                    }
                ).dimensions(cardX + 48, cardY + 86, 200, 18).build());
            }

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                btn -> this.client.setScreen(this.parent)
            ).dimensions(cardX + cardW - 130, cardY + cardH - 32, 55, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"),
                btn -> {
                    if ("Type".equals(currentTab)) {
                        String cat = CATEGORIES[categoryIndex];
                        String var = getVariants(cat)[variantIndex % getVariants(cat).length];
                        Config.Rule r = new Config.Rule("Type", cat, var, "");
                        r.requiredEnchants = new ArrayList<>(this.requiredEnchants);
                        r.blacklistedEnchants = new ArrayList<>(this.blacklistedEnchants);
                        Config.addRule(slotIndex, r);
                    } else if ("Specific".equals(currentTab)) {
                        Config.Rule r = new Config.Rule("Specific", "", "", chosenItemId);
                        r.mustBeEnchanted = this.mustBeEnchanted;
                        r.requiredEnchants = new ArrayList<>(this.requiredEnchants);
                        Config.addRule(slotIndex, r);
                    }
                    this.client.setScreen(this.parent);
                }
            ).dimensions(cardX + cardW - 70, cardY + cardH - 32, 55, 20).build());
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
            if ("Specific".equals(currentTab)) {
                return "Pick any item in Minecraft and assign enchantment filters.\nItem will be prioritized for this slot regardless of category.";
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
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int cardW = Math.min(420, this.width - 24);
            int cardH = Math.min(230, this.height - 30);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            context.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xD00A0E17);
            context.fill(cardX, cardY, cardX + cardW, cardY + 1, 0x904F6B90);
            context.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0x304F6B90);
            context.fill(cardX, cardY, cardX + 1, cardY + cardH, 0x504F6B90);
            context.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0x504F6B90);

            context.drawText(this.textRenderer, "Add rule", cardX + 16, cardY + 12, 0xFFFFFFFF, false);
            int rulesCount = Config.getRulesForSlot(slotIndex).size();
            context.fill(cardX + cardW - 85, cardY + 8, cardX + cardW - 16, cardY + 22, 0xFF1E293B);
            context.drawText(this.textRenderer, "Priority: " + (rulesCount + 1), cardX + cardW - 77, cardY + 13, 0xFFCBD5E1, false);

            String[] tabs = { "Type", "Specific" };
            int tabW = (cardW - 32) / 2;
            for (int t = 0; t < tabs.length; t++) {
                if (tabs[t].equals(currentTab)) {
                    int tX = cardX + 16 + t * tabW;
                    context.fill(tX, cardY + 44, tX + tabW - 4, cardY + 46, 0xFF10B981);
                }
            }

            if ("Specific".equals(currentTab)) {
                Item item = Registries.ITEM.get(Identifier.of(chosenItemId));
                if (item != null && item != Items.AIR) {
                    context.drawItem(new ItemStack(item), cardX + 24, cardY + 62);
                }
            }

            String[] descLines = getDescription().split("\n");
            int textY = cardY + 116;
            for (String line : descLines) {
                context.drawText(this.textRenderer, line, cardX + 24, textY, 0xFFAAB8C8, false);
                textY += 12;
            }

            super.render(context, mouseX, mouseY, delta);
        }
    }

    // ==========================================================
    // 6. SEARCHABLE ITEM PICKER SCREEN (REAL ITEM SPRITES)
    // ==========================================================
    public static class ItemPickerScreen extends Screen {
        private final AddRuleScreen parent;
        private TextFieldWidget searchBox;
        private final List<Item> allItems = new ArrayList<>();
        private List<Item> filteredItems = new ArrayList<>();
        private int page = 0;
        private static final int ITEMS_PER_PAGE = 36;
        private String currentSearch = "";

        public ItemPickerScreen(AddRuleScreen parent) {
            super(Text.literal("Pick an item"));
            this.parent = parent;
            for (Item item : Registries.ITEM) {
                if (item != Items.AIR) allItems.add(item);
            }
            this.filteredItems = new ArrayList<>(allItems);
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            searchBox = new TextFieldWidget(this.textRenderer, centerX - 100, 25, 200, 18, Text.literal("Search"));
            searchBox.setText(currentSearch);
            searchBox.setChangedListener(this::updateSearch);
            this.addDrawableChild(searchBox);

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("< Prev"),
                btn -> { if (page > 0) { page--; this.clearAndInit(); } }
            ).dimensions(centerX - 100, this.height - 28, 60, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Next >"),
                btn -> { if ((page + 1) * ITEMS_PER_PAGE < filteredItems.size()) { page++; this.clearAndInit(); } }
            ).dimensions(centerX + 40, this.height - 28, 60, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> this.client.setScreen(this.parent)
            ).dimensions(centerX - 30, this.height - 28, 60, 20).build());

            int gridStartX = centerX - 90;
            int gridStartY = 48;
            int startIdx = page * ITEMS_PER_PAGE;

            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = startIdx + (row * 9 + col);
                    if (idx >= filteredItems.size()) break;

                    Item item = filteredItems.get(idx);
                    this.addDrawableChild(ButtonWidget.builder(
                        Text.empty(),
                        btn -> {
                            parent.chosenItemId = Registries.ITEM.getId(item).toString();
                            this.client.setScreen(this.parent);
                        }
                    ).dimensions(gridStartX + col * 20, gridStartY + row * 20, 18, 18).build());
                }
            }
        }

        private void updateSearch(String text) {
            this.currentSearch = text;
            page = 0;
            String query = text.toLowerCase().trim();
            if (query.isEmpty()) {
                filteredItems = new ArrayList<>(allItems);
            } else {
                filteredItems = allItems.stream()
                    .filter(i -> Registries.ITEM.getId(i).getPath().toLowerCase().contains(query))
                    .toList();
            }
            this.clearAndInit();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (searchBox.charTyped(chr, modifiers)) return true;
            return super.charTyped(chr, modifiers);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xD80A0E17);

            int centerX = this.width / 2;
            String title = "Pick an Item (" + filteredItems.size() + " available)";
            context.drawText(this.textRenderer, title, centerX - this.textRenderer.getWidth(title) / 2, 10, 0xFFFFFFFF, false);

            super.render(context, mouseX, mouseY, delta);

            int gridStartX = centerX - 90;
            int gridStartY = 48;
            int startIdx = page * ITEMS_PER_PAGE;

            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = startIdx + (row * 9 + col);
                    if (idx >= filteredItems.size()) break;
                    Item item = filteredItems.get(idx);
                    context.drawItem(new ItemStack(item), gridStartX + col * 20 + 1, gridStartY + row * 20 + 1);
                }
            }
        }
    }

    // ==========================================================
    // 7. ENCHANTMENT PICKER (MAX LEVELS, MULTI-SELECT)
    // ==========================================================
    public static class EnchantPickerScreen extends Screen {
        private final AddRuleScreen parent;
        private TextFieldWidget searchBox;

        public static class EnchantInfo {
            public String id;
            public String cleanName;
            public String maxLevelStr;

            public EnchantInfo(String id, String cleanName, String maxLevelStr) {
                this.id = id;
                this.cleanName = cleanName;
                this.maxLevelStr = maxLevelStr;
            }
        }

        private static final List<EnchantInfo> ENCHANT_DATABASE = List.of(
            new EnchantInfo("sharpness", "Sharpness", "V"),
            new EnchantInfo("smite", "Smite", "V"),
            new EnchantInfo("bane_of_arthropods", "Bane of Arthropods", "V"),
            new EnchantInfo("fire_aspect", "Fire Aspect", "II"),
            new EnchantInfo("looting", "Looting", "III"),
            new EnchantInfo("sweeping_edge", "Sweeping Edge", "III"),
            new EnchantInfo("unbreaking", "Unbreaking", "III"),
            new EnchantInfo("mending", "Mending", "I"),
            new EnchantInfo("efficiency", "Efficiency", "V"),
            new EnchantInfo("silk_touch", "Silk Touch", "I"),
            new EnchantInfo("fortune", "Fortune", "III"),
            new EnchantInfo("power", "Power", "V"),
            new EnchantInfo("punch", "Punch", "II"),
            new EnchantInfo("flame", "Flame", "I"),
            new EnchantInfo("infinity", "Infinity", "I"),
            new EnchantInfo("protection", "Protection", "IV"),
            new EnchantInfo("fire_protection", "Fire Protection", "IV"),
            new EnchantInfo("feather_falling", "Feather Falling", "IV"),
            new EnchantInfo("blast_protection", "Blast Protection", "IV"),
            new EnchantInfo("projectile_protection", "Projectile Protection", "IV"),
            new EnchantInfo("respiration", "Respiration", "III"),
            new EnchantInfo("aqua_affinity", "Aqua Affinity", "I"),
            new EnchantInfo("thorns", "Thorns", "III"),
            new EnchantInfo("depth_strider", "Depth Strider", "III"),
            new EnchantInfo("frost_walker", "Frost Walker", "II"),
            new EnchantInfo("soul_speed", "Soul Speed", "III"),
            new EnchantInfo("swift_sneak", "Swift Sneak", "III"),
            new EnchantInfo("multishot", "Multishot", "I"),
            new EnchantInfo("quick_charge", "Quick Charge", "III"),
            new EnchantInfo("piercing", "Piercing", "IV"),
            new EnchantInfo("density", "Density", "V"),
            new EnchantInfo("breach", "Breach", "IV"),
            new EnchantInfo("wind_burst", "Wind Burst", "III"),
            new EnchantInfo("loyalty", "Loyalty", "III"),
            new EnchantInfo("impaling", "Impaling", "V"),
            new EnchantInfo("riptide", "Riptide", "III"),
            new EnchantInfo("channeling", "Channeling", "I")
        );

        private List<EnchantInfo> filtered = new ArrayList<>(ENCHANT_DATABASE);

        public EnchantPickerScreen(AddRuleScreen parent) {
            super(Text.literal("Pick enchantments"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            searchBox = new TextFieldWidget(this.textRenderer, centerX - 110, 25, 220, 18, Text.literal("Search"));
            searchBox.setChangedListener(this::updateSearch);
            this.addDrawableChild(searchBox);

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Clear All"),
                btn -> {
                    parent.requiredEnchants.clear();
                    parent.blacklistedEnchants.clear();
                    this.clearAndInit();
                }
            ).dimensions(centerX - 110, this.height - 28, 80, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                btn -> this.client.setScreen(this.parent)
            ).dimensions(centerX + 30, this.height - 28, 80, 20).build());

            int startY = 48;
            for (int i = 0; i < Math.min(filtered.size(), 6); i++) {
                EnchantInfo info = filtered.get(i);
                boolean req = parent.requiredEnchants.contains(info.id);
                boolean ban = parent.blacklistedEnchants.contains(info.id);

                String status = req ? "[REQUIRED]" : (ban ? "[BANNED]" : "[OFF]");
                String label = info.cleanName + " (Max: " + info.maxLevelStr + ") " + status;

                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(label),
                    btn -> {
                        if (parent.requiredEnchants.contains(info.id)) {
                            parent.requiredEnchants.remove(info.id);
                            parent.blacklistedEnchants.add(info.id);
                        } else if (parent.blacklistedEnchants.contains(info.id)) {
                            parent.blacklistedEnchants.remove(info.id);
                        } else {
                            parent.requiredEnchants.add(info.id);
                        }
                        this.clearAndInit();
                    }
                ).dimensions(centerX - 110, startY + i * 22, 220, 20).build());
            }
        }

        private void updateSearch(String query) {
            String q = query.toLowerCase().trim();
            if (q.isEmpty()) {
                filtered = new ArrayList<>(ENCHANT_DATABASE);
            } else {
                filtered = ENCHANT_DATABASE.stream()
                    .filter(e -> e.cleanName.toLowerCase().contains(q) || e.id.contains(q))
                    .toList();
            }
            this.clearAndInit();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (searchBox.charTyped(chr, modifiers)) return true;
            return super.charTyped(chr, modifiers);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xD80A0E17);

            int centerX = this.width / 2;
            String title = "Enchantment Filters (Click row: Req -> Ban -> Off)";
            context.drawText(this.textRenderer, title, centerX - this.textRenderer.getWidth(title) / 2, 10, 0xFFFFFFFF, false);

            super.render(context, mouseX, mouseY, delta);
        }
    }
}