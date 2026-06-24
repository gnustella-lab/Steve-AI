package com.steve.ai.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Centralized configuration for Steve AI.
 *
 * <p>Provides separate API key, model, and parameter fields for each supported
 * LLM provider (OpenAI, Groq, Gemini). Falls back to environment variables
 * when config values are empty.</p>
 *
 * <p><b>Environment Variable Overrides:</b></p>
 * <ul>
 *   <li>{@code STEVE_OPENAI_API_KEY} → openai.apiKey</li>
 *   <li>{@code STEVE_GROQ_API_KEY}   → groq.apiKey</li>
 *   <li>{@code STEVE_GEMINI_API_KEY} → gemini.apiKey</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class SteveConfig {
    public static final ForgeConfigSpec SPEC;

    // ── Provider selection ──────────────────────────────────────────
    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER;

    // ── OpenAI ──────────────────────────────────────────────────────
    public static final ForgeConfigSpec.ConfigValue<String> OPENAI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> OPENAI_MODEL;

    // ── Groq ────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_MODEL;

    // ── Gemini ──────────────────────────────────────────────────────
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_MODEL;

    // ── Shared parameters ───────────────────────────────────────────
    public static final ForgeConfigSpec.IntValue MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue TEMPERATURE;

    // ── Behavior ────────────────────────────────────────────────────
    public static final ForgeConfigSpec.IntValue ACTION_TICK_DELAY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHAT_RESPONSES;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_STEVES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // ── AI provider selection ───────────────────────────────────
        builder.comment("AI Provider Selection").push("ai");

        AI_PROVIDER = builder
            .comment("AI provider to use: 'groq' (FASTEST), 'openai', or 'gemini'")
            .define("provider", "groq");

        MAX_TOKENS = builder
            .comment("Maximum tokens per API request (shared across providers)")
            .defineInRange("maxTokens", 8000, 100, 65536);

        TEMPERATURE = builder
            .comment("Temperature for AI responses (0.0-2.0, lower is more deterministic)")
            .defineInRange("temperature", 0.7, 0.0, 2.0);

        builder.pop();

        // ── OpenAI section ──────────────────────────────────────────
        builder.comment("OpenAI API Configuration").push("openai");

        OPENAI_API_KEY = builder
            .comment("Your OpenAI API key. Can also be set via STEVE_OPENAI_API_KEY environment variable.")
            .define("apiKey", "");

        OPENAI_MODEL = builder
            .comment("OpenAI model to use (gpt-4o, gpt-4-turbo, gpt-3.5-turbo)")
            .define("model", "gpt-4o");

        builder.pop();

        // ── Groq section ────────────────────────────────────────────
        builder.comment("Groq API Configuration").push("groq");

        GROQ_API_KEY = builder
            .comment("Your Groq API key. Can also be set via STEVE_GROQ_API_KEY environment variable.")
            .define("apiKey", "");

        GROQ_MODEL = builder
            .comment("Groq model to use (llama-3.1-8b-instant, llama-3.1-70b-versatile, mixtral-8x7b-32768)")
            .define("model", "llama-3.1-8b-instant");

        builder.pop();

        // ── Gemini section ──────────────────────────────────────────
        builder.comment("Google Gemini API Configuration").push("gemini");

        GEMINI_API_KEY = builder
            .comment("Your Gemini API key. Can also be set via STEVE_GEMINI_API_KEY environment variable.")
            .define("apiKey", "");

        GEMINI_MODEL = builder
            .comment("Gemini model to use (gemini-1.5-flash, gemini-1.5-pro, gemini-2.0-flash-exp)")
            .define("model", "gemini-1.5-flash");

        builder.pop();

        // ── Behavior section ────────────────────────────────────────
        builder.comment("Steve Behavior Configuration").push("behavior");

        ACTION_TICK_DELAY = builder
            .comment("Ticks between action checks (20 ticks = 1 second)")
            .defineInRange("actionTickDelay", 20, 1, 100);

        ENABLE_CHAT_RESPONSES = builder
            .comment("Allow Steves to respond in chat")
            .define("enableChatResponses", true);

        MAX_ACTIVE_STEVES = builder
            .comment("Maximum number of Steves that can be active simultaneously")
            .defineInRange("maxActiveSteves", 10, 1, 50);

        builder.pop();

        SPEC = builder.build();
    }

    // ── Environment variable helpers ─────────────────────────────────

    /**
     * Resolves the effective API key for a provider, checking config first
     * then falling back to environment variables.
     *
     * @param configValue  The config value (may be empty)
     * @param envVarName   Environment variable name to check as fallback
     * @return The resolved API key, or empty string if not set anywhere
     */
    public static String resolveApiKey(String configValue, String envVarName) {
        if (configValue != null && !configValue.isBlank()) {
            return configValue.trim();
        }
        String envValue = System.getenv(envVarName);
        return (envValue != null && !envValue.isBlank()) ? envValue.trim() : "";
    }

    /**
     * Returns the effective OpenAI API key (config → env var fallback).
     */
    public static String getOpenAIApiKey() {
        return resolveApiKey(OPENAI_API_KEY.get(), "STEVE_OPENAI_API_KEY");
    }

    /**
     * Returns the effective Groq API key (config → env var fallback).
     */
    public static String getGroqApiKey() {
        return resolveApiKey(GROQ_API_KEY.get(), "STEVE_GROQ_API_KEY");
    }

    /**
     * Returns the effective Gemini API key (config → env var fallback).
     */
    public static String getGeminiApiKey() {
        return resolveApiKey(GEMINI_API_KEY.get(), "STEVE_GEMINI_API_KEY");
    }
}

