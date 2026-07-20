# Yarn → Mojang mappings migration inventory (Layer 2)

> **Status: COMPLETED (Minecraft 1.21.11).** The mod now builds against official
> Mojang mappings via `loom.officialMojangMappings()` in `build.gradle`; the
> `yarn_mappings` property has been removed from `gradle.properties` and `build.gradle`.
> The checklist below is retained for historical reference. Note that a few "expected
> Mojang symbol" entries below differed from the actual 1.21.11 Mojmap and were
> corrected against the remapped jar during the migration — most notably:
> `Identifier` is kept (in `net.minecraft.resources`, not renamed to `ResourceLocation`),
> `ResourceKey.identifier()` (not `location()`), `Identifier.fromNamespaceAndPath(...)`
> (no `.of(...)`), `GameRules` lives in `net.minecraft.world.level.gamerules` with
> `.get(rule)`, command permissions use `PermissionSet.ALL_PERMISSIONS`, and
> `TellRawCommand`'s private static method remains unnamed (`method_13777`, `remap=false`).

Fabric Yarn mappings are frozen at Minecraft 1.21.11 and will not be published for
newer Minecraft versions. Any upgrade past 1.21.11 therefore requires migrating the
mod off Yarn mappings and onto official Mojang (Mojmap) mappings.

This document is the **Layer 2 migration checklist** from the "derisking the Yarn
switch" plan. It inventories every place the code touches Minecraft's mapped API so
the rename can be performed mechanically and reviewed line by line, rather than
discovered at compile (or worse, at runtime) time. The Layer 1 unit-test baseline
(`minecord-api` `StringTemplateTests` + `StringUtilsTests`, 40 tests) must stay green
throughout the migration.

> **Scope.** This is an inventory/checklist only — it makes **no code changes**. The
> Mojang symbol names below are the expected targets to verify against the official
> mappings for the destination Minecraft version; treat them as "verify + apply", not
> as authoritative until confirmed against the actual Mojmap for that version.

## How to read this

- **Yarn symbol** — what the code uses today (Yarn 1.21.11).
- **Mojang symbol** — the expected Mojmap equivalent to confirm and rename to.
- Mixin descriptors (`@Inject`/`@Redirect`/`@Accessor` targets) are called out
  separately because a wrong descriptor **fails silently at runtime** — the mixin
  simply does not apply — instead of failing the compile.

---

## 0. Build configuration (do this first)

- [ ] `build.gradle:29` — replace the Yarn mappings dependency
      `mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"` with official Mojang
      mappings, e.g. `mappings loom.officialMojangMappings()`.
- [ ] `gradle.properties` (root + `minecord-api/gradle.properties`) — remove/retire the
      `yarn_mappings` property once no longer referenced; bump `minecraft_version`,
      `loader_version`, `fabric_version` to the destination version.
- [ ] Accept the Mojang mappings licence flag if Loom requires it for the target version.
- [ ] Re-run `./gradlew build test` — expect a wave of "cannot find symbol" errors that
      map onto the class/method renames catalogued below. Work through them per module.

---

## 1. Class-level renames (imports)

These `net.minecraft.*` types are imported across the 34 mapping-dependent classes.
Yarn and Mojmap frequently agree on class names but differ on package and, especially,
on **method/field** names (Section 2). Confirm each against the target Mojmap.

| Yarn class (import) | Expected Mojang class | Notes |
| --- | --- | --- |
| `net.minecraft.server.MinecraftServer` | `net.minecraft.server.MinecraftServer` | package usually stable |
| `net.minecraft.server.network.ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` | class + package rename |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` | class + package rename |
| `net.minecraft.server.command.ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` | class + package rename |
| `net.minecraft.entity.damage.DamageSource` | `net.minecraft.world.damagesource.DamageSource` | package rename |
| `net.minecraft.util.crash.CrashReport` | `net.minecraft.CrashReport` | package rename |
| `net.minecraft.entity.LivingEntity` | `net.minecraft.world.entity.LivingEntity` | package rename |
| `net.minecraft.advancement.Advancement` | `net.minecraft.advancements.Advancement` | package rename |
| `net.minecraft.advancement.AdvancementEntry` | `net.minecraft.advancements.AdvancementHolder` | class + package rename |
| `net.minecraft.advancement.AdvancementDisplay` | `net.minecraft.advancements.DisplayInfo` | class + package rename |
| `net.minecraft.advancement.PlayerAdvancementTracker` | `net.minecraft.server.PlayerAdvancements` | class + package rename |
| `net.minecraft.world.World` | `net.minecraft.world.level.Level` | class + package rename |
| `net.minecraft.server.world.ServerWorld` | `net.minecraft.server.level.ServerLevel` | class + package rename |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.ResourceLocation` | class + package rename |
| `net.minecraft.util.Formatting` | `net.minecraft.ChatFormatting` | class + package rename |
| `net.minecraft.util.math.BlockPos` | `net.minecraft.core.BlockPos` | package rename |
| `net.minecraft.util.math.Vec3d` | `net.minecraft.world.phys.Vec3` | class + package rename |
| `net.minecraft.util.math.Vec2f` | `net.minecraft.world.phys.Vec2` | class + package rename |
| `net.minecraft.network.message.SignedMessage` | `net.minecraft.network.chat.PlayerChatMessage` | class + package rename |
| `net.minecraft.network.message.MessageType` | `net.minecraft.network.chat.ChatType` | class + package rename |
| `net.minecraft.command.argument.EntityArgumentType` | `net.minecraft.commands.arguments.EntityArgument` | class + package rename |
| `net.minecraft.command.argument.TextArgumentType` | `net.minecraft.commands.arguments.ComponentArgument` | class + package rename |
| `net.minecraft.command.argument.GameProfileArgumentType` | `net.minecraft.commands.arguments.GameProfileArgument` | class + package rename |
| `net.minecraft.command.EntitySelectorReader` | `net.minecraft.commands.arguments.selector.EntitySelectorParser` | class + package rename |
| `net.minecraft.command.permission.LeveledPermissionPredicate` | *verify* | permission API; confirm target |
| `net.minecraft.server.command.TellRawCommand` | `net.minecraft.server.commands.TellRawCommand` | package rename |
| `net.minecraft.server.command.CommandOutput` | `net.minecraft.commands.CommandSource` | class + package rename |
| `net.minecraft.server.network.ServerPlayNetworkHandler` | `net.minecraft.server.network.ServerGamePacketListenerImpl` | class rename |
| `net.minecraft.server.PlayerConfigEntry` | *verify* | confirm target |
| `net.minecraft.entity.player.PlayerEntity` | `net.minecraft.world.entity.player.Player` | class + package rename |
| `net.minecraft.stat.Stats` | `net.minecraft.stats.Stats` | package rename |
| `net.minecraft.world.rule.GameRules` | `net.minecraft.world.level.GameRules` | package rename |

---

## 2. Method / field renames (call sites)

The riskiest *compile-time* renames are chained method calls where Yarn and Mojmap
diverge on method names. Confirm each against the target Mojmap.

| File (relative to module `src/main/java/me/axieum/mcmod/minecord/`) | Yarn call | Expected Mojang call |
| --- | --- | --- |
| `minecord-api :: api/util/StringUtils.java` | `world.getRegistryKey().getValue()` | `level.dimension().location()` |
| `minecord-api :: api/util/StringUtils.java` | `Formatting.strip(...)` | `ChatFormatting.stripFormatting(...)` |
| `minecord-api :: impl/placeholder/MinecordPlaceholders.java` | `Identifier.of(...)`, `player.getDisplayName().getString()` | `ResourceLocation.fromNamespaceAndPath(...)` / `.tryParse(...)`, `player.getDisplayName().getString()` |
| `minecord-api :: impl/MinecordImpl.java` | `server.getPlayerManager().getPlayer(uuid)` | `server.getPlayerList().getPlayer(uuid)` |
| `minecord-chat :: impl/chat/util/MinecraftDispatcher.java` | `server.getPlayerManager()`, `server.getCurrentPlayerCount()`, `getPlayerList()`, `player.getEntityWorld().getRegistryKey().getValue()` | `server.getPlayerList()`, `getPlayerCount()`, `getPlayers()`, `player.level().dimension().location()` |
| `minecord-chat :: impl/chat/config/ChatConfig.java` | `world.getRegistryKey().getValue()` | `level.dimension().location()` |
| `minecord-chat :: impl/chat/callback/discord/MessageUpdateListener.java` | `Formatting.RED/GREEN/RESET` | `ChatFormatting.RED/GREEN/RESET` |
| `minecord-chat :: impl/chat/callback/minecraft/PlayerDeathCallback.java` | `player.getDisplayName().getString()`, `player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_DEATH))`, `player.getUuidAsString()` | `player.getDisplayName().getString()`, `player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH))`, `player.getStringUUID()` |
| `minecord-chat :: impl/chat/callback/minecraft/PlayerChangeWorldCallback.java` | `player.getUuidAsString()` | `player.getStringUUID()` |
| `minecord-chat :: impl/chat/callback/minecraft/EntityDeathCallback.java` | `entity.getDisplayName().getString()` | `entity.getDisplayName().getString()` (confirm nullable) |
| `minecord-chat :: impl/chat/callback/minecraft/PlayerConnectionCallback.java` | `player.getUuidAsString()` | `player.getStringUUID()` |
| `minecord-chat :: impl/chat/callback/minecraft/ServerMessageCallback.java` | `source.getWorld()` | `source.getLevel()` |
| `minecord-chat :: api/chat/event/ChatPlaceholderEvents.java` | `MessageType.Parameters` | `ChatType.Bound` |
| `minecord-cmds :: impl/cmds/command/discord/*.java` | see Section 4 | verify per command |

> The `getUuidAsString()` → `getStringUUID()` rename appears in **four** chat callbacks
> — grep for it to catch them all in one pass.

---

## 3. Mixin descriptors — HIGHEST RISK

Mixin `@Inject`/`@Redirect`/`@Accessor`/`@Shadow` targets are string- and
descriptor-based. Under Mojmap the referenced owner classes, method names, and field
names all change, and a mismatch makes the mixin **silently fail to apply** — no
compile error, no exception, just missing behaviour. Each of these must be re-derived
against the target Mojmap and validated at runtime (Layer 3).

- [ ] **`minecord-api :: mixin/api/MinecraftServerMixin.java`**
  - `@Inject(method = "runServer", at = @At("TAIL"))` — confirm `runServer` name.
  - `@Inject(method = "setCrashReport", at = @At("TAIL"))` — confirm `setCrashReport` name.
  - Field type `CrashReport` (package moves to `net.minecraft.CrashReport`).

- [ ] **`minecord-chat :: mixin/chat/LivingEntityMixin.java`**
  - `@Inject(method = "onDeath", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/world/World;sendEntityStatus(Lnet/minecraft/entity/Entity;B)V"))`
  - Both the owner (`World` → `Level`) **and** method (`sendEntityStatus` → likely
    `broadcastEntityEvent`) rename in the descriptor. Rewrite the full `L…;name(…)V`
    signature.

- [ ] **`minecord-chat :: mixin/chat/ServerPlayerEntityMixin.java`**
  - `@Inject(method = "onDeath", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/server/world/ServerWorld;sendEntityStatus(Lnet/minecraft/entity/Entity;B)V"))`
  - Owner `ServerWorld` → `ServerLevel`, method `sendEntityStatus` → `broadcastEntityEvent`,
    `Entity` param package moves. Rewrite full descriptor.

- [ ] **`minecord-chat :: mixin/chat/LivingEntityAccessor.java`**
  - `@Accessor(value = "lastBlockPos")` — confirm the Mojmap field name for
    `lastBlockPos` and the `BlockPos` (→ `net.minecraft.core.BlockPos`) return type.

- [ ] **`minecord-chat :: mixin/chat/PlayerAdvancementTrackerMixin.java`**
  - `@Mixin(PlayerAdvancementTracker.class)` → target `PlayerAdvancements`.
  - `@Shadow private ServerPlayerEntity owner;` → confirm shadowed field name/type
    (`ServerPlayer owner` / possibly renamed field).
  - `@Inject(method = "grantCriterion", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/advancement/AdvancementRewards;apply(Lnet/minecraft/server/network/ServerPlayerEntity;)V"))`
    — owner `AdvancementRewards` package moves, param `ServerPlayerEntity` → `ServerPlayer`.
  - `advancement.value()` on `AdvancementEntry` → `AdvancementHolder` accessor.

- [ ] **`minecord-chat :: mixin/chat/TellRawCommandMixin.java`** ⚠️ **sharpest risk**
  - Targets `@Inject(method = "method_13777", ...)` and
    `@Redirect(method = "method_13777", ...)` — **`method_13777` is a Yarn *intermediary*
    name**, not a mapped name. It will not exist under Mojmap and there is no direct
    textual equivalent; the lambda/synthetic it refers to must be re-identified against
    the target version's `TellRawCommand` (the command's `execute`/registration lambda),
    or the mixin re-approached entirely. `remap = false` is set today because of the
    intermediary — that will need re-evaluation.
  - `@Redirect` target
    `Lnet/minecraft/command/argument/EntityArgumentType;getPlayers(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;)Ljava/util/Collection;`
    → owner `EntityArgument`, method `getPlayers`, confirm signature.
  - `EntityArgumentType.PLAYER_NOT_FOUND_EXCEPTION` → `EntityArgument.NO_PLAYERS_FOUND`
    (confirm constant name).
  - `EntitySelectorReader.SELECTOR_PREFIX` → `EntitySelectorParser.SYNTAX_SELECTOR_START`
    (confirm constant name).

---

## 4. Per-module summary

- **`minecord-api`** (2 mixins + `StringUtils`/`MinecordImpl`/`MinecordPlaceholders`):
  server lifecycle, crash report, world-name, placeholder registration.
- **`minecord-chat`** (4 mixins + dispatchers/callbacks): the bulk of the mapping
  surface — entity/player death, advancements, `/tellraw`, `/say`/`/me`, world filters.
- **`minecord-cmds`**: `TPSCommand`, `UptimeCommand`, `CustomCommand`,
  `DiscordCommandListener`, `MinecordCommand`, `MinecordCommandEvents` — verify their
  `ServerCommandSource`/`MinecraftServer`/`Text` usages.
- **`minecord-presence`**: no `net.minecraft.*` imports — **no mapping changes needed**.

## 5. Validation gates

1. `./gradlew build` compiles cleanly under Mojmap (Section 1 + 2 done).
2. `./gradlew test` — all Layer 1 unit tests stay green (behaviour unchanged).
3. Layer 3 server smoke tests exercise every mixin path in Section 3 (join/leave,
   player death, mob death, advancement grant, `/tellraw @a`, chat relay), since those
   are the paths a bad descriptor breaks without any compile-time signal.
