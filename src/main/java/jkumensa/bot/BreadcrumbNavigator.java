package jkumensa.bot;

import java.util.Arrays;
import java.util.stream.Collectors;

public class BreadcrumbNavigator {
    public static BreadcrumbNavigator fromString(String s) {
        return new BreadcrumbNavigator(s);
    }
    private final String here;
    private final String[] split;

    private BreadcrumbNavigator(String here) {
        this.here = here;
        this.split = here.split(":");
    }

    public String navigateDownTo(String s) {
        return here + ":" + s;
    }

    public String navigateToParent() {
        return Arrays.stream(split).limit(split.length - 1).collect(Collectors.joining(":"));
    }

    public String getCurrent() {
        return split[split.length - 1];
    }

    public String get(int index) {
        return split[index];
    }

    public int depth() {
        return split.length;
    }
}
