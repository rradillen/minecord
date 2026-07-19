package me.axieum.mcmod.minecord.impl.chat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the mapping-agnostic {@code @a} selector decision used by the {@code /tellraw} mixin.
 *
 * <p>This decision gates both the Discord broadcast and the "no player found" suppression in
 * {@code TellRawCommandMixin}. It is pure string logic (prefix + {@code 'a'}) and is pinned here so a
 * Yarn-to-Mojang rename that changes the selector construction is caught without a booted server.
 */
@DisplayName("TellRawSelectors.isAllPlayersSelector")
public class TellRawSelectorsTests
{
    /** The vanilla selector-prefix character, i.e. {@code EntitySelectorReader.SELECTOR_PREFIX}. */
    private static final char AT = '@';

    @Test
    @DisplayName("matches the all-players selector '@a'")
    void matchesAllPlayers()
    {
        assertTrue(TellRawSelectors.isAllPlayersSelector("@a", AT));
    }

    @Test
    @DisplayName("does not match other single-target or type selectors")
    void doesNotMatchOtherSelectors()
    {
        assertFalse(TellRawSelectors.isAllPlayersSelector("@p", AT), "@p targets the nearest player");
        assertFalse(TellRawSelectors.isAllPlayersSelector("@e", AT), "@e targets all entities");
        assertFalse(TellRawSelectors.isAllPlayersSelector("@r", AT), "@r targets a random player");
        assertFalse(TellRawSelectors.isAllPlayersSelector("@s", AT), "@s targets the command source");
    }

    @Test
    @DisplayName("does not match a bare player name or an unprefixed 'a'")
    void doesNotMatchNamesOrUnprefixed()
    {
        assertFalse(TellRawSelectors.isAllPlayersSelector("Steve", AT));
        assertFalse(TellRawSelectors.isAllPlayersSelector("a", AT));
        assertFalse(TellRawSelectors.isAllPlayersSelector("@abc", AT), "must be exactly '@a', not a prefix");
    }

    @Test
    @DisplayName("does not match an empty or null token")
    void doesNotMatchEmptyOrNull()
    {
        assertFalse(TellRawSelectors.isAllPlayersSelector("", AT));
        assertFalse(TellRawSelectors.isAllPlayersSelector(null, AT));
    }

    @Test
    @DisplayName("honours the supplied selector-prefix character")
    void honoursSuppliedPrefix()
    {
        // If the vanilla prefix ever differed, the decision tracks it rather than a hard-coded '@'
        assertTrue(TellRawSelectors.isAllPlayersSelector("#a", '#'));
        assertFalse(TellRawSelectors.isAllPlayersSelector("@a", '#'));
    }
}
