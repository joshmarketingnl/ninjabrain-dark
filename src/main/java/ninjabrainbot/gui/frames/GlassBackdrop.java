package ninjabrainbot.gui.frames;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
 * Windows will not composite its own acrylic backdrop behind a Swing window (the accent APIs turn
 * the window opaque instead), so the blur is produced here: grab the screen under the window while
 * the window itself is excluded from capture, scale it far down, and let bilinear filtering smear
 * it back up to size.
 */
public class GlassBackdrop extends JPanel implements IDisposable {

	/** Capturing at 1/DOWNSCALE and stretching back up is what produces the blur. */
	private static final int DOWNSCALE = 10;

	private final Window window;

	private ScheduledExecutorService executor;
	private ScheduledFuture<?> task;
	private Robot robot;

	private volatile BufferedImage backdrop;
	private volatile boolean enabled = false;
	private int refreshesPerSecond = 8;

	public GlassBackdrop(Window window) {
		this.window = window;
		setOpaque(false);
	}

	public void setEnabled(boolean enabled, int refreshesPerSecond) {
		this.refreshesPerSecond = Math.max(1, Math.min(30, refreshesPerSecond));
		if (this.enabled == enabled) {
			if (enabled)
				restart();
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			restart();
		} else {
			stop();
			backdrop = null;
			repaint();
		}
	}

	public boolean isBackdropAvailable() {
		return WindowsCapture.isSupported();
	}

	private void restart() {
		stop();
		if (!WindowsCapture.isSupported())
			return;
		if (robot == null) {
			try {
				robot = new Robot();
			} catch (AWTException e) {
				Logger.log("Could not create Robot for the glass backdrop: " + e);
				return;
			}
		}
		if (executor == null)
			executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "glass-backdrop");
				thread.setDaemon(true);
				return thread;
			});
		long period = Math.max(33, 1000 / refreshesPerSecond);
		task = executor.scheduleWithFixedDelay(this::captureSafely, 120, period, TimeUnit.MILLISECONDS);
	}

	private void stop() {
		if (task != null) {
			task.cancel(false);
			task = null;
		}
	}

	private void captureSafely() {
		try {
			capture();
		} catch (Throwable t) {
			Logger.log("Glass backdrop capture failed: " + t);
			stop();
		}
	}

	private void capture() {
		if (!enabled || window == null || !window.isShowing())
			return;
		Rectangle bounds = window.getBounds();
		if (bounds.width <= 0 || bounds.height <= 0)
			return;

		BufferedImage raw;
		WindowsCapture.setExcludedFromCapture(window, true);
		try {
			raw = robot.createScreenCapture(bounds);
		} finally {
			WindowsCapture.setExcludedFromCapture(window, false);
		}

		backdrop = downscale(raw);
		SwingUtilities.invokeLater(this::repaint);
	}

	private static BufferedImage downscale(BufferedImage source) {
		int width = Math.max(2, source.getWidth() / DOWNSCALE);
		int height = Math.max(2, source.getHeight() / DOWNSCALE);
		BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = small.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return small;
	}

	@Override
	protected void paintComponent(Graphics g) {
		BufferedImage image = backdrop;
		if (enabled && image != null && !Translucency.isSuspended()) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
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

	@Override
	public void dispose() {
		stop();
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

}
