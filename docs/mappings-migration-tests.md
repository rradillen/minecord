# Mappings-migration test strategy

This document records the test net that derisks the Yarn → Mojang mappings migration
(see `mojang-mappings-migration.md` for the rename inventory). The migration is a
near-mechanical rename where **behaviour must stay identical**, so the goal of these
tests is to pin the invariants that a bad rename would silently break — not to forbid
the intended behaviour.

The framing throughout: **the risk is regression, not design.** The dangerous
capability — `CustomCommand` running configured commands at op-level 4 — is by design
(an admin who wires `/ban` into a Discord command wants exactly that). Tests therefore
pin *how* that capability behaves (authority, target, what string actually runs) so a
Mojmap rename cannot turn intended behaviour into false behaviour. Bad Discord-side
output is cheap and self-evident, so the Discord-only paths (presence, chat relay,
listeners) are deliberately **not** covered here.

Tests split into three layers by cost, matching what the current harness can reach.

## The hard boundary: the Fabric runtime

The current suite is plain `junit-jupiter` with **no booted Minecraft and no mocking
framework**. A pure-JUnit test can only touch code that does not initialise the Fabric
loader. Two seams that look "mapping-agnostic" actually cross this boundary and so
**cannot** run under plain JUnit:

- **Placeholder → `Text` conversion.** `PlaceholdersExt.parseString`/`parseText`
  eventually calls `eu.pb4.placeholders` `ParentNode.toText(...)`, whose
  `GeneralUtils.<clinit>` reads `FabricLoader.getInstance().isDevelopmentEnvironment()`.
  Outside a launched game there is no `FabricLauncher`, so this throws
  `ExceptionInInitializerError` (NPE). There is no system-property bypass.
- **`net.minecraft.command.EntitySelectorReader`.** `SELECTOR_PREFIX` is not an inlined
  compile-time constant, so referencing it triggers the class's static initialiser,
  which pulls in the wider Minecraft/`Text` init and hits the same wall.

The consequence drives the layering below: the pure decision logic is **extracted** away
from these seams so it can be pinned in Layer 1, and anything that genuinely needs the
runtime is pushed to Layer 3.

## Layer 1 — pure JUnit, no Minecraft runtime (implemented)

Cheap, fast, runs in the existing `:minecord-*:test` tasks with no new dependency.

- **`CustomCommand` command normalisation** —
  `minecord-cmds/.../command/discord/CustomCommandTests`.
  `prepareCommand` was split so the Fabric-free tail is a standalone
  `CustomCommand.normaliseCommand(String)` (trim + strip a single leading `/`), and the
  `EmptyNode` template short-circuit returns before any placeholder parsing. These are
  the decisions that determine the exact string handed to the command dispatcher at
  op-level 4, so they are pinned independently of the placeholder/Minecraft runtime:
  single vs. multiple leading slashes, non-leading slashes preserved, lone `/` → empty,
  whitespace trimming, and `EmptyNode` → `""` even when options are present.
  The placeholder-substitution step itself is library behaviour behind the Fabric
  boundary and is left to Layer 3.
- **`/tellraw @a` selector decision** —
  `minecord-chat/.../impl/chat/util/TellRawSelectorsTests`.
  The `@a` test in `TellRawCommandMixin.targetsAllPlayers` was extracted into a Fabric-free
  `TellRawSelectors.isAllPlayersSelector(token, selectorPrefix)` that the mixin delegates
  to. This gates both the Discord broadcast and the "no player found" suppression, so the
  token comparison (`prefix + 'a'`, exact match, null/empty-safe, prefix-driven) is pinned.
  The node-indexing that extracts the token from the `CommandContext` stays in the mixin
  and is covered by Layer 3.

## Layer 2 — command-execution wiring (deferred into Layer 3)

These invariants pin *how* `CustomCommand.execute` runs a command and are exactly what a
bad Mojmap descriptor would silently corrupt:

- the `ServerCommandSource` is built with `LeveledPermissionPredicate.OWNERS` (op-4) —
  not something weaker or stronger after the rename;
- `MinecordCommandEvents.Custom.ALLOW_EXECUTE` returning `null`/empty short-circuits
  **before** `dispatcher.execute`, so a cancelled command never runs;
- a `null`/empty prepared command never reaches the dispatcher.

They touch `ServerCommandSource`/`MinecraftServer`, so they cannot run under plain JUnit.
**Decision: do not add Mockito.** Standing up a real `ServerCommandSource` still needs a
booted server, and mocking it would only assert against a hand-built stub rather than the
real mapped types — the very thing the migration is trying to de-risk. These assertions
are therefore folded into the Layer 3 end-to-end command test, which exercises the real
source at real authority. This keeps the build's dependency surface unchanged.

> Note on threading: `dispatcher.execute()` currently runs on the JDA event thread. A
> test cannot easily assert thread-safety, but if execution is ever scheduled onto the
> server thread (`server.execute(...)`) that refactor becomes unit-testable and closes
> the most likely "corrupt the save" path. Tracked as a follow-up, out of scope for the
> mappings migration itself.

## Layer 3 — server gametest smoke tests (outstanding; the decisive layer)

A wrong mixin descriptor **fails silently** — no exception, the hook simply does not
apply — so only a test that boots a real server and exercises the real command can catch
it. This is the layer that actually protects the migration and is **not yet
implemented**: it needs a Fabric gametest harness (`fabric-gametest` + a
`runGametest`/server run configuration), which cannot be run or verified in the current
CI sandbox (no game assets / network to launch a server). It is specified here so it can
be added as a dedicated follow-up.

Target cases:

1. **`/tellraw @a` mixin fires** — run the command on a headless server and assert
   `TellRawMessageCallback` was invoked (the `@Inject` on `method_13777` applied). Catches
   the silent intermediary-descriptor mismatch called out in the migration inventory.
2. **`/tellraw @a` with zero players online does not error** — the reason the `@Redirect`
   on `EntityArgumentType.getPlayers` exists. Assert no `CommandSyntaxException` and that
   the Discord callback still fires. Catches a broken redirect.
3. **Observer mixins do not abort their host method** — trigger an entity death, a player
   death and an advancement grant; assert the vanilla side-effect completes (entity
   removed, advancement recorded) *and* the callback fired. This is what keeps a renamed
   field that throws inside `onDeath`/`grantCriterion` from degrading "bad Discord output"
   into "half-applied server state".
4. **`CustomCommand` end-to-end (also covers Layer 2)** — feed a benign state-changing
   command (e.g. `/time set` or `/gamerule`) through `CustomCommand.execute` against the
   live server; assert the state actually changed, at op-4 authority, and that an
   `ALLOW_EXECUTE` veto prevents any change.

## Running

```
./gradlew test          # all modules — Layer 1 (fast, no game)
./gradlew :minecord-cmds:test :minecord-chat:test   # targeted
```

Layer 1 must stay green throughout the migration; a red Layer 1 means behaviour changed,
not just names.
