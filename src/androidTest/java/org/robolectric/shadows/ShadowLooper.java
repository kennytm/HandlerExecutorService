package org.robolectric.shadows;

public class ShadowLooper {
    public void idle(final long millisec) {
    }

    public boolean hasQuit() {
        return true;
    }
}
