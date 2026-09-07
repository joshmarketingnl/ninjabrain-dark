package ninjabrainbot.gui.frames;

import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.net.URL;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;

import ninjabrainbot.Main;
import ninjabrainbot.event.DisposeHandler;
import ninjabrainbot.event.IDisposable;
import ninjabrainbot.gui.buttons.FlatButton;
import ninjabrainbot.gui.buttons.TitleBarButton;
import ninjabrainbot.gui.components.RefreshWindowOnMonitorChangeListener;
import ninjabrainbot.gui.components.labels.ThemedLabel;
import ninjabrainbot.gui.components.panels.TitleBarPanel;
import ninjabrainbot.gui.style.SizePreference;
import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.gui.style.Translucency;
import ninjabrainbot.gui.style.theme.WrappedColor;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;

public abstract class ThemedFrame extends JFrame implements IDisposable {

	protected final TitleBarPanel titlebarPanel;
	protected final ThemedLabel titletextLabel;

	final WrappedColor bgCol;

	protected final DisposeHandler disposeHandler = new DisposeHandler();
	private final StyleManager styleManager;

	/** Only the main window gets a frosted glass content pane, other windows stay plain. */
	protected final GlassBackdrop glassBackdrop;

	public ThemedFrame(StyleManager styleManager, NinjabrainBotPreferences preferences, String title) {
		this(styleManager, preferences, title, false);
	}

	public ThemedFrame(StyleManager styleManager, NinjabrainBotPreferences preferences, String title, boolean useGlassBackdrop) {
		super(title);
		this.styleManager = styleManager;
		styleManager.registerThemedFrame(this);
		setUndecorated(true); // Remove borders
		setAlwaysOnTop(preferences.alwaysOnTop.get()); // Always focused
		glassBackdrop = useGlassBackdrop ? new GlassBackdrop(this) : null;
		if (glassBackdrop != null)
			setContentPane(glassBackdrop);
		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		titlebarPanel = new TitleBarPanel(styleManager, this);
		add(titlebarPanel);
		titletextLabel = new ThemedLabel(styleManager, title, true) {
			@Override
			public int getTextSize(SizePreference p) {
				return p.TEXT_SIZE_TITLE_LARGE;
			}
		};
		titletextLabel.setForegroundColor(styleManager.currentTheme.TEXT_COLOR_TITLE);
		titlebarPanel.add(titletextLabel);
		titlebarPanel.addButton(createExitButton(styleManager));

		bgCol = styleManager.currentTheme.COLOR_NEUTRAL;

		addComponentListener(new RefreshWindowOnMonitorChangeListener(this));
	}

	private FlatButton createExitButton(StyleManager styleManager) {
		URL iconURL = Main.class.getResource("/exit_icon.png");
		ImageIcon img = new ImageIcon(iconURL);
		FlatButton button = new TitleBarButton(styleManager, img);
		button.setHoverColor(styleManager.currentTheme.COLOR_EXIT_BUTTON_HOVER);
		button.addActionListener(__ -> onExitButtonClicked());
		return button;
	}

	protected abstract void onExitButtonClicked();

	public TitleBarPanel getTitleBar() {
		return titlebarPanel;
	}

	public void updateBounds(StyleManager styleManager) {
		int titlebarHeight = titlebarPanel.getPreferredSize().height;
		titletextLabel.setBounds((titlebarHeight - styleManager.size.TEXT_SIZE_TITLE_LARGE) / 2, 0, titletextLabel.getPreferredSize().width, titlebarHeight);
	}

	public void updateFontsAndColors() {
		Color bg = bgCol.color();
		boolean translucent = Translucency.appliesTo(getContentPane());
		if (getContentPane() instanceof JComponent)
			((JComponent) getContentPane()).setOpaque(!translucent);
		getContentPane().setBackground(bg);
		// A see-through window needs a per-pixel transparent frame background, the panels on top
		// supply the actual tint.
		setBackground(translucent ? new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 0) : bg);
	}

	public void checkIfOffScreen() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		for (GraphicsDevice gd : ge.getScreenDevices()) {
			if (gd.getDefaultConfiguration().getBounds().contains(getBounds())) {
				return;
			}
		}
		setLocation(100, 100);
	}

	@Override
	public void dispose() {
		super.dispose();
		if (glassBackdrop != null)
			glassBackdrop.dispose();
		disposeHandler.dispose();
	}

}
