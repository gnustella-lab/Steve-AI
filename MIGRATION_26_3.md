# Migration to Minecraft 26.3 (Wilderness Bound)

This mod now targets **Minecraft Java Edition 26.3**, **Forge 66.1.9**, **Java 25** and
**ForgeGradle 7 / Gradle 9.7.1**. Compilation and tests run on GitHub Actions; see
`.github/workflows/ci.yml` and `.github/workflows/release.yml`.

## Build and metadata

- `build.gradle`: ForgeGradle `[7.0.17,8)`, `net.minecraftforge.jarjar` `0.2.3`,
  `minecraft.dependency('net.minecraftforge:forge:26.3-66.1.9')`, Java 25 toolchain.
- `settings.gradle`: Foojay resolver `1.0.0`; wrapper: Gradle 9.7.1.
- `mods.toml`: loader `[66,)`, Minecraft `[26.3,26.4,)`; `pack.mcmeta` format 121.
- CI/release workflows: Temurin 25, Gradle 9.7.1.

## Source-level API changes

| 1.20.1 API | 26.3 API used now |
| --- | --- |
| `ResourceLocation` | `net.minecraft.resources.Identifier` (`fromNamespaceAndPath`, `tryParse`) |
| `GuiGraphics`, `Screen#render` | `GuiGraphicsExtractor`, `Screen#extractRenderState` |
| `KeyMapping(name, Type, int, category)` / GLFW | `KeyMapping(name, InputConstants.KEY_GRAVE, KeyMapping.Category)` (SDL3) |
| `MouseButtonEvent`/`KeyEvent`/`CharacterEvent` | New input event records passed to screen handlers |
| `RenderGuiOverlayEvent.Post` | Forge `AddGuiOverlayLayersEvent` + `ForgeLayeredDraw` |
| `@SubscribeEvent` (old package), `IEventBus` | `eventbus.api.listener.SubscribeEvent`, `BusGroup`, event `BUS` fields |
| `FMLJavaModLoadingContext.get().getModEventBus()` | Constructor-injected context + `getModBusGroup()` |
| `EntityType.Builder#build(String)` | `build(ResourceKey)` from `DeferredRegister#key` |
| `MobSpawnType` | `EntitySpawnReason` |
| `defineSynchedData()` | `defineSynchedData(SynchedEntityData.Builder)` |
| `ItemStack.isSameItemSameTags` | `isSameItemSameComponents` |
| `ItemStack#save/of`, item NBT | `ItemStack.OPTIONAL_CODEC` + `NbtOps`, data components |
| `ContainerHelper.saveAllItems(CompoundTag, …)` | `TagValueOutput` / `TagValueInput` (`ValueOutput` / `ValueInput`) |
| `Tier`, `TieredItem`, `SwordItem`, `ArmorItem` | `DataComponents.TOOL`, `EQUIPPABLE` and attribute modifiers |
| `Recipe#getResultItem`, `Ingredient#getItems` | `RecipeDisplay#result().resolveForFirstStack`, `Ingredient#items()` (see `ModernRecipes`) |
| `RecipeManager#getAllRecipesFor` | `getRecipes()` returning `RecipeHolder`s |
| `NbtIo.readCompressed(InputStream)` | `NbtIo.readCompressed(InputStream, NbtAccounter)` |
| `TickEvent.ServerTickEvent/ClientTickEvent` | `…Post` records with `BUS` fields |
| `@GameTestHolder`, `@PrefixGameTestTemplate`, `@GameTest(templateNamespace/template/timeoutTicks)` | `@GameTestNamespace`, `@GameTestDontPrefix`, Forge `@GameTest(structure/maxTicks)` |
| `CommandSourceStack#hasPermission(int)` | `PermissionSet` / operator status via `PlayerList#isOp` |
| `Level.isClientSide` field | `Level.isClientSide()` method |

`ModernRecipes` centralizes the display-driven recipe queries used by the crafting planner.
