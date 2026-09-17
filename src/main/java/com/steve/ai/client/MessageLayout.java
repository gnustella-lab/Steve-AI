package com.steve.ai.client;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** One source of geometry for measuring, drawing and scrolling wrapped bubbles. */
record MessageLayout<T>(List<T> lines, int bubbleWidth, int bubbleHeight) {
    static <T> MessageLayout<T> of(List<T> lines, ToDoubleFunction<T> width, int lineHeight) {
        return new MessageLayout<>(List.copyOf(lines),
            (int) Math.ceil(lines.stream().mapToDouble(width).max().orElse(0)) + 10,
            Math.max(1, lines.size()) * lineHeight + 10);
    }

    int totalHeight() {
        return bubbleHeight + 17; // sender name and gap
    }
}
