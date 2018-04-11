package jkumensa.bot;

import org.junit.Assert;
import org.junit.Test;

public class BreadcrumbNavigatorTest {

    @Test
    public void a() {
        BreadcrumbNavigator bn = BreadcrumbNavigator.fromString("a:b");
        Assert.assertEquals(2, bn.depth());
        Assert.assertEquals("a", bn.get(0));
        Assert.assertEquals("b", bn.get(1));
    }
}
