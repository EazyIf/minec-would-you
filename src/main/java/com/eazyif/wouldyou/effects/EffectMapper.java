package com.eazyif.wouldyou.effects;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.LightningEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translates the free-text effect description from the AI into concrete
 * gameplay actions. Multiple keywords can be matched in a single string
 * (e.g. "speed boost but slowness").
 */
public final class EffectMapper {

    private static final int LONG_DURATION = 20 * 60 * 5; // 5 minutes
    private static final Identifier MAX_HEALTH_MOD =
            Identifier.of("wouldyou", "wyr_max_health");

    private EffectMapper() {}

    /**
     * Apply effects derived from {@code text} to {@code player}. Returns a
     * human-readable summary of what was applied.
     */
    public static String apply(ServerPlayerEntity player, String text) {
        if (text == null || text.isBlank()) return "(nothing)";
        String s = text.toLowerCase(Locale.ROOT);
        ServerWorld world = player.getServerWorld();
        List<String> applied = new ArrayList<>();

        // Status effects
        if (containsAny(s, "speed boost", "speed")) {
            give(player, StatusEffects.SPEED, 1, applied, "Speed II");
        }
        if (containsAny(s, "jump boost", "high jump", "jump")) {
            give(player, StatusEffects.JUMP_BOOST, 1, applied, "Jump Boost II");
        }
        if (containsAny(s, "night vision")) {
            give(player, StatusEffects.NIGHT_VISION, 0, applied, "Night Vision");
        }
        if (containsAny(s, "regeneration", "regen")) {
            give(player, StatusEffects.REGENERATION, 0, applied, "Regeneration");
        }
        if (containsAny(s, "strength")) {
            give(player, StatusEffects.STRENGTH, 1, applied, "Strength II");
        }
        if (containsAny(s, "resistance")) {
            give(player, StatusEffects.RESISTANCE, 0, applied, "Resistance");
        }
        if (containsAny(s, "fire resistance", "immune to fire")) {
            give(player, StatusEffects.FIRE_RESISTANCE, 0, applied, "Fire Resistance");
        }
        if (containsAny(s, "water breathing")) {
            give(player, StatusEffects.WATER_BREATHING, 0, applied, "Water Breathing");
        }
        if (containsAny(s, "haste")) {
            give(player, StatusEffects.HASTE, 1, applied, "Haste II");
        }
        if (containsAny(s, "luck")) {
            give(player, StatusEffects.LUCK, 0, applied, "Luck");
        }
        if (containsAny(s, "glowing", "glow")) {
            give(player, StatusEffects.GLOWING, 0, applied, "Glowing");
        }

        // Negative effects
        if (containsAny(s, "slowness", "slow")) {
            give(player, StatusEffects.SLOWNESS, 1, applied, "Slowness II");
        }
        if (containsAny(s, "weakness")) {
            give(player, StatusEffects.WEAKNESS, 0, applied, "Weakness");
        }
        if (containsAny(s, "hunger")) {
            give(player, StatusEffects.HUNGER, 0, applied, "Hunger");
        }
        if (containsAny(s, "blindness", "blind")) {
            give(player, StatusEffects.BLINDNESS, 0, applied, "Blindness");
        }
        if (containsAny(s, "poison")) {
            give(player, StatusEffects.POISON, 0, applied, "Poison");
        }
        if (containsAny(s, "wither effect", "withering")) {
            give(player, StatusEffects.WITHER, 0, applied, "Wither");
        }
        if (containsAny(s, "mining fatigue")) {
            give(player, StatusEffects.MINING_FATIGUE, 1, applied, "Mining Fatigue II");
        }
        if (containsAny(s, "nausea")) {
            give(player, StatusEffects.NAUSEA, 0, applied, "Nausea");
        }
        if (containsAny(s, "levitation")) {
            give(player, StatusEffects.LEVITATION, 0, applied, "Levitation");
        }

        // Items / rewards
        int diamonds = pickQuantity(s, "diamond");
        if (diamonds > 0) {
            grant(player, new ItemStack(Items.DIAMOND, diamonds));
            applied.add(diamonds + "x Diamond");
        }
        int emeralds = pickQuantity(s, "emerald");
        if (emeralds > 0) {
            grant(player, new ItemStack(Items.EMERALD, emeralds));
            applied.add(emeralds + "x Emerald");
        }
        int gold = pickQuantity(s, "gold");
        if (gold > 0) {
            grant(player, new ItemStack(Items.GOLD_INGOT, gold));
            applied.add(gold + "x Gold");
        }
        int iron = pickQuantity(s, "iron");
        if (iron > 0) {
            grant(player, new ItemStack(Items.IRON_INGOT, iron));
            applied.add(iron + "x Iron");
        }
        int arrows = pickQuantity(s, "arrow");
        if (arrows > 0) {
            grant(player, new ItemStack(Items.ARROW, arrows));
            applied.add(arrows + "x Arrow");
        }
        if (containsAny(s, "food", "cooked beef", "steak")) {
            grant(player, new ItemStack(Items.COOKED_BEEF, 16));
            applied.add("16x Cooked Beef");
        }

        // Mob spawns
        int creepers = pickQuantity(s, "creeper");
        if (creepers > 0) {
            spawnNearby(world, player, EntityType.CREEPER, creepers);
            applied.add(creepers + " creepers spawned");
        }
        int zombies = pickQuantity(s, "zombie");
        if (zombies > 0) {
            spawnNearby(world, player, EntityType.ZOMBIE, zombies);
            applied.add(zombies + " zombies spawned");
        }
        int skeletons = pickQuantity(s, "skeleton");
        if (skeletons > 0) {
            spawnNearby(world, player, EntityType.SKELETON, skeletons);
            applied.add(skeletons + " skeletons spawned");
        }

        // Misc world effects
        if (containsAny(s, "lightning")) {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(player.getX(), player.getY(), player.getZ());
                world.spawnEntity(bolt);
                applied.add("Lightning struck");
            }
        }
        if (containsAny(s, "set on fire", "on fire", "ignite")) {
            player.setOnFireFor(8);
            applied.add("Set on fire");
        }
        if (containsAny(s, "extinguish")) {
            player.extinguish();
            applied.add("Extinguished");
        }

        // Max health changes
        if (containsAny(s, "less health", "lower max health", "reduce max health")) {
            modifyMaxHealth(player, -4.0);
            applied.add("Max health -4");
        }
        if (containsAny(s, "more health", "higher max health", "extra hearts")) {
            modifyMaxHealth(player, 4.0);
            applied.add("Max health +4");
        }
        if (containsAny(s, "heal fully", "full heal")) {
            player.setHealth(player.getMaxHealth());
            applied.add("Healed");
        }

        return applied.isEmpty() ? "(no recognized effects)" : String.join(", ", applied);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    private static void give(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect,
                             int amplifier, List<String> log, String label) {
        p.addStatusEffect(new StatusEffectInstance(effect, LONG_DURATION, amplifier, false, true, true));
        log.add(label);
    }

    private static void grant(ServerPlayerEntity p, ItemStack stack) {
        if (!p.getInventory().insertStack(stack)) {
            p.dropItem(stack, false);
        }
    }

    private static <T extends LivingEntity> void spawnNearby(ServerWorld world, ServerPlayerEntity player,
                                                             EntityType<T> type, int count) {
        for (int i = 0; i < count; i++) {
            T entity = type.create(world);
            if (entity == null) continue;
            double angle = (Math.PI * 2 * i) / Math.max(count, 1);
            double dx = Math.cos(angle) * 4.0;
            double dz = Math.sin(angle) * 4.0;
            entity.refreshPositionAndAngles(player.getX() + dx, player.getY(), player.getZ() + dz,
                    world.random.nextFloat() * 360f, 0f);
            world.spawnEntity(entity);
        }
    }

    /**
     * Apply a persistent additive modifier to the player's max health attribute.
     * Repeated application of the same delta is idempotent (same modifier id).
     */
    private static void modifyMaxHealth(ServerPlayerEntity player, double delta) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        Identifier id = delta < 0
                ? Identifier.of("wouldyou", "wyr_max_health_neg")
                : Identifier.of("wouldyou", "wyr_max_health_pos");
        attr.removeModifier(id);
        attr.addPersistentModifier(new EntityAttributeModifier(
                id, delta, EntityAttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Look for a number directly preceding a keyword (e.g. "5 diamonds").
     * If the keyword is mentioned without a number, returns a small default.
     * Returns 0 if the keyword isn't mentioned at all.
     */
    private static int pickQuantity(String text, String keyword) {
        int idx = text.indexOf(keyword);
        if (idx < 0) return 0;
        // Walk backward over whitespace then digits
        int i = idx - 1;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
        int end = i + 1;
        while (i >= 0 && Character.isDigit(text.charAt(i))) i--;
        int start = i + 1;
        if (start < end) {
            try {
                int n = Integer.parseInt(text.substring(start, end));
                return Math.max(1, Math.min(64, n));
            } catch (NumberFormatException ignored) {}
        }
        return 4; // default count when keyword present but no number
    }

    public static Text describe(String summary) {
        return Text.literal("[WYR] " + summary);
    }
}
