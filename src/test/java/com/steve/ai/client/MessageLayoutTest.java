package com.steve.ai.client;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageLayoutTest {
    @Test void narrowWrappingPreservesLongTokensUnicodeAndExplicitBlankLines() {
        var splitter = new StringSplitter((codepoint, style) -> 1);
        String text = "á😀long_token_without_spaces\n\nnext";
        var lines = splitter.splitLines(text, 3, Style.EMPTY);
        String joined = lines.stream().map(line -> line.getString()).collect(java.util.stream.Collectors.joining());
        assertEquals(text.replace("\n", ""), joined);
        assertTrue(lines.stream().allMatch(line -> splitter.stringWidth(line) <= 3));
        assertTrue(lines.stream().anyMatch(line -> line.getString().isEmpty()));
    }

    @Test void heightAndWidthUseEveryLineAndReserveSenderSpace() {
        var layout = MessageLayout.of(java.util.List.of("short", "longest line", "end"), String::length, 12);
        assertEquals(22, layout.bubbleWidth());
        assertEquals(46, layout.bubbleHeight());
        assertEquals(63, layout.totalHeight());
    }

    @Test void emptyMessageStillHasOneLineOfHeight() {
        var layout = MessageLayout.of(java.util.List.<String>of(), String::length, 12);
        assertEquals(22, layout.bubbleHeight());
    }

    @Test void spawnInstructionsWrapWithoutLosingTextAndGrowTheBubble() {
        var splitter = new StringSplitter((codepoint, style) -> 1);
        String text = "Use 'spawn <name>' to create a Steve.\nOr /steve spawn <name> in chat.";
        var lines = splitter.splitLines(text, 20, Style.EMPTY);
        var layout = MessageLayout.of(lines, line -> splitter.stringWidth(line), 12);
        assertTrue(lines.size() > 2);
        assertTrue(lines.stream().allMatch(line -> splitter.stringWidth(line) <= 20));
        assertTrue(lines.stream().anyMatch(line -> line.getString().contains("<name>")));
        assertEquals(lines.size() * 12 + 10, layout.bubbleHeight());
        assertEquals(layout.bubbleHeight() + 17, layout.totalHeight());
    }
}
