package me.axieum.mcmod.minecord.impl.cmds.command.discord;

import java.util.Collections;
import java.util.Map;

import eu.pb4.placeholders.api.node.EmptyNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the mapping-agnostic seams of {@link CustomCommand} command preparation.
 *
 * <p>{@link CustomCommand#prepareCommand} feeds a string into the command dispatcher that then runs
 * at op-level 4, so what that string ends up being is safety-critical. The placeholder-substitution
 * step transitively initialises the Fabric loader (via {@code eu.pb4.placeholders}) and therefore
 * cannot run under plain JUnit; the parts that <em>can</em> — the {@link EmptyNode} short-circuit and
 * the trim / single leading-{@code /} strip normalisation — are exactly the decisions the Yarn-to-Mojang
 * rename must not perturb, and are pinned here.
 */
@DisplayName("CustomCommand command preparation (mapping-agnostic seams)")
public class CustomCommandTests
{
    @Nested
    @DisplayName("normaliseCommand strips a single leading slash")
    class LeadingSlash
    {
        @Test
        @DisplayName("removes a single leading slash")
        void removesLeadingSlash()
        {
            assertEquals("gamemode creative", CustomCommand.normaliseCommand("/gamemode creative"));
        }

        @Test
        @DisplayName("removes only the first of several leading slashes")
        void removesOnlyFirstSlash()
        {
            assertEquals("/kill @e", CustomCommand.normaliseCommand("//kill @e"));
        }

        @Test
        @DisplayName("keeps a non-leading slash untouched")
        void keepsNonLeadingSlash()
        {
            assertEquals("execute in minecraft:the_nether run tp @s ~ ~ ~",
                CustomCommand.normaliseCommand("execute in minecraft:the_nether run tp @s ~ ~ ~"));
        }

        @Test
        @DisplayName("treats a lone slash as an empty command")
        void treatsLoneSlashAsEmpty()
        {
            assertEquals("", CustomCommand.normaliseCommand("/"));
        }
    }

    @Nested
    @DisplayName("normaliseCommand trims surrounding whitespace")
    class Trimming
    {
        @Test
        @DisplayName("trims leading and trailing whitespace")
        void trimsWhitespace()
        {
            assertEquals("time set day", CustomCommand.normaliseCommand("   time set day   "));
        }

        @Test
        @DisplayName("strips the leading slash after trimming")
        void stripsSlashAfterTrimming()
        {
            assertEquals("say hi", CustomCommand.normaliseCommand("  /say hi  "));
        }

        @Test
        @DisplayName("reduces whitespace-only input to an empty command without throwing")
        void reducesBlankToEmpty()
        {
            assertTrue(CustomCommand.normaliseCommand("   \t ").isEmpty(),
                "a blank command should normalise to an empty string");
        }

        @Test
        @DisplayName("returns an already-normalised command verbatim")
        void returnsNormalisedVerbatim()
        {
            assertEquals("difficulty peaceful", CustomCommand.normaliseCommand("difficulty peaceful"));
        }
    }

    @Nested
    @DisplayName("prepareCommand short-circuits an empty template")
    class EmptyTemplate
    {
        @Test
        @DisplayName("returns an empty command for an EmptyNode template with no options")
        void returnsEmptyForEmptyNode()
        {
            assertEquals("", CustomCommand.prepareCommand(EmptyNode.INSTANCE, Collections.emptyMap(), null));
        }

        @Test
        @DisplayName("returns an empty command for an EmptyNode template even when options are present")
        void emptyNodeShortCircuitsBeforeParsing()
        {
            // The EmptyNode branch must return before any placeholder parsing (which needs the Fabric runtime)
            assertEquals("", CustomCommand.prepareCommand(EmptyNode.INSTANCE, Map.of("player", "Steve"), null));
        }
    }
}
