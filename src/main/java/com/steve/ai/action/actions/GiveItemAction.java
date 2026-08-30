package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gives items from the Steve inventory to the owning player or a specified player.
 */
public class GiveItemAction extends BaseAction {
    private String itemName;
    private int quantity;
    private String playerName;
    private int ticksRunning;
    private static final int MAX_TICKS = 200;

    public GiveItemAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        itemName = task.getStringParameter("item");
        quantity = task.getIntParameter("quantity", 1);
        playerName = task.getStringParameter("player", null);
        ticksRunning = 0;

        if (itemName == null || itemName.isBlank()) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Missing item parameter").build();
            return;
        }
    }

    @Override
    protected void onTick() {
        ticksRunning++;
        if (ticksRunning > MAX_TICKS) {
            result = ActionResult.failure(ActionResult.ERROR_TIMEOUT, "Give timeout").build();
            return;
        }

        Player targetPlayer = findPlayer();
        if (targetPlayer == null) {
            if (ticksRunning % 20 == 0) {
                steve.sendChatMessage("I can't find player '" + playerName + "' to give items to.");
            }
            return;
        }

        if (!steve.blockPosition().closerThan(targetPlayer.blockPosition(), 4.0)) {
            steve.getNavigation().moveTo(targetPlayer, 1.0);
            return;
        }

        steve.getNavigation().stop();

        Item targetItem = parseItem(itemName);
        if (targetItem == Items.AIR) {
            result = ActionResult.failure(ActionResult.ERROR_VALIDATION, "Unknown item: " + itemName).build();
            return;
        }

        int available = steve.getSteveInventory().count(targetItem);
        if (available < quantity) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE,
                "Need " + quantity + " " + itemName + " but only have " + available)
                .retryable(true)
                .observation("missing_item", itemName)
                .observation("missing_quantity", Math.max(1, quantity - available))
                .build();
            return;
        }

        ItemStack given = steve.getSteveInventory().withdraw(targetItem, quantity);

        if (given.isEmpty()) {
            result = ActionResult.failure(ActionResult.ERROR_RESOURCE, "Could not withdraw " + itemName).build();
            return;
        }

        if (!(targetPlayer instanceof ServerPlayer serverPlayer)) {
            steve.getSteveInventory().insert(given);
            result = ActionResult.failure(ActionResult.ERROR_PLAYER_OFFLINE,
                "Target is not an active server player").retryable(true).build();
            return;
        }
        int deliveredCount = given.getCount();
        if (!serverPlayer.getInventory().add(given) && !given.isEmpty()) {
            targetPlayer.drop(given, false);
        }

        String msg = "Gave " + deliveredCount + " " + itemName
            + " to " + targetPlayer.getName().getString();
        result = ActionResult.success(msg)
            .observation("delivered", true)
            .observation("deliveredItem", itemName)
            .observation("deliveredQuantity", deliveredCount)
            .observation("recipientUuid", targetPlayer.getUUID().toString())
            .build();
    }

    @Override
    protected void onCancel() {
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Give " + quantity + " " + itemName + " to " + (playerName != null ? playerName : "owner");
    }

    private Player findPlayer() {
        if (playerName == null || playerName.isBlank() || playerName.equalsIgnoreCase("owner")
            || playerName.equalsIgnoreCase("me")) {
            return steve.getPreferredPlayer();
        }
        for (Player player : steve.level().players()) {
            if (player.getName().getString().equalsIgnoreCase(playerName)) {
                return player;
            }
        }
        return null;
    }

    private Item parseItem(String name) {
        if (name == null || name.isBlank()) return Items.AIR;
        name = name.toLowerCase().replace(" ", "_");
        if (!name.contains(":")) {
            name = "minecraft:" + name;
        }
        ResourceLocation rl = ResourceLocation.tryParse(name);
        return rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
    }
}
