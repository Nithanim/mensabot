package jkumensa.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CleanerTest {

    public CleanerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testSplitFuckedUpFreeformUserInputTagP() throws IOException {
        Document doc = getDocument("2017-10-11");
        Element ele = doc.select("#speiseplan.desktop #week #days .day:nth-of-type(1) .day-content > div:nth-of-type(2) .category-content").first();
        System.out.println("html:");
        System.out.println(ele);

        System.out.println("Representation:");
        List<List<Node>> a = Cleaner.splitFuckedUpFreeformUserInput(ele.children());
        a.forEach(e -> {
            System.out.println("-");
            e.forEach(f -> System.out.println("\t" + f));
        });

    }

    @Test
    public void testSplitFuckedUpFreeformUserInputTagBr() throws IOException {
        Document doc = getDocument("2017-10-11");
        Element ele = doc.select("#speiseplan.desktop #week #days .day:nth-of-type(1) .day-content > div:nth-of-type(1) .category-content").first();
        System.out.println("html:");
        System.out.println(ele);

        System.out.println("Representation:");
        List<List<Node>> a = Cleaner.splitFuckedUpFreeformUserInput(ele.children());
        a.forEach(e -> {
            System.out.println("-");
            e.forEach(f -> System.out.println("\t" + f));
        });
    }
    
    @Test
    public void testSplitFuckedUpFreeformUserInputTag3() throws IOException {
        Document doc = getDocument("2017-10-11");
        Element ele = doc.select("#speiseplan.desktop #week #days .day:nth-of-type(2) .day-content > div:nth-of-type(1) .category-content").first();
        System.out.println("html:");
        System.out.println(ele);

        System.out.println("Representation:");
        List<List<Node>> a = Cleaner.splitFuckedUpFreeformUserInput(ele.children());
        a.forEach(e -> {
            System.out.println("-");
            e.forEach(f -> System.out.println("\t" + f));
        });
    }
    
     @Test
    public void testSplitFuckedUpFreeformUserInputTag4() throws IOException {
        Document doc = getDocument("2017-10-11");
        Element ele = doc.select("#speiseplan.desktop #week #days .day:nth-of-type(2) .day-content > div:nth-of-type(2) .category-content").first();
        System.out.println("html:");
        System.out.println(ele);

        System.out.println("Representation:");
        List<List<Node>> a = Cleaner.splitFuckedUpFreeformUserInput(ele.children());
        a.forEach(e -> {
            System.out.println("-");
            e.forEach(f -> System.out.println("\t" + f));
        });
    }

    private Document getDocument(String date) throws IOException {
        InputStream s = getClass().getResourceAsStream("/html/mensa_" + date + ".html");
        return Jsoup.parse(s, "UTF-8", "http://menu.mensen.at/index/index/locid/1");
    }
}
