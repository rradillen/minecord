package me.axieum.mcmod.minecord.api.util;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.MarkdownLiteParserV1;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.TagLikeParser;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import me.axieum.mcmod.minecord.api.Minecord;

/**
 * Extension methods for interfacing with the {@link eu.pb4.placeholders.api.Placeholders Placeholder API}.
 */
public final class PlaceholdersExt
{
    /** A handler that resolves a placeholder (with an optional argument) to a result. */
    @FunctionalInterface
    public interface PlaceholderHandler
    {
        /**
         * Resolves the placeholder.
         *
         * @param ctx placeholder context
         * @param arg placeholder argument, or {@code null} if absent
         * @return placeholder result
         */
        PlaceholderResult onPlaceholderRequest(@Nullable ServerPlaceholderContext ctx, @Nullable String arg);
    }

    /** A lookup of placeholder handlers by key. */
    @FunctionalInterface
    public interface PlaceholderGetter
    {
        /**
         * Returns the handler for the given placeholder key.
         *
         * @param key placeholder key
         * @return placeholder handler, or {@code null} if unknown
         */
        @Nullable PlaceholderHandler get(String key);
    }

    /** Parser context key for the placeholder resolver that is applied when a parsed node is rendered. */
    private static final ParserContext.Key<Function<String, Component>> RESOLVER_KEY =
        ParserContext.Key.of("minecord:placeholder_resolver");
    /** The placeholder node parser. */
    public static final NodeParser NODE_PARSER = NodeParser.builder()
        .quickText()
        .placeholders(TagLikeParser.Format.of('%', '%'), RESOLVER_KEY)
        .build();

    private PlaceholdersExt() {}

    /*
     * Placeholder contexts.
     */

    /**
     * Returns the Minecraft server captured by Minecord placeholder context if it exists.
     *
     * @return Minecraft server placeholder context if present
     */
    public static @Nullable ServerPlaceholderContext getMinecordServerContext()
    {
        return Minecord.getInstance().getMinecraft().map(ServerPlaceholderContext::of).orElse(null);
    }

    /*
     * Component-output placeholder parsers.
     */

    /**
     * Formats a placeholder template into a Minecraft text component.
     *
     * @param node pre-parsed placeholder text node
     * @param context placeholder context
     * @param placeholders mapping of placeholder key-value pairs
     * @return formatted Minecraft text
     */
    public static Component parseText(
        @NotNull TextNode node,
        @Nullable ServerPlaceholderContext context,
        @NotNull Map<String, PlaceholderHandler> placeholders
    )
    {
        return parseText(node, context, placeholders::get);
    }

    /**
     * Formats a placeholder template into a Minecraft text component.
     *
     * @param node pre-parsed placeholder text node
     * @param context placeholder context
     * @param placeholderGetter function that takes placeholder key and returns value
     * @return formatted Minecraft text
     */
    public static Component parseText(
        @NotNull TextNode node,
        @Nullable ServerPlaceholderContext context,
        @NotNull PlaceholderGetter placeholderGetter
    )
    {
        final ParserContext parserContext = context != null ? context.asParserContext() : ParserContext.of();
        return node.toComponent(
            parserContext.with(RESOLVER_KEY, raw -> resolve(raw, context, placeholderGetter)), true
        );
    }

    /**
     * Resolves a raw placeholder (e.g. {@code key} or {@code key argument}) using local, then global placeholders.
     *
     * @param raw raw placeholder text between the delimiters
     * @param context placeholder context
     * @param getter local placeholder getter
     * @return resolved text, or the unresolved placeholder if unknown
     */
    private static Component resolve(
        final String raw,
        final @Nullable ServerPlaceholderContext context,
        final PlaceholderGetter getter
    )
    {
        final int split = raw.indexOf(' ');
        final String key = split < 0 ? raw : raw.substring(0, split);
        final String arg = split < 0 ? null : raw.substring(split + 1);

        PlaceholderResult result = null;
        final PlaceholderHandler handler = getter.get(key);
        if (handler != null) {
            result = handler.onPlaceholderRequest(context, arg);
        } else if (context != null) {
            final Identifier id = Identifier.tryParse(key);
            if (id != null) result = Placeholders.parseServerPlaceholder(id, arg, context);
        }
        return result != null && result.isValid() ? result.component() : Component.literal("%" + raw + "%");
    }

    /**
     * Parses text into a placeholder text node.
     *
     * @param text placeholder template text
     * @return parsed placeholder text node
     */
    public static @NotNull TextNode parseNode(@Nullable Component text)
    {
        return text != null ? TextNode.wrap(NODE_PARSER.parseNodes(TextNode.convert(text))) : TextNode.empty();
    }

    /*
     * String-output placeholder parsers.
     */

    /**
     * Formats a placeholder template into a string.
     *
     * @param string placeholder template string
     * @param context placeholder context
     * @param placeholders mapping of placeholder key-value pairs
     * @return formatted Minecraft text
     */
    public static String parseString(
        @NotNull String string,
        @Nullable ServerPlaceholderContext context,
        @NotNull Map<String, PlaceholderHandler> placeholders
    )
    {
        return parseString(string, context, placeholders::get);
    }

    /**
     * Formats a placeholder template into a string.
     *
     * @param string placeholder template string
     * @param context placeholder context
     * @param placeholderGetter function that takes placeholder key and returns value
     * @return formatted Minecraft text
     */
    public static String parseString(
        @NotNull String string,
        @Nullable ServerPlaceholderContext context,
        @NotNull PlaceholderGetter placeholderGetter
    )
    {
        return parseString(parseNode(string), context, placeholderGetter);
    }

    /**
     * Formats a placeholder template into a string.
     *
     * @param node pre-parsed placeholder text node
     * @param context placeholder context
     * @param placeholders mapping of placeholder key-value pairs
     * @return formatted Minecraft text
     */
    public static String parseString(
        @NotNull TextNode node,
        @Nullable ServerPlaceholderContext context,
        @NotNull Map<String, PlaceholderHandler> placeholders
    )
    {
        return parseString(node, context, placeholders::get);
    }

    /**
     * Formats a placeholder template into a string.
     *
     * @param node pre-parsed placeholder text node
     * @param context placeholder context
     * @param placeholderGetter function that takes placeholder key and returns value
     * @return formatted Minecraft text
     */
    public static String parseString(
        @NotNull TextNode node,
        @Nullable ServerPlaceholderContext context,
        @NotNull PlaceholderGetter placeholderGetter
    )
    {
        return parseText(node, context, placeholderGetter).getString();
    }

    /**
     * Parses a string into a placeholder text node.
     *
     * @param string placeholder template string
     * @return parsed placeholder text node
     */
    public static @NotNull TextNode parseNode(@Nullable String string)
    {
        return string != null && !string.isEmpty() ? NODE_PARSER.parseNode(string) : TextNode.empty();
    }

    /*
     * Placeholder handlers.
     */

    /**
     * Returns a {@link String} placeholder handler.
     *
     * @param string string
     * @return string placeholder handler
     */
    public static PlaceholderHandler string(final String string)
    {
        return (ctx, arg) -> PlaceholderResult.value(string);
    }

    /**
     * Returns a {@link Component} placeholder handler.
     *
     * @param text Minecraft text
     * @return text placeholder handler
     */
    public static PlaceholderHandler text(final Component text)
    {
        return (ctx, arg) -> PlaceholderResult.value(text);
    }

    /**
     * Returns a {@link Component} markdown placeholder handler.
     *
     * @param markdown markdown string
     * @return markdown placeholder handler
     */
    public static PlaceholderHandler markdown(final String markdown)
    {
        final Component markdownText = MarkdownLiteParserV1.ALL.parseComponent(markdown, ParserContext.of());
        return (ctx, arg) -> PlaceholderResult.value(markdownText);
    }

    /**
     * Returns a {@link Duration} placeholder handler.
     *
     * @param duration duration instance
     * @return duration placeholder handler
     */
    public static PlaceholderHandler duration(final Duration duration)
    {
        return (ctx, arg) -> PlaceholderResult.value(
            arg != null ? DurationFormatUtils.formatDuration(duration.toMillis(), arg)
                : DurationFormatUtils.formatDurationWords(duration.toMillis(), true, true)
        );
    }

    /**
     * Returns a lazy {@link Duration} placeholder handler.
     *
     * @param duration duration supplier
     * @return lazy duration placeholder handler
     */
    public static PlaceholderHandler duration(final Supplier<Duration> duration)
    {
        return (ctx, arg) -> duration(duration.get()).onPlaceholderRequest(ctx, arg);
    }
}
