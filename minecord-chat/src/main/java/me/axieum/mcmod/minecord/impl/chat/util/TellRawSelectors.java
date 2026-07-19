package me.axieum.mcmod.minecord.impl.chat.util;

/**
 * Mapping-agnostic helpers for interpreting {@code /tellraw} entity selectors.
 *
 * <p>The {@code /tellraw} mixin needs to know whether a command targets <em>all</em> players (i.e.
 * the {@code @a} selector) so it can both broadcast to Discord and suppress the "no player found"
 * error when nobody is online. The selector-token comparison is pure string logic and is isolated
 * here so it can be characterised without a Minecraft runtime — the surrounding mixin only supplies
 * the already-extracted selector token and the vanilla selector-prefix character.
 */
public final class TellRawSelectors
{
    private TellRawSelectors() {}

    /**
     * Returns whether the given selector token targets all players (the {@code @a} selector).
     *
     * @param token          the raw selector token as it appeared in the command input, e.g. {@code @a}
     * @param selectorPrefix the vanilla selector-prefix character, i.e. {@code @}
     * @return {@code true} if the token is exactly the all-players selector
     */
    public static boolean isAllPlayersSelector(final String token, final char selectorPrefix)
    {
        return token != null && token.equals("" + selectorPrefix + 'a');
    }
}
