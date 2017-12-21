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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jkumensa.parser.JkuMensaParser;
import jkumensa.parser.data.CategoryData;
import jkumensa.parser.data.MensaDayData;
import jkumensa.parser.i.Mensa;
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

    private volatile MensaData mensaData;
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
        try {
            Document doc = Jsoup.connect("http://menu.mensen.at/index/index/locid/1").get();
            List<MensaDayData> parsingResult = new JkuMensaParser().parse(doc);

            LocalDateTime reqDate = LocalDateTime.now();
            if (reqDate.getHour() > 16) { //switch to new day after mid-day
                reqDate.plusDays(1);
            }
            if(reqDate.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue()) {
                reqDate = reqDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            }
            logger.debug("Determined interesting mensa date as {}", reqDate.toLocalDate());

            Map<LocalDate, MensaDayData> mapped = parsingResult.stream().collect(Collectors.toMap(MensaDayData::getDate, d -> d));

            MensaDayData currentDay = mapped.get(reqDate.toLocalDate());

            if (currentDay != null) {
                mensaData = new MensaData(currentDay);
            } else {
                mensaData = null;
            }
        } catch (IOException ex) {
            logger.error("Unable to update mensa", ex);
            //keep previous data
        } catch(Exception ex) {
            logger.error("Unable to update mensa", ex);
            mensaData = null; //default to "unknown" to prevent confusion
        }
    }

    public MensaData getMensaData() {
        return mensaData;
    }

    public class MensaData {
        private final LocalDate date;
        private final Map<String, CategoryData> categories;

        public MensaData(MensaDayData parsed) {
            this.date = parsed.getDate();
            this.categories = parsed.getCategories().stream().collect(Collectors.toMap(c -> c.getTitle(), c -> c));
        }

        public Mensa getMensa() {
            return Mensa.JKU;
        }

        public LocalDate getDate() {
            return date;
        }

        public Map<String, CategoryData> getCategories() {
            return categories;
        }
    }
}
