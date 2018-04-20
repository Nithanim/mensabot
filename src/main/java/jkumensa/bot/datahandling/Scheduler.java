package jkumensa.bot.datahandling;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import static jkumensa.bot.datahandling.DataProvider.logger;

public class Scheduler {
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
    
    private volatile Thread updater;
    private final Runnable onTrigger;

    public Scheduler(Runnable onTrigger) {
        this.onTrigger = onTrigger;
    }
    
    public void start() {
        if (updater != null) {
            throw new IllegalStateException("Updated already running!");
        }
        updater = new Thread(() -> {
            onTrigger.run();
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
                onTrigger.run();
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
}
