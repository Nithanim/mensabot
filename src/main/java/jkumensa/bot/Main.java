package jkumensa.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import jkumensa.bot.workaround.CombinedBot;
import jkumensa.bot.workaround.PollRelayBot;
import jkumensa.bot.workaround.SimpleWebhook;
import jkumensa.bot.workaround.WebhookRelayBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws TelegramApiRequestException, IOException {
        Properties p = new Properties();
        p.load(Files.newInputStream(Paths.get("./settings.properties")));

        String mode = p.getProperty("bot.mode");
        CombinedBot bot = new MensaBot(p);

        if ("webhook".equals(mode)) {
            logger.info("Starting mensa telegram bot using webhook");
            WebhookRelayBot relay = new WebhookRelayBot(bot);
            bot.setBotInterface(relay);

            int port = Integer.parseInt(p.getProperty("bot.port"));
            String url = p.getProperty("bot.url");
            logger.info("Starting on port {} with external url {}", port, url);

            SimpleWebhook webhook = new SimpleWebhook(port);
            webhook.startServer();
            webhook.registerWebhook(relay);
            relay.setWebhook(url, null);

            logger.info("Bot started! 'quit' to shut down...");

            Thread dummy = new Thread(() -> {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ex) {
                }
            }); 
            dummy.start(); // keep the jvm alive without non-daemon threads
            
            waitShutdown(() -> {
                try {
                    relay.setWebhook("", null);
                } catch (Exception ex) {
                    logger.error("Error on shutdown", ex);
                }
                bot.stop();
                webhook.stopServer();
            });

        } else if ("poll".equals(mode)) {
            logger.info("Starting mensa telegram bot using polling");
            PollRelayBot relay = new PollRelayBot(bot);
            bot.setBotInterface(relay);
            relay.clearWebhook();
            DefaultBotSession session = new DefaultBotSession();
            session.setToken(bot.getBotToken());
            session.setOptions(relay.getOptions());
            session.setCallback(relay);
            session.start();

            logger.info("Bot started! 'quit' to shut down...");

            waitShutdown(() -> {
                bot.stop();
                session.stop();
            });
        }
    }

    private static final AtomicBoolean shutdownLock = new AtomicBoolean(false);

    private static void waitShutdown(Runnable r) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("ShutdownHook: Stopping bot...");

            Thread.getAllStackTraces().keySet().forEach(System.out::println);

            if (!shutdownLock.getAndSet(true)) {
                logger.info("Executing cleanup in shutdown hook thread...");
                r.run();
            }
        }));

        try {
            Scanner s = new Scanner(System.in);
            String line;
            while (!(line = s.nextLine()).equalsIgnoreCase("quit")) {
                logger.info("Got {} but only \"quit\" supported!", line);
            }

            if (!shutdownLock.getAndSet(true)) {
                logger.info("Executing cleanup in main thread...");
                r.run();
            }
        } catch (NoSuchElementException ex) {
            logger.warn("Closed stdin? Exiting mainthread now", ex);
            //Thrown by scanner if Stdin closed (?)
            //fallback to shutdown listener
        }
    }
}
