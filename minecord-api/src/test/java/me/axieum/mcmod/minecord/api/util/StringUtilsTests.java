package me.axieum.mcmod.minecord.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.Identifier;

@DisplayName("String Utils")
public class StringUtilsTests
{
    @Test
    @DisplayName("Convert bytes to human-readable strings")
    public void bytesToHuman()
    {
        assertEquals("-1.7 EB", StringUtils.bytesToHuman(-1670674413465640806L));
        assertEquals("-1.5 PB", StringUtils.bytesToHuman(-1467280456954828L));
        assertEquals("-1.7 TB", StringUtils.bytesToHuman(-1676528299409L));
        assertEquals("-1.9 GB", StringUtils.bytesToHuman(-1875645323L));
        assertEquals("-1.8 MB", StringUtils.bytesToHuman(-1766906L));
        assertEquals("-1.9 kB", StringUtils.bytesToHuman(-1906L));
        assertEquals("-953 B", StringUtils.bytesToHuman(-953L));
        assertEquals("-1 B", StringUtils.bytesToHuman(-1L));
        assertEquals("0 B", StringUtils.bytesToHuman(0L));
        assertEquals("1 B", StringUtils.bytesToHuman(1L));
        assertEquals("851 B", StringUtils.bytesToHuman(851L));
        assertEquals("1.6 kB", StringUtils.bytesToHuman(1643L));
        assertEquals("2.0 MB", StringUtils.bytesToHuman(1987654L));
        assertEquals("1.7 GB", StringUtils.bytesToHuman(1674324975L));
        assertEquals("2.0 TB", StringUtils.bytesToHuman(1968857463543L));
        assertEquals("1.5 PB", StringUtils.bytesToHuman(1546546463542643L));
        assertEquals("1.4 EB", StringUtils.bytesToHuman(1427965224842685628L));
    }

    @Nested
    @DisplayName("Translate Discord flavoured markdown to Minecraft-formatted text")
    public class DiscordToMinecraft
    {
        @Test
        @DisplayName("Translate emojis from unicode formatted text")
        public void emojis()
        {
            assertEquals(
                "This is a smiley :slightly_smiling: face!",
                StringUtils.discordToMinecraft("This is a smiley \uD83D\uDE42 face!")
            );
        }

        @Test
        @DisplayName("Strip any left over formatting")
        public void stripFormatting()
        {
            assertEquals(
                "This is green text!",
                StringUtils.discordToMinecraft("This is §agreen§r text!")
            );
        }

        @Test
        @DisplayName("Leave plain text unchanged")
        public void plainText()
        {
            assertEquals(
                "Hello, world!",
                StringUtils.discordToMinecraft("Hello, world!")
            );
        }

        @Test
        @DisplayName("Handle empty input")
        public void empty()
        {
            assertEquals("", StringUtils.discordToMinecraft(""));
        }
    }

    @Nested
    @DisplayName("Translate Minecraft-formatted text to Discord flavoured markdown")
    public class MinecraftToDiscord
    {
        @Test
        @DisplayName("Suppress @everyone and @here mentions")
        public void suppressGlobalMentions()
        {
            assertEquals(
                "I can't mention @_everyone_!",
                StringUtils.minecraftToDiscord("I can't mention @everyone!")
            );
            assertEquals(
                "I can't mention everyone @_here_!",
                StringUtils.minecraftToDiscord("I can't mention everyone @here!")
            );
        }

        @Test
        @DisplayName("Strip any left over formatting")
        public void stripFormatting()
        {
            assertEquals(
                "This is green text!",
                StringUtils.minecraftToDiscord("This is §agreen§r text!")
            );
        }

        @Test
        @DisplayName("Translate bold formatting")
        public void bold()
        {
            assertEquals(
                "This is **bold** text!",
                StringUtils.minecraftToDiscord("This is §lbold§r text!")
            );
        }

        @Test
        @DisplayName("Translate underline formatting")
        public void underline()
        {
            assertEquals(
                "This is __underlined__ text!",
                StringUtils.minecraftToDiscord("This is §nunderlined§r text!")
            );
        }

        @Test
        @DisplayName("Translate italic formatting")
        public void italics()
        {
            assertEquals(
                "This is _italic_ text!",
                StringUtils.minecraftToDiscord("This is §oitalic§r text!")
            );
        }

        @Test
        @DisplayName("Translate strikethrough formatting")
        public void strikethrough()
        {
            assertEquals(
                "This is ~~struck~~ text!",
                StringUtils.minecraftToDiscord("This is §mstruck§r text!")
            );
        }

        @Test
        @DisplayName("Obfuscate spoiler formatting")
        public void spoilers()
        {
            assertEquals(
                "This is ||hidden|| text!",
                StringUtils.minecraftToDiscord("This is §khidden§r text!")
            );
        }

        @Test
        @DisplayName("Collapse consecutive line breaks")
        public void collapseLineBreaks()
        {
            assertEquals(
                "line one line two",
                StringUtils.minecraftToDiscord("line one\n\nline two")
            );
        }

        @Test
        @DisplayName("Handle empty input")
        public void empty()
        {
            assertEquals("", StringUtils.minecraftToDiscord(""));
        }
    }

    @Test
    @DisplayName("Derive World Name")
    public void deriveWorldName()
    {
        assertEquals(
            "Overworld",
            StringUtils.deriveWorldName(Identifier.of("minecraft", "overworld"))
        );
        assertEquals(
            "Deep Dark",
            StringUtils.deriveWorldName(Identifier.of("extrautils", "the_deep_dark"))
        );
    }

    @Nested
    @DisplayName("Derive World Name from identifier path")
    public class DeriveWorldNameFromPath
    {
        @Test
        @DisplayName("Capitalise a single word")
        public void singleWord()
        {
            assertEquals("Overworld", StringUtils.deriveWorldName("overworld"));
        }

        @Test
        @DisplayName("Replace underscores and capitalise each word")
        public void underscoreDelimited()
        {
            assertEquals("Deep Dark", StringUtils.deriveWorldName("deep_dark"));
        }

        @Test
        @DisplayName("Strip a leading 'the' keyword")
        public void stripsLeadingThe()
        {
            assertEquals("Nether", StringUtils.deriveWorldName("the_nether"));
            assertEquals("Deep Dark", StringUtils.deriveWorldName("the_deep_dark"));
        }

        @Test
        @DisplayName("Strip only the first 'the ' occurrence")
        public void stripsFirstTheOnly()
        {
            assertEquals("End Of World", StringUtils.deriveWorldName("end_of_the_world"));
        }

        @Test
        @DisplayName("Handle empty input")
        public void empty()
        {
            assertEquals("", StringUtils.deriveWorldName(""));
        }
    }
}
