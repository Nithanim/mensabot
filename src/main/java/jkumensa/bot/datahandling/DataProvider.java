package jkumensa.bot.datahandling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import jkumensa.api.Mensa;
import jkumensa.api.MensaApiResult;
import jkumensa.api.MensaCategory;
import jkumensa.api.data.MensaApiResultData;
import jkumensa.parser.data.MensaDayData;
import jkumensa.parser.i.MensaDay;
import jkumensa.parser.jku.JkuMensaParser;
import jkumensa.parser.khg.KhgMensaParser;
import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataProvider {
    public static final Logger logger = LoggerFactory.getLogger(DataProvider.class);

    @Getter
    private volatile MensaApiResult mensaData;

    private final Scheduler scheduler;
    private Runnable onUpdate;

    public DataProvider() {
        this.scheduler = new Scheduler(this::update);
    }

    public void start() {
        scheduler.start();
    }

    public void stop() {
        scheduler.stop();
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public void update() {
        LocalDate relevantDay = getRelevantDay();
        Map<Mensa, Future<List<? extends MensaDay>>> fs = fetchData();

        EnumMap<Mensa, List<? extends MensaCategory>> newData = new EnumMap<>(Mensa.class);
        for (Mensa m : fs.keySet()) {
            try {
                Future<List<? extends MensaDay>> f = fs.get(m);
                MensaDay day = filterByDate(f.get(), relevantDay);
                if (day == null) {
                    logger.info("No data was found for relevant day for {}, skipping", m);
                }
                newData.put(m, day.getCategories());
            } catch (Exception ex) {
                logger.error("Unable to update mensa {}", m, ex);
            }
        }
        mensaData = new MensaApiResultData(Date.from(relevantDay.atStartOfDay(ZoneId.of("Europe/Vienna")).toInstant()), newData);

        triggerOnUpdateListeners();
    }

    private void triggerOnUpdateListeners() {
        try {
            if (onUpdate != null) {
                onUpdate.run();
            }
        } catch (Exception ex) {
            logger.error("Exception running onUpdate handler!", ex);
        }
    }

    private Map<Mensa, Future<List<? extends MensaDay>>> fetchData() {
        HashMap<Mensa, Future<List<? extends MensaDay>>> data = new HashMap<>();

        CompletableFuture<List<? extends MensaDay>> classic = new CompletableFuture<>();
        CompletableFuture<List<? extends MensaDay>> choice = new CompletableFuture<>();
        new Thread(() -> {
            try {
                Document doc = Jsoup.connect("http://menu.mensen.at/index/index/locid/1").get();
                JkuMensaParser jmp = new JkuMensaParser();
                Map<JkuMensaParser.MensaSubType, List<MensaDayData>> r = jmp.parse(doc);
                classic.complete(r.get(JkuMensaParser.MensaSubType.CLASSIC));
                choice.complete(r.get(JkuMensaParser.MensaSubType.CHOICE));
            } catch (Exception ex) {
                classic.completeExceptionally(ex);
                choice.completeExceptionally(ex);
            }
        }).start();
        data.put(Mensa.CLASSIC, classic);
        data.put(Mensa.CHOICE, choice);

        CompletableFuture<List<? extends MensaDay>> khg = new CompletableFuture<>();
        new Thread(() -> {
            try {
                Document doc = Jsoup.connect("https://www.dioezese-linz.at/institution/8075/essen/menueplan").get();
                KhgMensaParser khgp = new KhgMensaParser();
                List<MensaDayData> r = khgp.parse(doc);
                khg.complete(r);
            } catch (Exception ex) {
                khg.completeExceptionally(ex);
            }
        }).start();
        data.put(Mensa.KHG, khg);

        return data;
    }

    private MensaDay filterByDate(List<? extends MensaDay> parsingResult, LocalDate date) {
        Map<LocalDate, MensaDay> mapped = parsingResult.stream().collect(Collectors.toMap(MensaDay::getDate, d -> d));
        MensaDay currentDay = mapped.get(date);
        return currentDay;
    }

    private LocalDate getRelevantDay() {
        LocalDateTime reqDate = LocalDateTime.now(ZoneId.of("Europe/Vienna"));
        if (reqDate.getHour() > 16) { //switch to new day after mid-day
            reqDate = reqDate.plusDays(1);
        }
        if (reqDate.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue()) {
            reqDate = reqDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        LocalDate date = reqDate.toLocalDate();
        logger.debug("Determined interesting mensa date as {}", date);
        return date;
    }

    public class MensaData {
        private final LocalDate date;
        private final List<? extends MensaCategory> categories;
        private final Mensa mensa;

        public MensaData(Mensa mensa, MensaDay parsed) {
            this.date = parsed.getDate();
            this.categories = parsed.getCategories();
            this.mensa = mensa;
        }

        public Mensa getMensa() {
            return mensa;
        }

        public LocalDate getDate() {
            return date;
        }

        public List<? extends MensaCategory> getCategories() {
            return categories;
        }
    }
}
