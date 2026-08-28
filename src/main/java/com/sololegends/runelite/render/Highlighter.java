package com.sololegends.runelite.render;

import java.awt.*;

/**
 * Shared drawing helper so every highlight in the plugin honours the same
 * user configured outline thickness and fill opacity.
 */
public class Highlighter {

	public static final int MIN_THICKNESS = 1;
	public static final int MAX_THICKNESS = 8;

	// Strokes are re-created every frame otherwise, cache the handful we can use
	private static final Stroke[] STROKES = new Stroke[MAX_THICKNESS + 1];

	static {
		for (int i = MIN_THICKNESS; i <= MAX_THICKNESS; i++) {
			STROKES[i] = new BasicStroke(i);
		}
	}

	private Highlighter() {
	}

	public static Stroke stroke(int thickness) {
		return STROKES[Math.max(MIN_THICKNESS, Math.min(MAX_THICKNESS, thickness))];
	}

	/**
	 * Applies the configured color at the given opacity, the color's own alpha is
	 * ignored so the fill can be tuned independently of the outline.
	 */
	public static Color fill(Color color, int opacity) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, opacity)));
	}

	/**
	 * Fills then outlines the shape, a fill opacity of 0 skips the fill entirely.
	 */
	public static void render(Graphics2D graphics, Shape shape, Color color, int thickness, int fill_opacity) {
		if (shape == null || color == null) {
			return;
		}
		if (fill_opacity > 0) {
			graphics.setColor(fill(color, fill_opacity));
			graphics.fill(shape);
		}
		final Stroke original = graphics.getStroke();
		graphics.setColor(color);
		graphics.setStroke(stroke(thickness));
		graphics.draw(shape);
		graphics.setStroke(original);
	}
}
