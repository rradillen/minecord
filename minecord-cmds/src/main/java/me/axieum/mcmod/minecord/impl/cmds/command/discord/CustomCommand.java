package me.axieum.mcmod.minecord.impl.cmds.command.discord;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderHandler;
import eu.pb4.placeholders.api.node.EmptyNode;
import eu.pb4.placeholders.api.node.TextNode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ParsingException;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.rule.GameRules;

import me.axieum.mcmod.minecord.api.Minecord;
import me.axieum.mcmod.minecord.api.cmds.command.MinecordCommand;
import me.axieum.mcmod.minecord.api.cmds.event.MinecordCommandEvents;
import me.axieum.mcmod.minecord.api.util.PlaceholdersExt;
import me.axieum.mcmod.minecord.impl.cmds.config.CommandConfig;
import static me.axieum.mcmod.minecord.api.util.PlaceholdersExt.string;
import static me.axieum.mcmod.minecord.impl.cmds.MinecordCommandsImpl.LOGGER;
import static me.axieum.mcmod.minecord.impl.cmds.MinecordCommandsImpl.getConfig;

/**
 * Custom Minecraft proxy Minecord command.
 */
public class CustomCommand extends MinecordCommand
{
    // The custom command config instance
    private final CommandConfig.CustomCommandSchema config;

    /**
     * Constructs a new custom command.
     *
     * @param config custom command config
     */
    public CustomCommand(CommandConfig.CustomCommandSchema config)
    {
        super(config.name, config.description);
        this.config = config;
        setEphemeral(config.ephemeral);
        setCooldown(config.cooldown);
        setCooldownScope(config.cooldownScope);
        data.setDefaultPermissions(
            config.allowByDefault ? DefaultMemberPermissions.ENABLED : DefaultMemberPermissions.DISABLED
        );
        Arrays.stream(config.options)
            .map(CommandConfig.BaseCommandSchema.OptionSchema::getOptionData)
            .forEach(data::addOptions);
    }

    @Override
    public boolean isEphemeral()
    {
        return config.ephemeral;
    }

    @Override
    public void execute(@NotNull SlashCommandInteractionEvent event, @Nullable MinecraftServer server) throws Exception
    {
        assert server != null; // this.requiresMinecraft = true

        // Prepare the Minecraft command
        final String origCommand;
        try {
            origCommand = prepareCommand(config.commandNode, event.getOptions(), server);
        } catch (ParsingException | IllegalArgumentException e) {
            throw new Exception("Unable to prepare Minecraft command!", e);
        }

        // Fire an event to allow the command execution to be mutated or cancelled
        final String mcCommand = MinecordCommandEvents.Custom.ALLOW_EXECUTE.invoker().onAllowCustomCommand(
            this, event, server, origCommand
        );
        if (mcCommand == null || mcCommand.isEmpty()) return;

        // Create a temporary command source and hence output, to relay command feedback
        final String tag = event.getUser().getName();
        final String username = event.getMember() != null
            ? event.getMember().getEffectiveName()
            : event.getUser().getName();
        final DiscordCommandOutput output = new DiscordCommandOutput(event, server, mcCommand);
        final ServerCommandSource origSource = new ServerCommandSource(
            output, // command output
            Vec3d.ZERO, Vec2f.ZERO, server.getOverworld(), // location & world
            LeveledPermissionPredicate.OWNERS, tag, Text.literal(username), // permission & display name
            server, null // server & entity
        );

        // Fire an event to allow the command source to be mutated
        final ServerCommandSource source = MinecordCommandEvents.Custom.BEFORE_EXECUTE.invoker().onBeforeCustomCommand(
            this, event, server, mcCommand, origSource
        );

        // Attempt to proxy the Minecraft command
        final AtomicBoolean success = new AtomicBoolean(false);
        final AtomicInteger result = new AtomicInteger(0);
        @Nullable CommandSyntaxException error = null;
        try {
            LOGGER.info("@{} is running '/{}'", tag, mcCommand);

            // Parse the command and build its context
            final ParseResults<ServerCommandSource> parseResults = server.getCommandManager().getDispatcher().parse(
                mcCommand,
                source.withReturnValueConsumer((bl, i) -> {
                    success.set(bl); // if unsuccessful, it may choose to raise a command syntax exception
                    result.set(i);
                })
            );
            final CommandContext<ServerCommandSource> context = parseResults.getContext().build(mcCommand);

            // Analyse the command context for a player's UUID to show their avatar on any command feedback
            findPlayerUuids(context.getLastChild()).findFirst()
                .flatMap(uuid -> Minecord.getInstance().getAvatarUrl(uuid, 16))
                .ifPresent(url -> output.thumbnailUrl = url);

            // Execute the command
            server.getCommandManager().getDispatcher().execute(parseResults);
        } catch (CommandSyntaxException e) {
            error = e;
        } finally {
            LOGGER.info(
                "@{} ran '/{}' with result {} ({})", tag, mcCommand, result.get(), success.get() ? "success" : "fail"
            );
        }

        // Fire an event to broadcast the commands successful/failed execution
        MinecordCommandEvents.Custom.AFTER_EXECUTE.invoker().onCustomCommand(
            this, event, server, mcCommand, success.get(), result.get(), error
        );

        // Finally, if there was still no command feedback sent, let them know with a default message
        // NB: This is to prevent the "The application did not respond" error in Discord, e.g. '/say' or '/tellraw'
        if (output.prevMessage == null) {
            if (error == null) {
                output.thumbnailUrl = null;
                source.sendFeedback(
                    () -> PlaceholdersExt.parseText(
                        getConfig().messages.feedbackNode, PlaceholderContext.of(source), Collections.emptyMap()
                    ),
                    false
                );
            } else {
                source.sendError(Text.literal(error.getMessage()));
            }
        }
    }

    /**
     * Substitutes a Minecraft command template with options from a Discord command.
     *
     * @param command Minecraft command template using {@code ${<name>}} for the 'name' argument
     * @param options Discord command options to substitute into the template
     * @param server  optional Minecraft server for placeholder context
     * @return a prepared Minecraft command
     * @throws ParsingException if an invalid command option type is encountered
     */
    private static String prepareCommand(
        @NotNull TextNode command,
        List<OptionMapping> options,
        @Nullable MinecraftServer server
    ) throws ParsingException
    {
        // Reduce the Discord command options down to a plain name-value mapping
        final Map<String, String> values = new HashMap<>(options.size());
        options.forEach(option -> values.put(option.getName(), option.getAsString()));

        // Delegate to the mapping-agnostic core, deriving placeholder context from the server if present
        return prepareCommand(command, values, server != null ? PlaceholderContext.of(server) : null);
    }

    /**
     * Substitutes a Minecraft command template with plain option values.
     *
     * <p>This is the mapping-agnostic core of command preparation: it depends only on the placeholder
     * API and plain strings, so it can be exercised without a Discord {@link OptionMapping} or a
     * {@link MinecraftServer}. It pins the behaviour where raw Discord input enters an op-level command,
     * namely placeholder substitution, output trimming and leading-{@code /} stripping.
     *
     * @param command Minecraft command template using {@code ${<name>}} for the 'name' argument
     * @param options mapping of option name to substituted value
     * @param ctx     optional placeholder context
     * @return a prepared Minecraft command, without any leading {@code /}
     */
    static String prepareCommand(
        @NotNull TextNode command,
        @NotNull Map<String, String> options,
        @Nullable PlaceholderContext ctx
    )
    {
        if (command == EmptyNode.INSTANCE) return "";

        // Prepare new command placeholders
        final HashMap<String, PlaceholderHandler> placeholders = new HashMap<>(options.size());
        options.forEach((name, value) -> placeholders.put(name, string(value)));

        // Parse the placeholders in the given command, then normalise the result
        return normaliseCommand(PlaceholdersExt.parseString(command, ctx, placeholders));
    }

    /**
     * Normalises a prepared command string into a form suitable for the command dispatcher.
     *
     * <p>This is the mapping-agnostic, dependency-free tail of {@link #prepareCommand}: it trims
     * surrounding whitespace and strips a single leading {@code /} so the string can be handed to
     * {@code CommandDispatcher#parse}. It decides exactly what runs at op-level 4, so a regression
     * here (e.g. failing to strip the slash, or stripping too much) would silently change the
     * command that executes — hence it is pinned independently of the placeholder/Minecraft runtime.
     *
     * @param command raw prepared command, possibly padded and/or prefixed with a single {@code /}
     * @return the trimmed command without a leading {@code /}
     */
    static String normaliseCommand(@NotNull String command)
    {
        final String result = command.trim();
        return !result.isEmpty() && result.charAt(0) == '/' ? result.substring(1) : result;
    }

    /**
     * Traverses the nodes of a Minecraft command context for a player-related
     * argument and returns their UUID if present.
     *
     * @param context Minecraft command context
     * @return a stream of Minecraft player UUIDs if present
     */
    private static Stream<String> findPlayerUuids(CommandContext<ServerCommandSource> context)
    {
        return context
            .getNodes()
            .stream()
            .map(ParsedCommandNode::getNode)
            .filter(node -> node instanceof ArgumentCommandNode)
            .map(node -> (ArgumentCommandNode<?, ?>) node)
            .map(node -> {
                try {
                    if (node.getType() instanceof EntityArgumentType) {
                        // Entity
                        return EntityArgumentType.getPlayer(context, node.getName()).getUuidAsString();
                    } else if (node.getType() instanceof GameProfileArgumentType) {
                        // Game Profile
                        Collection<PlayerConfigEntry> c = GameProfileArgumentType.getProfileArgument(
                            context, node.getName()
                        );
                        return c.size() == 1 ? c.iterator().next().id().toString() : null;
                    }
                } catch (CommandSyntaxException | IllegalArgumentException ignored) { /* ignored */ }
                return null;
            })
            .filter(Objects::nonNull);
    }

    /**
     * A virtual Minecraft command output for use via Discord.
     */
    private final class DiscordCommandOutput implements CommandOutput
    {
        private final SlashCommandInteractionEvent event;
        private final MinecraftServer server;
        private final String mcCommand;
        public boolean erroneous = false;
        public @Nullable String prevMessage = null;
        public @Nullable String thumbnailUrl = null;

        /**
         * Constructs a new virtual command output for relaying feedback to Discord.
         *
         * @param event     JDA slash command event
         * @param server    Minecraft server
         * @param mcCommand command to be executed in Minecraft (without leading '/')
         */
        private DiscordCommandOutput(SlashCommandInteractionEvent event, MinecraftServer server, String mcCommand)
        {
            this.event = event;
            this.server = server;
            this.mcCommand = mcCommand;
        }

        @Override
        public void sendMessage(Text message)
        {
            // Build an initial embed for the command feedback
            final String text = prevMessage != null ? prevMessage + '\n' + message.getString() : message.getString();
            EmbedBuilder embed = new EmbedBuilder()
                // Set the colour to green for a success, and red for a failure
                .setColor(!erroneous ? 0x00ff00 : 0xff0000)
                // Set the thumbnail
                .setThumbnail(thumbnailUrl)
                // Set the message
                .setDescription(text);

            // Fire an event to allow the command feedback to be mutated or cancelled
            embed = MinecordCommandEvents.Custom.FEEDBACK.invoker().onCustomCommandFeedback(
                CustomCommand.this, event, server, mcCommand, message, !erroneous, embed
            );

            // Build and reply with the resulting embed
            if (embed != null) {
                if (prevMessage == null) {
                    // This is the first reply, send a new message
                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                } else {
                    // There already exists a reply, edit the original message
                    event.getHook().editOriginalEmbeds(embed.build()).queue();
                }
                prevMessage = text;
            }
        }

        @Override
        public boolean shouldReceiveFeedback()
        {
            return true;
        }

        @Override
        public boolean shouldTrackOutput()
        {
            // This method appears to only be called during 'ServerCommandSource#sendError'
            return erroneous = true;
        }

        @Override
        public boolean shouldBroadcastConsoleToOps()
        {
            return server.getOverworld().getGameRules().getValue(GameRules.COMMAND_BLOCK_OUTPUT);
        }
    }
}
