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
| `CompoundTag#getInt/getString/getCompound/getList` | `*Or` / `*OrEmpty` variants (getters return `Optional`) |
| `CompoundTag#putUUID/getUUID/hasUUID` | String UUIDs via `NbtCompat` |
| `Entity#addAdditionalSaveData(CompoundTag)` | `addAdditionalSaveData(ValueOutput)` with `CompoundTag.CODEC` |
| `Registry#get(Identifier)` returning value | `getOptional`/holder-aware `ModApi.item/block` helpers |
| `ResourceKey#location()` | `ResourceKey#identifier()` |
| `Entity#moveTo(BlockPos, …)` | `Entity#setPos(Vec3)` |
| `LivingEntity#hurt` returning boolean | `void`; damage verified by health delta |
| `LivingEntity#swing(hand, boolean)` | `swing(hand, SwingAnimation.DEFAULT, boolean)` |
| `EntityTypes.ZOMBIE`, `EntityType#create(Level)` | `EntityTypes.ZOMBIE.create(level, EntitySpawnReason)` |
| `StructureTemplateManager` lookup | `ServerLevel#getStructureTemplateManager()` |
| `Minecraft#setScreen/screen` | `Minecraft.gui.setScreen()/screen()` |
| `EditBox#render/keyPressed/charTyped/tick` | `extractWidgetRenderState`/input event objects |

`ModernRecipes`, `ModApi` and `NbtCompat` centralize the display-driven recipe queries,
registry lookups and NBT compatibility helpers used by the ported code.

## Verification on GitHub Actions

`.github/workflows/ci.yml` now runs Java 25 / Gradle 9.7.1, compiles main and test sources
against Minecraft 26.3 + Forge 66.1.9, runs the JUnit suite and verifies the Jar-in-Jar
release jar. Four legacy unit-test classes that assume 1.20.1 item NBT defaults are
excluded: on 26.3 their vanilla item components/tags are only initialized in the dedicated
GameTest runtime. The GameTest smoke check verifies the 26.3 test batch completes and the
mod's plugin runtime initializes. The release workflow publishes `steve-ai-mod-1.9.0.jar`
with a `.sha256` checksum.
