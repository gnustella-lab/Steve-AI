# Steve AI - Autonomous AI Agent for Minecraft

We built Cursor for Minecraft. Instead of AI that helps you write code, you get AI agents that actually play the game with you.

This repository is a maintained fork of [YuvDwi/Steve](https://github.com/YuvDwi/Steve), focused on production hardening, safer agent execution, automated testing, reproducible releases, and ongoing open-source maintenance.

https://github.com/user-attachments/assets/23f0ccdd-7a7a-4d49-9dd9-215ebf67265a

## What It Does

Steve acts as an Agent, or a series of Agents if you choose to employ all of them. You describe what you want, and he understands the context and executes. Same concept here, except instead of code editing, you get embodied Steves that operate in your Minecraft world.

The interface is simple: press K to open a panel, type what you need. The agents handle the interpretation, planning, and execution. Say "mine some iron" and the agent reasons about where iron spawns, navigates to the appropriate depth, locates ore veins, and extracts the resources. Ask for a house and it considers the available materials, generates an appropriate structure, and builds it block by block.

What makes this interesting is the multi-agent coordination. When multiple Steves work on the same task, they don't just independently execute, they actively coordinate to avoid conflicts and optimize workload distribution. Tell three agents to build a castle and they'll automatically partition the structure, divide sections among themselves, and parallelize the construction.

The agents aren't following predefined scripts. They're operating off natural language instructions, which means:
- **Resource extraction** where agents determine optimal mining locations and strategies
- **Autonomous building** with agents planning layouts and material usage
- **Combat and defense** where agents assess threats and coordinate responses
- **Exploration and gathering** with pathfinding and resource location
- **Collaborative execution** with automatic workload balancing and conflict resolution

## Quick Start

**You need:**
- Minecraft 1.20.1 with Forge
- Java 17
- An OpenAI API key (or Groq/Gemini if you prefer)

**Installation:**
1. Download the JAR from releases
2. Put it in your `mods` folder
3. Launch Minecraft
4. Copy `config/steve-common.toml.example` to `config/steve-common.toml`
5. Add your API key to the config

**Config example:**
```toml
[openai]
apiKey = "your-api-key-here"
model = "gpt-3.5-turbo"
maxTokens = 1000
temperature = 0.7
```

Then spawn a Steve with `/steve spawn Bob` and press K to start giving commands.

## Usage Examples

```
"mine 20 iron ore"
"build a house near me"
"help Alex with the tower"
"defend me from zombies"
"follow me"
"gather wood from that forest"
"make a cobblestone platform here"
"attack that creeper"
```

The agents are pretty good at figuring out what you mean. You don't need to be super specific.

## Technical Architecture

### System Overview

Each Steve has a persistent, bounded executive. A user command becomes an `AgentGoal`, not a one-shot string. The executive observes the server world, requests a small planning horizon asynchronously, delegates tick-based actions to `ActionExecutor`, evaluates the result, and recovers or replans without requiring another command.

**Core execution flow:**
1. User input captured through `/steve goal` or the GUI
2. `AutonomyController` creates a persistent goal with provenance, constraints, parent relation, and budget
3. `ObservationService` creates a bounded immutable `ObservationSnapshot`
4. `TaskPlanner` receives the goal, active subgoal, relevant memory, recent results, failed approaches, action schemas, and remaining budget
5. `ResponseParser` accepts only the bounded operational decision schema
6. `Plan` and `PlanStep` represent the current receding horizon
7. `ActionExecutor` runs one action at a time on server ticks
8. `GoalEvaluator` verifies deterministic completion
9. `RecoveryEngine` retries, creates prerequisite goals, records protected locations, or requests a fresh horizon
10. `SteveMemory` stores bounded episodic, spatial, failure, and goal history

HTTP remains asynchronous. World, inventory, navigation, entity, and NBT mutations remain on the server thread.

### Core Components

**LLM Integration** (`com.steve.ai.llm`)
- **GeminiClient, GroqClient, OpenAIClient**: Pluggable LLM providers for agent reasoning
- **TaskPlanner**: Orchestrates LLM calls with context (conversation history, world state, Steve capabilities)
- **PromptBuilder**: Constructs prompts with available actions, examples, and formatting instructions
- **ResponseParser**: Extracts structured action sequences from LLM responses

**Action System** (`com.steve.ai.action`)
- **ActionExecutor**: Tick-based action runtime. It does not own autonomous goal recovery.
- **BaseAction**: Abstract lifecycle with server-thread start, tick, cancel, finish, and structured result hooks.
- **Plan / PlanStep**: Bounded executable horizon and per-step progress metadata.
- **SearchResourceAction**: Bounded pathfinding-aware resource discovery without teleporting.
- **Available action families**:
  - movement: `pathfind`, `follow`
  - gathering: `mine`, `gather`, `search_resource`, `pickup_item`
  - construction: `place`, `build`
  - inventory: give, deposit, withdraw, equip, unequip, drop, consume, inspect
  - survival processing: `craft`, `smelt`
  - combat: `attack`

**Structure Generation** (`com.steve.ai.structure`)
- **StructureGenerators**: Procedural generation algorithms (houses, castles, towers, barns)
- **StructureTemplateLoader**: NBT file loading from resources
- **BlockPlacement**: Shared data structure for block positioning

**Multi-Agent Collaboration** (`com.steve.ai.action`)
- **CollaborativeBuildManager**: Server-side coordination for parallel building
- **Spatial partitioning**: Automatically divides structures into non-overlapping sections
- **Work distribution**: Assigns sections to available Steves
- **Conflict prevention**: Atomic block placement with position tracking
- **Dynamic rebalancing**: Reassigns work when agents finish early

**Memory & Context** (`com.steve.ai.memory`, `com.steve.ai.perception`)
- **SteveMemory**: Bounded persistent memory for active/pending goals, recent episodes, goal outcomes, spatial facts, protected locations, and failed approaches.
- **WorldFact**: Timestamped, confidence-scored, dimension-aware facts with TTL.
- **EpisodicMemoryEntry**: Compact action/result/goal records.
- **ObservationSnapshot**: Immutable point-in-time perception, distinct from historical memory.
- **ObservationService**: Cooldown-based perception scheduler with bounded relevance selection.
- **StructureRegistry**: Catalogs built structures for reference and avoidance.

**Autonomous Executive** (`com.steve.ai.autonomy`)
- **AutonomyController**: Goal queue, observe/plan/act/evaluate/recover loop, asynchronous planning, interruption semantics, prerequisites, and budgets.
- **AutonomyMode**: `OFF`, `GOAL_DRIVEN`, and `PROACTIVE`.
- **RecoveryEngine / FailureTracker**: Deterministic recovery and repeated-strategy protection.
- **GoalEvaluator**: Conservative verification of inventory, delivery, position, and completed horizons.

**Code Execution** (`com.steve.ai.execution`)
- JavaScript execution is currently disabled. The compatibility facade rejects scripts until
  execution can be bounded by a real timeout and resource-limited sandbox.

### Key Design Decisions

**Tick-Based Execution**
Actions run incrementally across multiple game ticks rather than blocking. This prevents server freezes and maintains responsiveness. Each action's `tick()` method does minimal work per frame and tracks progress internally.

**Goal-driven planning**
The normal mode uses receding-horizon planning. The LLM proposes only a small number of executable tasks. Steve observes again after progress or failure, verifies deterministic conditions, and requests another horizon when the goal remains incomplete. This keeps context and API spending bounded while allowing the world to change.

**Server-thread boundary**
LLM and HTTP futures return immutable response data. The entity tick consumes those results and is the only path that mutates navigation, blocks, entities, inventories, containers, equipment, or NBT.

**Multi-Agent Coordination**
Collaborative builds continue to use deterministic spatial partitioning. Goal and plan identifiers are designed so a future shared job board can be added without changing the single-agent executive.

**Memory Management**
Context windows use bounded recent episodes, top relevant world facts, current plan steps, failed-strategy fingerprints, and current budget. Persisted memory is capped and versioned; transient `BaseAction` instances are never resumed blindly after a restart.

### Integration with Minecraft

**Entity Registration**
Steves are custom EntityType registered via Forge's deferred registry system. They extend PathfinderMob for vanilla pathfinding integration and implement custom goals for AI behavior.

**Event Hooks**
- ServerStarting: Initialize collaborative build manager
- ServerStopping: Cleanup active tasks and save state
- ClientTick: GUI rendering and input handling

**GUI Implementation**
Custom overlay GUI activated with K key. Uses Minecraft's Screen class with custom rendering. Text input forwarded to TaskPlanner on submission.

## Building from Source

Use Java 17 with a local Gradle 8.x installation. ForgeGradle 6 does not support Gradle 9+, so `gradle build` fails when your installed Gradle is too new. Gradle 8.4 is the known-good version for this project:

```bash
git clone https://github.com/gnustella-lab/Steve-AI.git
cd Steve-AI
JAVA_HOME=/path/to/jdk17 gradle build
```

If your default `gradle` is 9.x or newer, install/select Gradle 8.4 before building, or regenerate the wrapper JAR locally (do not commit it in this fork):

```bash
gradle wrapper --gradle-version 8.4
JAVA_HOME=/path/to/jdk17 ./gradlew build
```

Install `build/libs/steve-ai-mod-<version>.jar`, which includes all required runtime libraries. The
`-slim.jar` artifact is only for development and will not load by itself in Minecraft. To test in development:

```bash
JAVA_HOME=/path/to/jdk17 gradle runClient
```

**Project Structure:**
```
src/main/java/com/steve/ai/
├── entity/          # Steve entity, spawning, lifecycle
├── llm/             # LLM clients, prompt building, response parsing
├── action/          # Action classes and collaborative build manager
├── structure/       # Procedural generation and template loading
├── memory/          # Context management and world knowledge
├── execution/       # Action runtime and disabled JavaScript compatibility facade
├── client/          # GUI overlay
└── command/         # Minecraft commands (/steve spawn, etc)
```

## Contributing

We welcome contributions! Here's how to get started:

### Reporting Bugs

1. Check [existing issues](https://github.com/gnustella-lab/Steve-AI/issues) first
2. Include:
   - Minecraft/Forge/Steve AI versions
   - Steps to reproduce
   - Expected vs actual behavior
   - Logs from `logs/latest.log`

### Submitting Code

1. **Fork and clone**
   ```bash
   git clone https://github.com/YourUsername/Steve-AI.git
   cd Steve-AI
   ```

2. **Create feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make changes**
   - Follow code style (4-space indent, JavaDoc for public APIs)
   - Test with `JAVA_HOME=/path/to/jdk17 gradle build && JAVA_HOME=/path/to/jdk17 gradle runClient`

4. **Submit PR**
   - Clear commit messages
   - Describe changes and reasoning
   - Link related issues

### Code Style

- **Classes**: PascalCase
- **Methods/Variables**: camelCase
- **Constants**: UPPER_SNAKE_CASE
- **Indentation**: 4 spaces
- **Line length**: Max 120 characters
- **Comments**: JavaDoc for public methods

**Adding New Actions:**
1. Extend `BaseAction` in `com.steve.ai.action.actions`
2. Implement `tick()`, `isComplete()`, `onCancel()`
3. Update `PromptBuilder.java` to inform LLM about new action
4. Add example usage in prompt template

## Configuration

Edit `config/steve-common.toml` or start from `config/steve-common.toml.example`:

```toml
[ai]
provider = "groq"
maxTokens = 8000
temperature = 0.7

[autonomy]
enabled = true
mode = "GOAL_DRIVEN" # OFF, GOAL_DRIVEN, PROACTIVE
thinkCooldownTicks = 40
maxPlanHorizon = 4
maxReplansPerGoal = 8
maxRetriesPerStep = 3
maxLlmCallsPerGoal = 12
maxConsecutiveFailures = 5
maxRepeatedFailureFingerprint = 2
idleThinkInterval = 1200
perceptionIntervalTicks = 20
proactiveMaintenance = false

[groq]
apiKey = ""
model = "llama-3.1-8b-instant"
```

API keys can also be supplied through `STEVE_OPENAI_API_KEY`, `STEVE_GROQ_API_KEY`, or `STEVE_GEMINI_API_KEY`. They are never shown in autonomy status output.

Useful commands:

```text
/steve goal <name> <goal>
/steve tell <name> <goal>       # compatibility alias
/steve status <name>
/steve pause <name>
/steve resume <name>
/steve stop <name>              # absolute cancellation, no automatic resume
```

`OFF` preserves the legacy command-to-plan path. `GOAL_DRIVEN` is the safe default for explicit user goals. `PROACTIVE` is opt-in and currently limited to configured maintenance heuristics.

**Performance Tips:**
- Use Groq for fastest inference (recommended for gameplay)
- GPT-4 for better planning but higher latency
- Lower temperature (0.5-0.7) for more deterministic actions

## Current Limitations

- The default `GOAL_DRIVEN` mode can continue user goals across action failures, missing materials, tool prerequisites, and bounded replans. It does not invent unrelated goals.
- `PROACTIVE` is opt-in and maintenance is disabled by default. It must not be used as a griefing or unrestricted construction policy.
- A goal is marked complete only when a deterministic evaluator can verify it or when an unsupported semantic goal has a successful bounded terminal action. Deterministic inventory and delivery goals require observed quantities or delivery results.
- Protected regions and descriptor permissions remain authoritative. The executive never falls back to commands, shell execution, reflection, or arbitrary Java execution.
- Resource search and mining are bounded. Steve can still become `BLOCKED` when the configured retry, replan, or LLM budget is exhausted.
- Crafting and smelting are registered and integrated with prerequisite reporting, but full survival reliability depends on the actual world, recipes, containers, navigation, and available materials.

## Future Work

Planned follow-up work:
- richer navigation alternatives and path quality scoring
- more structure templates and deterministic construction planning
- shared job board and resource reservations for multi-agent work
- voice commands via a separate, permissioned input layer
- additional GameTests for restart, protected regions, and long-running goals
- optional compact memory summaries for very long sessions

## Why We Made This

We wanted to see if the Cursor model could work outside of coding. Turns out it translates pretty well. Same principles: deep environment integration, clear action primitives, persistent context.

Minecraft is actually a good testbed for agent research. Complex enough to be interesting, constrained enough that agents can actually succeed.

Plus it's just fun watching AIs build castles while you explore.

## Credits

- OpenAI/Groq/Google for LLM APIs
- Minecraft Forge for the modding framework
- LangChain/AutoGPT for agent architecture inspiration

## License

MIT

## Issues

Found a bug? [Open an issue in this repository](https://github.com/gnustella-lab/Steve-AI/issues).