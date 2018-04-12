package jkumensa.bot;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jkumensa.parser.data.MensaDayData;
import jkumensa.parser.i.Category;
import jkumensa.parser.i.Mensa;
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

    private static final Set<LocalTime> UPDATE_TIMES;

    static {
        TreeSet<LocalTime> ut = new TreeSet<>();
        ut.add(LocalTime.of(8, 0));
        ut.add(LocalTime.of(9, 0));
        ut.add(LocalTime.of(10, 0));
        ut.add(LocalTime.of(13, 0));
        ut.add(LocalTime.of(16, 0));
        ut.add(LocalTime.of(18, 0));
        ut.add(LocalTime.of(23, 0));
        UPDATE_TIMES = Collections.unmodifiableSet(ut);
    }

    @Getter
    private volatile Map<Mensa, MensaData> mensaData = new HashMap<>();
    private volatile Thread updater;

    public void start() {
        if (updater != null) {
            throw new IllegalStateException("Updated already running!");
        }
        updater = new Thread(() -> {
            while (!Thread.interrupted()) {
                logger.info("Calculating sleep time...");
                ZoneId zoneId = ZoneId.of("Europe/Berlin");
                ZonedDateTime now = ZonedDateTime.now(zoneId);

                Optional<ZonedDateTime> nextDateTime = Stream.concat(
                    UPDATE_TIMES.stream().map(lt -> ZonedDateTime.of(LocalDate.now(), lt, zoneId)),
                    UPDATE_TIMES.stream().map(lt -> ZonedDateTime.of(LocalDate.now().plusDays(1), lt, zoneId))
                ).filter(dt -> dt.isAfter(now))
                    .sorted()
                    .findFirst();

                logger.info("Wake up at {}", nextDateTime.get());
                long sleepTime = now.until(nextDateTime.get(), ChronoUnit.MILLIS);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                    break;
                }
                logger.info("Done sleeping updating now at {}", ZonedDateTime.now(zoneId));
                update();
            }
        });
        updater.setName("MensaUpdater");
        updater.setDaemon(true);
        updater.start();
    }

    public void stop() {
        if (updater != null) {
            updater.interrupt();
            updater = null;
        }
    }

    public void update() {
        Map<Mensa, MensaData> newData = new EnumMap<>(Mensa.class);
        Map<Mensa, MensaData> oldData = this.mensaData;

        Map<Mensa, Future<List<? extends MensaDay>>> fs = fetchData();

        for (Map.Entry<Mensa, Future<List<? extends MensaDay>>> e : fs.entrySet()) {
            try {
                MensaData updated = getNewestOrOldIfNeccessary(e.getKey(), oldData.get(e.getKey()), e.getValue());
                newData.put(e.getKey(), updated);
            } catch (Exception ex) {
                logger.error("Unable to update mensa {}", e.getKey(), ex);
            }
        }

        this.mensaData = newData;
    }

    private MensaData getNewestOrOldIfNeccessary(Mensa mensa, MensaData old, Future<List<? extends MensaDay>> f) {
        try {
            List<? extends MensaDay> ds;
            try {
                ds = f.get();
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof IOException) {
                    logger.error("Unable to update mensa {}", mensa, ex);
                    //keep previous if same day (we don't want to show old data)
                    if (old != null && LocalDate.now().equals(old.getDate())) {
                        return old;
                    } else {
                        return null;
                    }
                } else {
                    throw ex;
                }
            }
            MensaDay rd = filterRelevantDay(ds);
            logger.info("New mensadata for {} is {}", mensa, rd);
            return new MensaData(mensa, rd);
        } catch (Exception ex) {
            logger.error("Unable to update mensa {}", mensa, ex);
            return null; //default to "unknown" to prevent confusion
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

    private MensaDay filterRelevantDay(List<? extends MensaDay> parsingResult) {
        LocalDateTime reqDate = LocalDateTime.now();
        if (reqDate.getHour() > 16) { //switch to new day after mid-day
            reqDate.plusDays(1);
        }
        if (reqDate.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue()) {
            reqDate = reqDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        logger.debug("Determined interesting mensa date as {}", reqDate.toLocalDate());
        Map<LocalDate, MensaDay> mapped = parsingResult.stream().collect(Collectors.toMap(MensaDay::getDate, d -> d));
        MensaDay currentDay = mapped.get(reqDate.toLocalDate());
        return currentDay;
    }

    public class MensaData {
        private final LocalDate date;
        private final List<? extends Category> categories;
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

        public List<? extends Category> getCategories() {
            return categories;
        }
    }
}
