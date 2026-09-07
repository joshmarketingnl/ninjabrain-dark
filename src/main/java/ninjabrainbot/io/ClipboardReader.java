package ninjabrainbot.io;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import ninjabrainbot.event.IObservable;
import ninjabrainbot.event.ObservableField;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;

public class ClipboardReader implements IClipboardProvider, Runnable {

	private final NinjabrainBotPreferences preferences;

	final Clipboard clipboard;
	String lastClipboardString;

	private final AtomicBoolean forceReadLater;

	final ObservableField<String> clipboardString;

	public ClipboardReader(NinjabrainBotPreferences preferences) {
		this.preferences = preferences;
		clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboardString = new ObservableField<>(null, true);
		// Whatever is on the clipboard right now is left over from before this app started, most
		// likely an F3+C from an earlier session. Upstream starts with an empty value and so takes
		// it for a fresh measurement, which silently poisons the first triangulation after every
		// launch. Only changes made from here on count as a new throw.
		lastClipboardString = readClipboardQuietly();
		forceReadLater = new AtomicBoolean(false);
	}

	private String readClipboardQuietly() {
		try {
			String contents = (String) clipboard.getData(DataFlavor.stringFlavor);
			return contents == null ? "" : contents;
		} catch (UnsupportedFlavorException | IOException | RuntimeException ignored) {
			return "";
		}
	}

	public IObservable<String> clipboardText() {
		return clipboardString;
	}

	public void forceRead() {
		forceReadLater.set(true);
	}

	@Override
	public void run() {
		while (true) {
			boolean read = !preferences.altClipboardReader.get();
			if (preferences.altClipboardReader.get() && forceReadLater.get()) {
				read = true;
				// Sleep 0.1 seconds to let the game update the clipboard
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			String clipboardString = null;
			try {
				if (read) {
					clipboardString = ((String) clipboard.getData(DataFlavor.stringFlavor));
					if (clipboardString.length() > 1000)
						clipboardString = clipboardString.substring(0, 1000);
				}
			} catch (UnsupportedFlavorException | IllegalStateException | IOException ignored) {
			}
			if (clipboardString != null && !lastClipboardString.equals(clipboardString)) {
				onClipboardUpdated(clipboardString);
				lastClipboardString = clipboardString;
			}
			// Sleep 0.1 seconds
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void onClipboardUpdated(String clipboard) {
		clipboardString.set(clipboard);
	}

}
