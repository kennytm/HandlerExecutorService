package org.robolectric.annotation;

public @interface Config {
    Class<?> constants();
    int sdk();
}
