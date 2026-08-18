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

/** Builds stable, bounded prompts from registry metadata and immutable planning context. */
public final class PromptBuilder {
    private static final int MAX_SYSTEM_PROMPT_LENGTH = 24_000;
    private static final int MAX_PLANNING_PROMPT_LENGTH = 16_000;

    private PromptBuilder() {
    }

    public static String buildSystemPrompt() {
        return buildSystemPrompt(8);
    }

    public static String buildSystemPrompt(int maxHorizon) {
        StringBuilder prompt = new StringBuilder("""
            You are Steve, a safe Minecraft server agent. Return exactly one JSON object and no extra text.

            AGENT CONTRACT:
            - Pursue the PRIMARY GOAL, not a disconnected task.
            - Use only registered actions and their exact parameter schemas.
            - Treat observations as stale after an action and plan only a short executable horizon.
            - Never emit private chain-of-thought, Java, commands, scripts, reflection, shell operations, or credentials.
            - If a route or resource fails, choose a different bounded strategy instead of repeating it.

            OUTPUT SCHEMA:
            {"decision":"act|complete|blocked|ask_user","summary":"short operational update",
             "goalStatus":"in_progress|complete|blocked|paused|failed",
             "tasks":[{"action":"registered_action","parameters":{}}]}

            OUTPUT RULES:
            - decision=act requires 1 to the requested horizon executable tasks.
            - decision=complete is only a recommendation; the server verifies deterministic goals.
            - decision=blocked means no safe bounded strategy is known.
            - decision=ask_user is reserved for genuinely missing human information.
            - Keep summary under 160 characters and never include hidden reasoning.
            - Do not invent actions or parameters.

            REGISTERED ACTIONS:
            """);
        for (ActionDescriptor descriptor : ActionRegistry.getInstance().getPlannableDescriptors()) {
            if (prompt.length() >= MAX_SYSTEM_PROMPT_LENGTH - 1_000) break;
            prompt.append("- ").append(descriptor.name()).append(": ")
                .append(descriptor.description()).append("\n  parameters: ")
                .append(descriptor.parameterSchema().toJson()).append('\n');
            for (String example : descriptor.examples()) {
                if (prompt.length() >= MAX_SYSTEM_PROMPT_LENGTH - 512) break;
                prompt.append("  example: ").append(example).append('\n');
            }
        }
        prompt.append("MAX PLAN HORIZON: ").append(Math.max(1, Math.min(maxHorizon, 16))).append('\n');
        prompt.append("Return only the JSON object.");
        return bounded(prompt.toString(), MAX_SYSTEM_PROMPT_LENGTH);
    }

    /** Builds the autonomous receding-horizon context with stable section labels. */
    public static String buildPlanningPrompt(PlanningContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("=== PRIMARY GOAL ===\n");
        prompt.append(context.getPrimaryGoal() == null ? "none" : context.getPrimaryGoal().getDescription()).append('\n');
        prompt.append("=== ACTIVE SUBGOAL ===\n");
        prompt.append(context.getActiveSubgoal() == null ? "none" : context.getActiveSubgoal().getDescription()).append('\n');
        prompt.append("=== CURRENT OBSERVATION ===\n");
        prompt.append(context.getObservation() == null ? "unavailable" : context.getObservation().toPromptContext()).append('\n');
        prompt.append("=== RELEVANT MEMORY ===\n");
        appendList(prompt, context.getRelevantMemory(), "none");
        prompt.append("=== RECENT COMPLETED STEPS ===\n");
        appendList(prompt, context.getRecentCompletedSteps(), "none");
        prompt.append("=== LAST ACTION RESULT ===\n");
        prompt.append(context.getLastActionResult().isBlank() ? "none" : context.getLastActionResult()).append('\n');
        prompt.append("=== FAILED APPROACHES ===\n");
        appendList(prompt, context.getFailedApproaches(), "none");
        prompt.append("=== AVAILABLE ACTION SCHEMAS ===\n");
        prompt.append("See REGISTERED ACTIONS in the system contract.\n");
        prompt.append("=== CURRENT AUTONOMY POLICY ===\n");
        prompt.append("server-authorized, bounded, no teleport exploration, no permission bypass\n");
        prompt.append("=== REMAINING BUDGET ===\n");
        prompt.append("horizon=").append(context.getMaxPlanHorizon())
            .append(", llmCalls=").append(context.getRemainingLlmCalls())
            .append(", replans=").append(context.getRemainingReplans()).append('\n');
        prompt.append("=== DECISION ===\nReturn the JSON object required by the system contract.");
        return bounded(prompt.toString(), MAX_PLANNING_PROMPT_LENGTH);
    }

    /** Compatibility prompt used by the legacy ActionExecutor/OFF mode. */
    public static String buildUserPrompt(SteveEntity steve, String command, WorldKnowledge worldKnowledge) {
        StringBuilder prompt = new StringBuilder();
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
        prompt.append('"').append(bounded(command, 512)).append("\"\n");
        prompt.append("\n=== YOUR JSON RESPONSE ===\n");
        return bounded(prompt.toString(), MAX_PLANNING_PROMPT_LENGTH);
    }

    private static void appendList(StringBuilder prompt, List<String> values, String empty) {
        if (values == null || values.isEmpty()) {
            prompt.append(empty).append('\n');
            return;
        }
        for (String value : values) prompt.append("- ").append(bounded(value, 512)).append('\n');
    }

    private static String formatPosition(BlockPos pos) {
        return String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String formatInventory(SteveEntity steve) {
        return steve.getSteveInventory().summarize(20);
    }

    private static String formatArmor(SteveEntity steve) {
        List<String> armor = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = steve.getItemBySlot(slot);
            if (!stack.isEmpty()) armor.add(formatStack(stack));
        }
        return armor.isEmpty() ? "empty" : String.join(", ", armor);
    }

    private static String formatStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String displayId = "minecraft".equals(itemId.getNamespace()) ? itemId.getPath() : itemId.toString();
        if (!stack.isDamageableItem()) return displayId + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
        int durability = Math.max(0, Math.round(100.0f * (stack.getMaxDamage() - stack.getDamageValue())
            / stack.getMaxDamage()));
        return displayId + ", durability " + durability + "%";
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
