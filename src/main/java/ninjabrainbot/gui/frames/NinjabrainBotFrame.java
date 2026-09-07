package ninjabrainbot.gui.frames;

import java.awt.GraphicsDevice;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.GraphicsEnvironment;
import java.awt.geom.RoundRectangle2D;
import java.awt.Image;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Objects;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import com.sun.jna.Platform;
import ninjabrainbot.Main;
import ninjabrainbot.event.IDisposable;
import ninjabrainbot.gui.buttons.FlatButton;
import ninjabrainbot.gui.buttons.NotificationsButton;
import ninjabrainbot.gui.buttons.TitleBarButton;
import ninjabrainbot.gui.components.labels.ThemedIcon;
import ninjabrainbot.gui.components.labels.ThemedLabel;
import ninjabrainbot.gui.mainwindow.boateye.BoatIcon;
import ninjabrainbot.gui.mainwindow.boateye.Mod360Icon;
import ninjabrainbot.gui.mainwindow.eyethrows.EnderEyePanel;
import ninjabrainbot.gui.mainwindow.triangulation.FocusTriangulationPanel;
import ninjabrainbot.gui.mainwindow.information.InformationListPanel;
import ninjabrainbot.gui.mainwindow.main.MainButtonPanel;
import ninjabrainbot.gui.mainwindow.main.MainTextArea;
import ninjabrainbot.gui.style.SizePreference;
import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.gui.style.Translucency;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.io.preferences.enums.FocusLayoutType;
import ninjabrainbot.io.preferences.enums.MainViewType;
import ninjabrainbot.io.updatechecker.IUpdateChecker;
import ninjabrainbot.model.datastate.IDataState;
import ninjabrainbot.model.information.InformationMessageList;
import ninjabrainbot.model.input.IButtonInputHandler;
import ninjabrainbot.util.I18n;
import ninjabrainbot.util.Profiler;

public class NinjabrainBotFrame extends ThemedFrame implements IDisposable {

	private final NinjabrainBotPreferences preferences;

	/** The eye throw table below still needs some room, however narrow the focus layout gets. */
	private static final int MIN_FOCUS_WIDTH = 280;

	/** Below this window width the version label next to the title is hidden. */
	private static final int VERSION_MIN_WIDTH = 360;

	private ThemedLabel versionTextLabel;
	private JButton settingsButton;
	private JLabel lockIcon;

	private MainTextArea mainTextArea;
	private InformationListPanel informationTextPanel;
	private EnderEyePanel enderEyePanel;

	private static final String TITLE_TEXT = Main.APP_NAME + " ";
	private static final String VERSION_TEXT = "v" + Main.VERSION;

	private final StyleManager styleManager;

	public NinjabrainBotFrame(StyleManager styleManager, NinjabrainBotPreferences preferences, IUpdateChecker updateChecker, IDataState dataState, IButtonInputHandler buttonInputHandler, InformationMessageList informationMessageList) {
		super(styleManager, preferences, TITLE_TEXT, true);
		this.preferences = preferences;
		this.styleManager = styleManager;
		Profiler.start("NinjabrainBotFrame");
		setLocation(preferences.windowX.get(), preferences.windowY.get()); // Set window position
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		updateWindowTranslucency(false);
		setAppIcon();

		createTitleBar(styleManager, dataState, updateChecker);
		createComponents(styleManager, dataState, buttonInputHandler, informationMessageList);
		setupSubscriptions(styleManager, dataState);
		Profiler.stop();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		SwingUtilities.invokeLater(() -> updateWindowTranslucency(true));
	}

	@Override
	public void updateBounds(StyleManager styleManager) {
		super.updateBounds(styleManager);
		// A narrow focus layout has no room for the version next to the title.
		versionTextLabel.setVisible(styleManager.size.WIDTH + getExtraWidth(styleManager) >= VERSION_MIN_WIDTH);
		int titlewidth = styleManager.getTextWidth(TITLE_TEXT, styleManager.fontSize(styleManager.size.TEXT_SIZE_TITLE_LARGE, false));
		int titlebarHeight = titlebarPanel.getPreferredSize().height;
		versionTextLabel.setBounds(titlewidth + (titlebarHeight - styleManager.size.TEXT_SIZE_TITLE_SMALL) / 2, (styleManager.size.TEXT_SIZE_TITLE_LARGE - styleManager.size.TEXT_SIZE_TITLE_SMALL) / 2, 70, titlebarHeight);
		int versionwidth = styleManager.getTextWidth(VERSION_TEXT, styleManager.fontSize(styleManager.size.TEXT_SIZE_TITLE_SMALL, false));
		lockIcon.setBounds(titlewidth + versionwidth + (titlebarHeight - styleManager.size.TEXT_SIZE_TITLE_SMALL) / 2, 0, titlebarHeight, titlebarHeight);
		// Frame size
		int extraWidth = getExtraWidth(styleManager);
		setSize(styleManager.size.WIDTH + extraWidth, getPreferredSize().height);
		setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), styleManager.size.WINDOW_ROUNDING, styleManager.size.WINDOW_ROUNDING));
	}

	public AbstractButton getSettingsButton() {
		return settingsButton;
	}

	public boolean isIdle() {
		return mainTextArea.isIdle();
	}

	private void setupSubscriptions(StyleManager styleManager, IDataState dataState) {
		// Settings
		disposeHandler.add(preferences.translucent.whenModified().subscribeEDT(__ -> updateWindowTranslucency()));
		disposeHandler.add(preferences.blurredBackground.whenModified().subscribeEDT(__ -> updateWindowTranslucency()));
		disposeHandler.add(preferences.windowOpacity.whenModified().subscribeEDT(__ -> updateWindowTranslucency()));
		disposeHandler.add(preferences.blurRefreshRate.whenModified().subscribeEDT(__ -> updateWindowTranslucency()));
		disposeHandler.add(preferences.focusTextScale.whenModified().subscribeEDT(__ -> refreshLayout()));
		disposeHandler.add(preferences.focusLayout.whenModified().subscribeEDT(__ -> refreshLayout()));
		disposeHandler.add(preferences.view.whenModified().subscribeEDT(__ -> refreshLayout()));
		disposeHandler.add(preferences.alwaysOnTop.whenModified().subscribeEDT(this::setAlwaysOnTop));
		disposeHandler.add(preferences.hotkeyMinimize.whenTriggered().subscribeEDT(__ -> toggleMinimized()));
		// Components bounds changed
		disposeHandler.add(mainTextArea.whenModified().subscribeEDT(__ -> updateSize(styleManager)));
		disposeHandler.add(informationTextPanel.whenModified().subscribeEDT(__ -> updateSize(styleManager)));
		disposeHandler.add(enderEyePanel.whenModified().subscribeEDT(__ -> updateSize(styleManager)));
		disposeHandler.add(dataState.locked().subscribeEDT(b -> lockIcon.setVisible(b)));
	}

	private void createTitleBar(StyleManager styleManager, IDataState dataState, IUpdateChecker updateChecker) {
		versionTextLabel = new ThemedLabel(styleManager, VERSION_TEXT) {
			@Override
			public int getTextSize(SizePreference p) {
				return p.TEXT_SIZE_TITLE_SMALL;
			}
		};
		versionTextLabel.setForegroundColor(styleManager.currentTheme.TEXT_COLOR_WEAK);
		lockIcon = new ThemedIcon(styleManager, new ImageIcon(Objects.requireNonNull(Main.class.getResource("/lock_icon.png"))));
		lockIcon.setVisible(dataState.locked().get());
		titlebarPanel.add(versionTextLabel);
		titlebarPanel.add(lockIcon);
		titlebarPanel.addButton(createMinimizeButton(styleManager));
		settingsButton = createSettingsButton(styleManager);
		titlebarPanel.addButton(settingsButton);
		NotificationsButton notificationsButton = new NotificationsButton(styleManager, this, preferences, updateChecker);
		titlebarPanel.addButton(notificationsButton);
		titlebarPanel.addButton(createLayoutButton(styleManager));
		titlebarPanel.addButton(new BoatIcon(styleManager, dataState.boatDataState().boatState(), preferences, disposeHandler));
		titlebarPanel.addButton(new Mod360Icon(styleManager, dataState.boatDataState(), preferences, disposeHandler));
	}

	@Override
	protected void onExitButtonClicked() {
		System.exit(0);
	}

	private void createComponents(StyleManager styleManager, IDataState dataState, IButtonInputHandler buttonInputHandler, InformationMessageList informationMessageList) {
		// Main text
		mainTextArea = new MainTextArea(styleManager, buttonInputHandler, preferences, dataState);
		add(mainTextArea);
		// Info and warnings
		informationTextPanel = new InformationListPanel(styleManager, informationMessageList);
		add(informationTextPanel);
		// "Throws" text + buttons
		MainButtonPanel mainButtonPanel = new MainButtonPanel(styleManager, buttonInputHandler);
		add(mainButtonPanel);
		// Throw panels
		enderEyePanel = new EnderEyePanel(styleManager, preferences, dataState, buttonInputHandler);
		add(enderEyePanel);
	}

	@Override
	public void validate() {
		super.validate();
		updateSize(styleManager);
	}

	private FlatButton createMinimizeButton(StyleManager styleManager) {
		URL iconURL = Main.class.getResource("/minimize_icon.png");
		ImageIcon img = new ImageIcon(iconURL);
		FlatButton button = new TitleBarButton(styleManager, img);
		button.addActionListener(p -> setState(JFrame.ICONIFIED));
		return button;
	}

	/** Cycles the focus view between its wide, two row and narrow arrangements. */
	private FlatButton createLayoutButton(StyleManager styleManager) {
		URL iconURL = Main.class.getResource("/layout_icon.png");
		FlatButton button = new TitleBarButton(styleManager, new ImageIcon(Objects.requireNonNull(iconURL)));
		button.setToolTipText(I18n.get("settings.focus_layout"));
		button.addActionListener(__ -> cycleFocusLayout());
		return button;
	}

	private void cycleFocusLayout() {
		if (!preferences.view.get().equals(MainViewType.FOCUS))
			preferences.view.set(MainViewType.FOCUS);
		FocusLayoutType[] layouts = FocusLayoutType.values();
		int next = (preferences.focusLayout.get().ordinal() + 1) % layouts.length;
		preferences.focusLayout.set(layouts[next]);
	}

	private FlatButton createSettingsButton(StyleManager styleManager) {
		URL iconURL = Main.class.getResource("/settings_icon.png");
		ImageIcon img = new ImageIcon(iconURL);
		return new TitleBarButton(styleManager, img);
	}

	private void toggleMinimized() {
		int state = getState();
		if (state == JFrame.ICONIFIED) {
			setState(JFrame.NORMAL);
		} else {
			setState(JFrame.ICONIFIED);
		}
	}

	/**
	 * The focus view scales its single result row up, so the window has to grow with it, otherwise
	 * the large coordinates get cut off.
	 */
	private int getExtraWidth(StyleManager styleManager) {
		MainViewType view = preferences.view.get();
		// The focus view keeps the width its layout will eventually need, also while idle. Growing
		// only once the first measurement lands makes the window jump out from under the player.
		if (view.equals(MainViewType.FOCUS))
			return Math.max(MIN_FOCUS_WIDTH, FocusTriangulationPanel.preferredWindowWidth(styleManager, preferences)) - styleManager.size.WIDTH;
		if (preferences.showAngleUpdates.get() && view.equals(MainViewType.DETAILED))
			return styleManager.size.ANGLE_COLUMN_WIDTH;
		return 0;
	}

	private void refreshLayout() {
		styleManager.updateFontsAndColors();
		updateSize(styleManager);
	}

	private void updateWindowTranslucency() {
		updateWindowTranslucency(true);
	}

	private void updateWindowTranslucency(boolean refreshStyles) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		boolean supported = gd.isWindowTranslucencySupported(WindowTranslucency.PERPIXEL_TRANSLUCENT);
		boolean translucent = supported && preferences.translucent.get();

		float opacity = Math.max(0.2f, Math.min(1f, preferences.windowOpacity.get() / 100f));
		Translucency.configure(this, translucent ? Math.round(255 * opacity) : 255);

		if (glassBackdrop != null)
			glassBackdrop.setEnabled(translucent && preferences.blurredBackground.get(), Math.round(preferences.blurRefreshRate.get()));

		if (refreshStyles && styleManager != null) {
			styleManager.updateFontsAndColors();
			repaint();
		}
	}

	private void updateSize(StyleManager styleManager) {
		int extraWidth = getExtraWidth(styleManager);
		setSize(styleManager.size.WIDTH + extraWidth, getPreferredSize().height);
		setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), styleManager.size.WINDOW_ROUNDING, styleManager.size.WINDOW_ROUNDING));
	}

	private void setAppIcon() {
		URL iconURL = Main.class.getResource(Main.ICON);
		ImageIcon img = new ImageIcon(Objects.requireNonNull(iconURL));
		setIconImage(img.getImage());

        if (Platform.isMac()) {
            setIconOnMac(img.getImage());
        }
	}

    private void setIconOnMac(Image image) {
        try {
            // Java 9+ way to set icon
            // Taskbar.getTaskbar().setIconImage(image);
            Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
            Object taskbar = taskbarClass.getMethod("getTaskbar").invoke(null);
            Method setIconImage = taskbarClass.getMethod("setIconImage", Image.class);
            setIconImage.invoke(taskbar, image);
            return;
        } catch (Exception e) {
			e.printStackTrace();
        }
        try {
            // Java 8 way to set icon
            // Application.getApplication().setDockIconImage(image);
            Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
            Object application = applicationClass.getMethod("getApplication").invoke(null);
            Method setDockIconImage = applicationClass.getMethod("setDockIconImage", Image.class);
            setDockIconImage.invoke(application, image);
        } catch (Exception e) {
			e.printStackTrace();
        }
    }

	@Override
	public void dispose() {
		super.dispose();
		mainTextArea.dispose();
		enderEyePanel.dispose();
	}

}
