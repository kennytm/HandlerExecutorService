package org.robolectric;

import android.os.Looper;

import org.robolectric.shadows.ShadowLooper;

public class Shadows {
    public static ShadowLooper shadowOf(final Looper looper) {
        return null;
    }
}
