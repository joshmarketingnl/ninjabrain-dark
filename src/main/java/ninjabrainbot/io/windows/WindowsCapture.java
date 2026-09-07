package ninjabrainbot.io.windows;

import java.awt.Window;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import ninjabrainbot.util.Logger;

/**
 * Lets a window hide itself from screen capture for a moment. Used by the frosted glass backdrop so
 * that the screenshot it takes of what is behind the window does not contain the window itself.
 * <p>
 * The window stays fully visible on the monitor while it is excluded; only capture APIs skip it.
 */
public class WindowsCapture {

	private static final int WDA_NONE = 0x00;
	private static final int WDA_EXCLUDEFROMCAPTURE = 0x11;

	private static boolean unavailable = false;

	public interface User32Ext extends StdCallLibrary {
		User32Ext INSTANCE = Native.load("user32", User32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean SetWindowDisplayAffinity(HWND hwnd, int affinity);
	}

	public static boolean isSupported() {
		return Platform.isWindows() && !unavailable;
	}

	public static void setExcludedFromCapture(Window window, boolean excluded) {
		if (!isSupported() || window == null || !window.isDisplayable())
			return;
		try {
			HWND hwnd = new HWND(Native.getWindowPointer(window));
			User32Ext.INSTANCE.SetWindowDisplayAffinity(hwnd, excluded ? WDA_EXCLUDEFROMCAPTURE : WDA_NONE);
		} catch (Throwable t) {
			unavailable = true;
			Logger.log("Capture exclusion unavailable: " + t);
		}
	}

}
