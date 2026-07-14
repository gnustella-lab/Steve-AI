package com.steve.ai.action;

import com.steve.ai.action.actions.BuildStructureAction;
import com.steve.ai.action.actions.CombatAction;
import com.steve.ai.action.actions.FollowPlayerAction;
import com.steve.ai.action.actions.GatherResourceAction;
import com.steve.ai.action.actions.MineBlockAction;
import com.steve.ai.action.actions.PathfindAction;
import com.steve.ai.action.actions.PlaceBlockAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionDescriptionTest {

    @Test
    void descriptionsAreAvailableBeforeActionsStart() {
        MineBlockAction mine = new MineBlockAction(null, new Task("mine", Map.of(
            "block", "diamond_ore",
            "quantity", 3
        )));
        PlaceBlockAction place = new PlaceBlockAction(null, new Task("place", Map.of(
            "block", "oak_planks",
            "x", 1,
            "y", 64,
            "z", 2
        )));

        String mineDescription = assertDoesNotThrow(mine::getDescription);
        String placeDescription = assertDoesNotThrow(place::getDescription);
        assertTrue(mineDescription.contains("diamond_ore"));
        assertTrue(placeDescription.contains("oak_planks"));
    }

    @Test
    void preStartDescriptionsContainRequestedParameters() {
        BuildStructureAction build = new BuildStructureAction(null, new Task("build", Map.of(
            "structure", "house",
            "blocks", java.util.List.of("oak_planks"),
            "dimensions", java.util.List.of(9, 6, 9)
        )));
        CombatAction attack = new CombatAction(null,
            new Task("attack", Map.of("target", "zombie")));
        FollowPlayerAction follow = new FollowPlayerAction(null,
            new Task("follow", Map.of("player", "Alex")));
        GatherResourceAction gather = new GatherResourceAction(null, new Task("gather", Map.of(
            "resource", "coal",
            "quantity", 4
        )));
        PathfindAction pathfind = new PathfindAction(null, new Task("pathfind", Map.of(
            "x", 12,
            "y", 70,
            "z", -8
        )));

        assertTrue(build.getDescription().contains("house"));
        assertTrue(attack.getDescription().contains("zombie"));
        assertTrue(follow.getDescription().contains("Alex"));
        assertTrue(gather.getDescription().contains("4 coal"));
        assertTrue(pathfind.getDescription().contains("12, 70, -8"));
    }
}
