package io.officestudio.shell;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run on a Slide APK: verifies real window visibility, not a mocked JS bridge. */
@RunWith(AndroidJUnit4.class)
public class SlideWindowInstrumentedTest {
    Instrumentation inst;
    MainActivity activity;
    @Before public void launch() {
        Assume.assumeTrue(BuildConfig.STUDIO.equals("slide"));
        inst = InstrumentationRegistry.getInstrumentation();
        activity = (MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    @After public void close() { if (activity != null) inst.runOnMainSync(() -> activity.finish()); }
    void bars(boolean navigationVisible) {
        long end = SystemClock.uptimeMillis() + 10000;
        do {
            boolean[] matches = {false};
            inst.runOnMainSync(() -> {
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.root);
                matches[0] = insets != null && !insets.isVisible(WindowInsetsCompat.Type.statusBars())
                    && insets.isVisible(WindowInsetsCompat.Type.navigationBars()) == navigationVisible;
            });
            if (matches[0]) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < end);
        fail("Slide status bar must be hidden; navigation visible=" + navigationVisible);
    }
    @Test public void viewerAndEditorKeepStatusRowOnLaunchFocusAndShowExit() {
        bars(true);
        inst.runOnMainSync(() -> activity.setSlideImmersive(true));
        bars(false);
        inst.runOnMainSync(() -> activity.setSlideImmersive(false));
        bars(true);
        inst.runOnMainSync(() -> activity.onWindowFocusChanged(true));
        bars(true);
    }
    @Test public void transientStatusBarCannotRecreateBlankTopRowAndCutoutKeyboardArePreserved() {
        WindowInsetsCompat transientBars = new WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 28, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 24))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(40, 0, 0, 0))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300)).build();
        inst.runOnMainSync(() -> {
            ViewCompat.dispatchApplyWindowInsets(activity.root, transientBars);
            assertEquals(0, activity.root.getPaddingTop());
            assertEquals(0, activity.root.getPaddingLeft());
            assertEquals(300, activity.root.getPaddingBottom());
            assertEquals(0, activity.slideSafeInsets.top);
            assertEquals(40, activity.slideSafeInsets.left);
            assertEquals(24, activity.slideSafeInsets.bottom);
            ViewCompat.requestApplyInsets(activity.root);
        });
    }
}
