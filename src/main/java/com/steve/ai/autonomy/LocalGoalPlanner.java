package com.steve.ai.autonomy;

import com.steve.ai.action.Task;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict local grammar: never interpret only the first clause of an open-ended request. */
public final class LocalGoalPlanner {
    private static final Pattern COMMAND = Pattern.compile(
        "^(craft|make|fazer|faca|faz|fabricar|fabrica|craftar|criar"
            + "|smelt|fundir|funde|derreter|smeltar"
            + "|gather|collect|mine|coletar|colete|recolher|recolha"
            + "|minerar|minere|minar|juntar|pegar|extrair)"
            + "\\s+(?:([0-9]{1,4})\\s+)?(?:(?:o|a|os|as|um|uma)\\s+)?"
            + "([a-z0-9_:/-]+(?: [a-z0-9_-]+){0,4})[.!]?$"
    );
    private static final Pattern BUILD = Pattern.compile(
        "^(build|construir|construa|monte|montar)\\s+"
            + "(?:(?:a|an|uma|um)\\s+)?"
            + "(?:(?:small|pequena|pequeno)\\s+)?"
            + "(house|home|casa|castle|castelo|tower|torre|barn|celeiro|shed|galpao|"
            + "wall|muro|platform|plataforma|hut|cabana)"
            + "(?:\\s+(?:near me|perto de mim|aqui|here))?[.!]?$"
    );
    private static final Map<String, String> STRUCTURES = Map.ofEntries(
        Map.entry("house", "house"), Map.entry("home", "house"), Map.entry("casa", "house"),
        Map.entry("hut", "house"), Map.entry("cabana", "house"),
        Map.entry("castle", "castle"), Map.entry("castelo", "castle"),
        Map.entry("tower", "tower"), Map.entry("torre", "tower"),
        Map.entry("barn", "barn"), Map.entry("celeiro", "barn"),
        Map.entry("shed", "barn"), Map.entry("galpao", "barn"),
        Map.entry("wall", "wall"), Map.entry("muro", "wall"),
        Map.entry("platform", "platform"), Map.entry("plataforma", "platform")
    );
    static final int HOUSE_WIDTH = 7;
    static final int HOUSE_HEIGHT = 4;
    static final int HOUSE_DEPTH = 7;

    private static final Map<String, String> ITEM_ALIASES = Map.ofEntries(
        Map.entry("tronco", "minecraft:oak_log"),
        Map.entry("troncos", "minecraft:oak_log"),
        Map.entry("tronco_de_carvalho", "minecraft:oak_log"),
        Map.entry("troncos_de_carvalho", "minecraft:oak_log"),
        Map.entry("madeira", "minecraft:oak_log"),
        Map.entry("madeiras", "minecraft:oak_log"),
        Map.entry("tabua", "minecraft:oak_planks"),
        Map.entry("tabuas", "minecraft:oak_planks"),
        Map.entry("tabua_de_carvalho", "minecraft:oak_planks"),
        Map.entry("tabuas_de_carvalho", "minecraft:oak_planks"),
        Map.entry("graveto", "minecraft:stick"),
        Map.entry("gravetos", "minecraft:stick"),
        Map.entry("pedregulho", "minecraft:cobblestone"),
        Map.entry("pedregulhos", "minecraft:cobblestone"),
        Map.entry("brita", "minecraft:cobblestone"),
        Map.entry("pedra", "minecraft:stone"),
        Map.entry("pedras", "minecraft:stone"),
        Map.entry("areia", "minecraft:sand"),
        Map.entry("terra", "minecraft:dirt"),
        Map.entry("grama", "minecraft:grass_block"),
        Map.entry("carvao", "minecraft:coal"),
        Map.entry("minerio_de_carvao", "minecraft:coal_ore"),
        Map.entry("minerio_de_ferro", "minecraft:iron_ore"),
        Map.entry("ferro_bruto", "minecraft:raw_iron"),
        Map.entry("lingote_de_ferro", "minecraft:iron_ingot"),
        Map.entry("minerio_de_ouro", "minecraft:gold_ore"),
        Map.entry("lingote_de_ouro", "minecraft:gold_ingot"),
        Map.entry("diamante", "minecraft:diamond"),
        Map.entry("diamantes", "minecraft:diamond"),
        Map.entry("minerio_de_diamante", "minecraft:diamond_ore"),
        Map.entry("cobre", "minecraft:copper_ingot"),
        Map.entry("picareta_de_madeira", "minecraft:wooden_pickaxe"),
        Map.entry("picareta_de_pedra", "minecraft:stone_pickaxe"),
        Map.entry("picareta_de_ferro", "minecraft:iron_pickaxe"),
        Map.entry("machado_de_madeira", "minecraft:wooden_axe"),
        Map.entry("pa_de_madeira", "minecraft:wooden_shovel"),
        Map.entry("espada_de_madeira", "minecraft:wooden_sword"),
        Map.entry("mesa_de_trabalho", "minecraft:crafting_table"),
        Map.entry("mesa_de_craft", "minecraft:crafting_table"),
        Map.entry("bancada", "minecraft:crafting_table"),
        Map.entry("fornalha", "minecraft:furnace"),
        Map.entry("forno", "minecraft:furnace"),
        Map.entry("tocha", "minecraft:torch"),
        Map.entry("tochas", "minecraft:torch"),
        Map.entry("bau", "minecraft:chest"),
        Map.entry("baus", "minecraft:chest"),
        Map.entry("vidro", "minecraft:glass"),
        Map.entry("trigo", "minecraft:wheat"),
        Map.entry("pao", "minecraft:bread")
    );

    public record Request(String action, String item, int quantity) {
        public boolean isBuild() {
            return "build".equals(action);
        }

        public Task task(int inventoryCount) {
            if (isBuild()) {
                return new Task("build", Map.of(
                    "structure", item,
                    "material", "oak_planks",
                    "width", HOUSE_WIDTH,
                    "height", HOUSE_HEIGHT,
                    "depth", HOUSE_DEPTH));
            }
            int missing = Math.max(0, quantity - Math.max(0, inventoryCount));
            return new Task(action, Map.of("gather".equals(action) ? "resource" : "item",
                item, "quantity", missing));
        }
    }

    /** One command or exactly two independently valid commands; no open-ended tails. */
    public Optional<java.util.List<Request>> parseSequence(String description, Predicate<String> registeredItem) {
        if (description == null || description.length() > 256) return Optional.empty();
        String[] clauses = normalize(description).split("\\s+(?:e|and)\\s+", -1);
        if (clauses.length < 1 || clauses.length > 2) return Optional.empty();
        java.util.List<Request> requests = new java.util.ArrayList<>();
        for (String clause : clauses) {
            var request = parse(clause, registeredItem);
            if (request.isEmpty()) return Optional.empty();
            requests.add(request.get());
        }
        return Optional.of(java.util.List.copyOf(requests));
    }

    public Optional<Request> parse(String description, Predicate<String> registeredItem) {
        if (description == null || description.length() > 256) return Optional.empty();
        String normalized = normalize(description);
        if (normalized.isEmpty()) return Optional.empty();
        Matcher build = BUILD.matcher(normalized);
        if (build.matches()) {
            String structure = STRUCTURES.get(build.group(2));
            if (structure == null) return Optional.empty();
            return Optional.of(new Request("build", structure, 1));
        }
        Matcher matcher = COMMAND.matcher(normalized);
        if (!matcher.matches()) return Optional.empty();
        int quantity = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
        if (quantity < 1 || quantity > 2048) return Optional.empty();
        String action = switch (matcher.group(1)) {
            case "craft", "make", "fazer", "faca", "faz", "fabricar", "fabrica", "craftar", "criar" -> "craft";
            case "smelt", "fundir", "funde", "derreter", "smeltar" -> "smelt";
            default -> "gather";
        };
        String item = resolveItem(matcher.group(3).replace(' ', '_'), action, registeredItem);
        if (item == null) return Optional.empty();
        return Optional.of(new Request(action, item, quantity));
    }

    static String normalize(String raw) {
        String stripped = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return stripped.toLowerCase(Locale.ROOT);
    }

    private static String resolveItem(String token, String action, Predicate<String> registeredItem) {
        String key = token.startsWith("minecraft:") ? token.substring("minecraft:".length()) : token;
        String aliased = actionSpecificAlias(key, action);
        if (aliased == null) {
            aliased = ITEM_ALIASES.get(key);
        }
        String[] candidates = aliased == null
            ? new String[] {token, token.contains(":") ? token : "minecraft:" + token}
            : new String[] {aliased};
        for (String candidate : candidates) {
            if (registeredItem.test(candidate)) return canonicalItem(candidate);
            if (candidate.endsWith("s")) {
                String singular = candidate.substring(0, candidate.length() - 1);
                if (registeredItem.test(singular)) return canonicalItem(singular);
            }
        }
        return null;
    }

    /** Namespace-qualifies a resolved id so every consumer (recipe lookups, constraint equality
     * gates) receives a canonical {@code minecraft:...} form. {@code ResourceLocation.tryParse}
     * defaults a bare id to the minecraft namespace, so a bare match would otherwise leak out of
     * {@link #resolveItem} and break local-plan gating. */
    private static String canonicalItem(String id) {
        return id == null || id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    private static String actionSpecificAlias(String key, String action) {
        return switch (key) {
            case "ferro", "iron" -> "smelt".equals(action) || "craft".equals(action)
                ? "minecraft:iron_ingot" : "minecraft:iron_ore";
            case "ouro", "gold" -> "smelt".equals(action) || "craft".equals(action)
                ? "minecraft:gold_ingot" : "minecraft:gold_ore";
            default -> null;
        };
    }
}
