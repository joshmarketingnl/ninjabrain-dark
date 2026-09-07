package ninjabrainbot.gui.style;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;

import javax.swing.SwingUtilities;

/**
 * Holds how see-through the main window currently is. Only components inside that one window are
 * affected, every other window (settings, theme editor, notifications) keeps painting normally.
 */
public class Translucency {

	private static Window translucentWindow = null;
	private static int alpha = 255;

	public static void configure(Window window, int alpha) {
		Translucency.translucentWindow = window;
		Translucency.alpha = Math.max(0, Math.min(255, alpha));
	}

	public static int alpha() {
		return alpha;
	}

	/**
	 * Set while the window is being painted into an offscreen image (the OBS overlay), where the
	 * frosted glass would leak a screenshot of the desktop into the exported png.
	 */
	private static boolean suspended = false;

	public static void suspend(boolean suspended) {
		Translucency.suspended = suspended;
	}

	public static boolean isSuspended() {
		return suspended;
	}

	/** Same color without its alpha channel, for opaque offscreen rendering. */
	public static Color opaque(Color color) {
		return color == null || color.getAlpha() == 255 ? color : new Color(color.getRGB(), false);
	}

	public static boolean appliesTo(Component component) {
		if (suspended || alpha >= 255 || translucentWindow == null || component == null)
			return false;
		return SwingUtilities.getWindowAncestor(component) == translucentWindow;
	}

	public static Color apply(Color color, Component component) {
		if (color == null || !appliesTo(component))
			return color;
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

}
