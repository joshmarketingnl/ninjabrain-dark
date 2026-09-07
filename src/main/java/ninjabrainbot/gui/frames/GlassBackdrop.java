package ninjabrainbot.gui.frames;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import ninjabrainbot.event.IDisposable;
import ninjabrainbot.gui.style.Translucency;
import ninjabrainbot.io.windows.WindowsCapture;
import ninjabrainbot.util.Logger;

/**
 * Content pane that paints a blurred picture of whatever is behind the window, so the semi
 * transparent panels on top of it read as frosted glass.
 * <p>
 * Windows will not composite its own acrylic backdrop behind a Swing window (both the accent APIs
 * and DwmSetWindowAttribute turn the window opaque instead), so the blur is produced here: grab the
 * screen under the window while the window itself is excluded from capture, scale it down, run a
 * separable gaussian over it and stretch it back up.
 * <p>
 * Two things keep it from looking like a stale screenshot. The capture covers a {@link #MARGIN} of
 * screen around the window, and painting samples that capture at the window's <em>current</em>
 * position, so a window dragged less than the margin since the last grab still shows the right
 * content. And a move wakes the capture thread immediately instead of waiting for the next tick.
 */
public class GlassBackdrop extends JPanel implements IDisposable {

	/** Captured at 1/DOWNSCALE, so the gaussian below is cheap and covers a wide radius. */
	private static final int DOWNSCALE = 4;

	/** Screen pixels grabbed outside the window, absorbing movement between two captures. */
	private static final int MARGIN = 40;

	/** Gaussian radius in downscaled pixels, so roughly 4x this many screen pixels. */
	private static final int BLUR_RADIUS = 5;

	/**
	 * Grabbing the screen costs about 7 ms and a per-pixel translucent window repaints through
	 * UpdateLayeredWindow, so a high steady rate is expensive. Instead the window runs at a calm
	 * rate and briefly sprints while it is being dragged, which is the only time the lag shows.
	 */
	private static final int ACTIVE_REFRESHES_PER_SECOND = 40;
	private static final long ACTIVE_DURATION_MILLIS = 600;

	/** Never re-capture faster than this, so dragging cannot spin the thread. */
	private static final long MIN_CAPTURE_INTERVAL_MILLIS = 12;

	private final Window window;

	private Thread worker;
	private final Object signal = new Object();
	private Robot robot;

	private volatile BufferedImage backdrop;
	private volatile Rectangle backdropBounds;
	private volatile boolean enabled = false;
	private volatile boolean running = false;
	private volatile int refreshesPerSecond = 12;
	private volatile long activeUntil = 0;
	private int[] previousPixels;

	private final float[] kernel = gaussianKernel(BLUR_RADIUS);

	public GlassBackdrop(Window window) {
		this.window = window;
		setOpaque(false);
		window.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent e) {
				requestCapture();
			}

			@Override
			public void componentResized(ComponentEvent e) {
				requestCapture();
			}
		});
	}

	public void setEnabled(boolean enabled, int refreshesPerSecond) {
		this.refreshesPerSecond = Math.max(1, Math.min(60, refreshesPerSecond));
		if (this.enabled == enabled)
			return;
		this.enabled = enabled;
		if (enabled) {
			start();
		} else {
			stop();
			backdrop = null;
			backdropBounds = null;
			repaint();
		}
	}

	/** Wakes the capture thread now, and keeps it sprinting for a moment. */
	private void requestCapture() {
		activeUntil = System.currentTimeMillis() + ACTIVE_DURATION_MILLIS;
		synchronized (signal) {
			signal.notifyAll();
		}
	}

	private void start() {
		if (running || !WindowsCapture.isSupported())
			return;
		if (robot == null) {
			try {
				robot = new Robot();
			} catch (AWTException e) {
				Logger.log("Could not create Robot for the glass backdrop: " + e);
				return;
			}
		}
		running = true;
		worker = new Thread(this::loop, "glass-backdrop");
		worker.setDaemon(true);
		worker.start();
	}

	private void stop() {
		running = false;
		requestCapture();
		worker = null;
	}

	private void loop() {
		while (running) {
			long start = System.currentTimeMillis();
			try {
				capture();
			} catch (Throwable t) {
				Logger.log("Glass backdrop capture failed: " + t);
				running = false;
				return;
			}
			int rate = start < activeUntil ? Math.max(ACTIVE_REFRESHES_PER_SECOND, refreshesPerSecond) : refreshesPerSecond;
			long period = 1000L / rate;
			long elapsed = System.currentTimeMillis() - start;
			long wait = Math.max(MIN_CAPTURE_INTERVAL_MILLIS, period - elapsed);
			synchronized (signal) {
				try {
					signal.wait(wait);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	private void capture() {
		if (!enabled || window == null || !window.isShowing())
			return;
		Rectangle bounds = window.getBounds();
		if (bounds.width <= 0 || bounds.height <= 0)
			return;

		Rectangle region = new Rectangle(bounds.x - MARGIN, bounds.y - MARGIN, bounds.width + 2 * MARGIN, bounds.height + 2 * MARGIN);
		region = clampToScreens(region);
		if (region.width <= 0 || region.height <= 0)
			return;

		BufferedImage raw;
		WindowsCapture.setExcludedFromCapture(window, true);
		try {
			raw = robot.createScreenCapture(region);
		} finally {
			WindowsCapture.setExcludedFromCapture(window, false);
		}

		BufferedImage small = downscale(raw, DOWNSCALE);
		// Nothing behind the window changed and it did not move: no reason to blur or repaint.
		if (isUnchanged(small) && region.equals(backdropBounds))
			return;

		backdrop = blur(small);
		backdropBounds = region;
		SwingUtilities.invokeLater(this::repaint);
	}

	private boolean isUnchanged(BufferedImage small) {
		int[] pixels = ((java.awt.image.DataBufferInt) small.getRaster().getDataBuffer()).getData();
		boolean same = previousPixels != null && java.util.Arrays.equals(previousPixels, pixels);
		previousPixels = pixels;
		return same;
	}

	/** Robot throws if the requested rectangle leaves the virtual desktop. */
	private static Rectangle clampToScreens(Rectangle region) {
		Rectangle virtual = null;
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			for (GraphicsConfiguration configuration : device.getConfigurations()) {
				virtual = virtual == null ? configuration.getBounds() : virtual.union(configuration.getBounds());
			}
		}
		return virtual == null ? region : region.intersection(virtual);
	}

	/** Halving repeatedly gives a much smoother result than one big bilinear step. */
	private static BufferedImage downscale(BufferedImage source, int factor) {
		BufferedImage current = source;
		int width = source.getWidth();
		int height = source.getHeight();
		while (factor > 1) {
			width = Math.max(1, width / 2);
			height = Math.max(1, height / 2);
			BufferedImage next = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = next.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(current, 0, 0, width, height, null);
			g.dispose();
			current = next;
			factor /= 2;
		}
		return current;
	}

	private BufferedImage blur(BufferedImage image) {
		if (image.getWidth() <= kernel.length || image.getHeight() <= kernel.length)
			return image;
		BufferedImage horizontal = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		new ConvolveOp(new Kernel(kernel.length, 1, kernel), ConvolveOp.EDGE_NO_OP, null).filter(image, horizontal);
		BufferedImage vertical = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		new ConvolveOp(new Kernel(1, kernel.length, kernel), ConvolveOp.EDGE_NO_OP, null).filter(horizontal, vertical);
		return vertical;
	}

	private static float[] gaussianKernel(int radius) {
		int size = radius * 2 + 1;
		float[] values = new float[size];
		float sigma = radius / 2f;
		float total = 0;
		for (int i = 0; i < size; i++) {
			int d = i - radius;
			values[i] = (float) Math.exp(-(d * d) / (2 * sigma * sigma));
			total += values[i];
		}
		for (int i = 0; i < size; i++) {
			values[i] /= total;
		}
		return values;
	}

	@Override
	protected void paintComponent(Graphics g) {
		BufferedImage image = backdrop;
		Rectangle region = backdropBounds;
		if (enabled && image != null && region != null && !Translucency.isSuspended() && window.isShowing()) {
			// Sample at where the window is now, not where it was when the screen was grabbed.
			Point location = window.getLocationOnScreen();
			double scale = (double) image.getWidth() / region.width;
			int sx1 = (int) Math.round((location.x - region.x) * scale);
			int sy1 = (int) Math.round((location.y - region.y) * scale);
			int sx2 = sx1 + (int) Math.round(getWidth() * scale);
			int sy2 = sy1 + (int) Math.round(getHeight() * scale);
			sx1 = clamp(sx1, 0, image.getWidth());
			sx2 = clamp(sx2, sx1 + 1, image.getWidth());
			sy1 = clamp(sy1, 0, image.getHeight());
			sy2 = clamp(sy2, sy1 + 1, image.getHeight());

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(image, 0, 0, getWidth(), getHeight(), sx1, sy1, sx2, sy2, null);
			g2.dispose();
		} else if (isOpaque() || Translucency.isSuspended()) {
			Color background = getBackground();
			if (background != null) {
				g.setColor(Translucency.opaque(background));
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		}
		super.paintComponent(g);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public void dispose() {
		stop();
	}

}
