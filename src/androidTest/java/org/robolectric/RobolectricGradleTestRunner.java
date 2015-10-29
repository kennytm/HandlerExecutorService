package org.robolectric;

import org.junit.internal.builders.IgnoredClassRunner;

public class RobolectricGradleTestRunner extends IgnoredClassRunner {
    public RobolectricGradleTestRunner(final Class<?> testClass) {
        super(testClass);
    }
}
