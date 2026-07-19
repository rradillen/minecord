package me.axieum.mcmod.minecord.gametest.chat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import me.axieum.mcmod.minecord.api.chat.event.minecraft.EntityDeathEvents;
import me.axieum.mcmod.minecord.api.chat.event.minecraft.GrantCriterionCallback;
import me.axieum.mcmod.minecord.api.chat.event.minecraft.TellRawMessageCallback;

/**
 * Server game tests for the Minecord Chat mixins.
 *
 * <p>These boot a real, headless dedicated server (see {@code ./gradlew :minecord-chat:runGametest})
 * and exercise the chat mixins end-to-end. Because the chat mixins declare {@code "required": true},
 * a mapping/descriptor regression makes their injections fail to apply, which crashes mixin
 * application at server start — so merely booting this suite already validates that every chat
 * mixin still resolves. Each test below additionally confirms that the injection fires at the right
 * point and that its host method still completes, which is exactly what a Yarn → Mojang mappings
 * migration must not silently break (see {@code docs/mappings-migration-tests.md}, Layer 3).
 */
public class MinecordChatGameTest
{
    /**
     * A {@code /tellraw @a} invocation, run from the server console with no players online, must:
     * <ul>
     *     <li>not raise a "No player was found" error (the {@code @Redirect} on
     *     {@code EntityArgumentType.getPlayers} in {@code TellRawCommandMixin}), and</li>
     *     <li>still fire {@link TellRawMessageCallback} (the {@code @Inject} into the intermediary
     *     {@code method_13777}).</li>
     * </ul>
     * If the injection point were lost after a mappings migration, the callback would never fire and
     * this test would fail. If the redirect were lost, the command would abort before reaching the
     * injection tail, so the callback would likewise not fire.
     *
     * @param context game test context
     */
    @GameTest
    public void tellRawToAllPlayersFiresCallback(GameTestHelper context)
    {
        final MinecraftServer server = context.getLevel().getServer();

        final AtomicBoolean fired = new AtomicBoolean(false);
        final AtomicReference<String> received = new AtomicReference<>(null);
        TellRawMessageCallback.EVENT.register((message, source) -> {
            fired.set(true);
            received.set(message.getString());
        });

        // Run '/tellraw @a ...' as the server (op level 4) with zero players online
        server.getCommands().performPrefixedCommand(
            server.createCommandSourceStack(), "tellraw @a {\"text\":\"minecord gametest\"}"
        );

        context.assertTrue(
            fired.get(),
            "TellRawMessageCallback did not fire for '/tellraw @a' (broken @Inject or @Redirect?)"
        );
        context.assertTrue(
            "minecord gametest".equals(received.get()),
            "TellRawMessageCallback received an unexpected message: " + received.get()
        );
        context.succeed();
    }

    /**
     * Killing an animal must fire {@link EntityDeathEvents#ANIMAL_MONSTER} (the {@code @Inject} into
     * {@code LivingEntity.onDeath} in {@code LivingEntityMixin}) <em>and</em> the host {@code onDeath}
     * method must still complete (the entity actually dies). This guards against a renamed target
     * that either drops the callback or throws inside {@code onDeath} and leaves the entity half-dead.
     *
     * @param context game test context
     */
    @GameTest
    public void animalDeathFiresCallback(GameTestHelper context)
    {
        final AtomicBoolean fired = new AtomicBoolean(false);
        EntityDeathEvents.ANIMAL_MONSTER.register((entity, source) -> {
            if (entity instanceof Pig) {
                fired.set(true);
            }
        });

        final Mob pig = context.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        context.kill(pig);

        context.assertTrue(
            fired.get(),
            "EntityDeathEvents.ANIMAL_MONSTER did not fire on animal death (broken @Inject?)"
        );
        context.assertTrue(
            pig.isDeadOrDying(),
            "LivingEntity.onDeath did not complete for the killed animal"
        );
        context.succeed();
    }

    /**
     * A player death must fire {@link EntityDeathEvents#PLAYER} (the {@code @Inject} into
     * {@code ServerPlayer.onDeath} in {@code ServerPlayerEntityMixin}) and the host
     * {@code onDeath} must still complete.
     *
     * @param context game test context
     */
    @GameTest
    public void playerDeathFiresCallback(GameTestHelper context)
    {
        final AtomicBoolean fired = new AtomicBoolean(false);
        EntityDeathEvents.PLAYER.register((player, source) -> fired.set(true));

        // Invoke the exact host method the mixin injects into. A mapping/descriptor regression that
        // moved or dropped the injection point would either fail to fire the callback or throw inside
        // onDeath (failing this test), which is precisely the silent breakage we are guarding against.
        final ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.die(player.damageSources().genericKill());

        context.assertTrue(
            fired.get(),
            "EntityDeathEvents.PLAYER did not fire on player death (broken @Inject?)"
        );
        context.succeed();
    }

    /**
     * Granting an advancement criterion must fire {@link GrantCriterionCallback} (the {@code @Inject}
     * into {@code PlayerAdvancementTracker.grantCriterion} in {@code PlayerAdvancementTrackerMixin})
     * and the grant itself must still take effect. Any advancement with at least one criterion is
     * used so the test does not hard-code criterion names.
     *
     * @param context game test context
     */
    @GameTest
    public void advancementGrantFiresCallback(GameTestHelper context)
    {
        final MinecraftServer server = context.getLevel().getServer();
        final ServerPlayer player = context.makeMockServerPlayerInLevel();

        // Find any advancement that exposes at least one criterion to grant
        AdvancementHolder advancement = null;
        String criterion = null;
        for (AdvancementHolder entry : server.getAdvancements().getAllAdvancements()) {
            final Map<String, Criterion<?>> criteria = entry.value().criteria();
            if (!criteria.isEmpty()) {
                advancement = entry;
                criterion = criteria.keySet().iterator().next();
                break;
            }
        }
        context.assertTrue(advancement != null, "No advancement with a criterion was available to grant");

        final String expectedCriterion = criterion;
        final AtomicBoolean fired = new AtomicBoolean(false);
        GrantCriterionCallback.EVENT.register((redeemer, grantedAdvancement, grantedCriterion) -> {
            if (redeemer == player && expectedCriterion.equals(grantedCriterion)) {
                fired.set(true);
            }
        });

        final boolean granted = player.getAdvancements().award(advancement, criterion);

        context.assertTrue(granted, "grantCriterion did not report the criterion as newly granted");
        context.assertTrue(
            fired.get(),
            "GrantCriterionCallback did not fire on advancement grant (broken @Inject?)"
        );
        context.succeed();
    }
}
