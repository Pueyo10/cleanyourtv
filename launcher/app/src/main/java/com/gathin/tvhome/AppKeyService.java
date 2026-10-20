package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Opens an app from a remote button that has nothing left to do, such as the PatchWall or Prime Video key once
 * those apps are disabled. The buttons come from key_apps in config.xml (or a theme's config.json), one
 * "KEYCODE_NAME=package" or "123=package" per entry. Only those keys are consumed, and only while their app can be
 * launched; every other key passes through untouched.
 *
 * To find out what a button sends: setprop log.tag.TvHomeKeys DEBUG, press it, then logcat -s TvHomeKeys.
 * Nothing is logged otherwise. Opt-in: APP_KEY_SERVICE=1 build-and-install.sh (or -AppKeyService on Windows).
 */
public class AppKeyService extends AccessibilityService {

    private static final String TAG = "TvHomeKeys";

    private final SparseArray<String> apps = new SparseArray<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        apps.clear();
        for (String entry : Config.load(this).keyApps) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            int code = keyCode(entry.substring(0, eq).trim());
            String pkg = entry.substring(eq + 1).trim();
            if (code != KeyEvent.KEYCODE_UNKNOWN && !pkg.isEmpty()) apps.put(code, pkg);
            else Log.w(TAG, "ignoring key_apps entry " + entry);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, KeyEvent.keyCodeToString(code) + " (" + code + ") scan=" + event.getScanCode());
        }
        String pkg = apps.get(code);
        if (pkg == null) return false;
        Intent launch = launchIntent(pkg);
        if (launch == null) return false;   // app missing or disabled: leave the key to the system
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            try {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return true;
    }

    private Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    /** "KEYCODE_PROG_RED" or a plain number such as "183"; KEYCODE_UNKNOWN when it is neither. */
    private static int keyCode(String name) {
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return KeyEvent.keyCodeFromString(name.startsWith("KEYCODE_") ? name : "KEYCODE_" + name);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
