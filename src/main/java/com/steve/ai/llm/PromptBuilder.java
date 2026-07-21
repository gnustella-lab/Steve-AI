package com.steve.ai.llm;

import com.steve.ai.entity.SteveEntity;
import com.steve.ai.memory.WorldKnowledge;
import com.steve.ai.plugin.ActionDescriptor;
import com.steve.ai.plugin.ActionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PromptBuilder {
    
    public static String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder("""
            You are a Minecraft AI agent. Respond ONLY with one valid JSON object and no extra text.

            FORMAT (strict JSON):
            {"summary":"short operational summary","tasks":[{"action":"registered action","parameters":{}}]}

            RULES:
            1. Use only registered actions and parameters declared by their schemas.
            2. Do not emit Java, commands, scripts, file paths, reflection targets or private chain-of-thought.
            3. Keep summary under 160 characters and tasks in the order they should execute.
            4. Do not add movement tasks unless the objective requires them.

            REGISTERED ACTIONS:
            """);
        for (ActionDescriptor descriptor : ActionRegistry.getInstance().getPlannableDescriptors()) {
            prompt.append("- ").append(descriptor.name()).append(": ")
                .append(descriptor.description()).append("\n  parameters: ")
                .append(descriptor.parameterSchema().toJson()).append('\n');
            for (String example : descriptor.examples()) {
                prompt.append("  example: ").append(example).append('\n');
            }
        }
        prompt.append("CRITICAL: Output only JSON. No Markdown fences or explanations.");
        return prompt.toString();
    }

    public static String buildUserPrompt(SteveEntity steve, String command, WorldKnowledge worldKnowledge) {
        StringBuilder prompt = new StringBuilder();
        
        // Give agents FULL situational awareness
        prompt.append("=== YOUR SITUATION ===\n");
        prompt.append("Position: ").append(formatPosition(steve.blockPosition())).append("\n");
        prompt.append("Nearby Players: ").append(worldKnowledge.getNearbyPlayerNames()).append("\n");
        prompt.append("Nearby Entities: ").append(worldKnowledge.getNearbyEntitiesSummary()).append("\n");
        prompt.append("Nearby Blocks: ").append(worldKnowledge.getNearbyBlocksSummary()).append("\n");
        prompt.append("Biome: ").append(worldKnowledge.getBiomeName()).append("\n");
        prompt.append("Inventory:\n").append(formatInventory(steve)).append("\n");
        prompt.append("Armor: ").append(formatArmor(steve)).append("\n");
        prompt.append("Main hand: ").append(formatStack(steve.getMainHandItem())).append("\n");
        prompt.append("Off hand: ").append(formatStack(steve.getOffhandItem())).append("\n");
        
        prompt.append("\n=== PLAYER COMMAND ===\n");
        prompt.append("\"").append(command).append("\"\n");
        
        prompt.append("\n=== YOUR JSON RESPONSE ===\n");
        
        return prompt.toString();
    }

    private static String formatPosition(BlockPos pos) {
        return String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String formatInventory(SteveEntity steve) {
        return steve.getSteveInventory().summarize(20);
    }

    private static String formatArmor(SteveEntity steve) {
        List<String> armor = new ArrayList<>();
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = steve.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                armor.add(formatStack(stack));
            }
        }
        return armor.isEmpty() ? "empty" : String.join(", ", armor);
    }

    private static String formatStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String displayId = "minecraft".equals(itemId.getNamespace()) ? itemId.getPath() : itemId.toString();
        if (!stack.isDamageableItem()) {
            return displayId + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
        }
        int durability = Math.max(0, Math.round(100.0f * (stack.getMaxDamage() - stack.getDamageValue())
            / stack.getMaxDamage()));
        return displayId + ", durability " + durability + "%";
    }
}

