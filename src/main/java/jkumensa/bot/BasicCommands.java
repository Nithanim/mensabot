package jkumensa.bot;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.Update;

/**
 * Holds the response for basic commands of the telegram bot.
 */
public class BasicCommands {
    private static final Logger logger = LoggerFactory.getLogger(BasicCommands.class);
    public static final Map<String, BiFunction<MensaBot, Update, SendMessage>> COMMANDS;

    static {
        Map<String, BiFunction<MensaBot, Update, SendMessage>> map = new HashMap<>();

        map.put(
            "start",
            (mb, update) -> {
                Long chatId = update.getMessage().getChatId();
                EditMessageText e = mb.generateMensaMenu("newmensamenu", chatId);
                SendMessage message = new SendMessage()
                    .setChatId(chatId)
                    .setText(e.getText())
                    .setReplyMarkup(e.getReplyMarkup());
                return message;
            }
        );

        map.put(
            "mensen",
            (mb, update) -> {
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .disableWebPagePreview()
                    .setParseMode("Markdown")
                    .setText(mb.getMensaLinks());
            }
        );

        map.put(
            "testcommand",
            (mb, update) -> {
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText("This works! Bailing out, you are now on your own. Good luck.\n" + update.getMessage().getChatId());
            }
        );

        map.put(
            "help",
            (mb, update) -> {
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText(
                        "/help \u27a1 This help\n"
                        + "/mensen \u27a1 Links to menu plans on the official websites\n"
                        + "/legend \u27a1 Overview over used symbols\n"
                        + "/allergycodes \u27a1 Print allergy codes\n"
                        + "/start \u27a1 Initial command, gives menu\n"
                        + "by @nithanim"
                    );
            }
        );

        BiFunction<MensaBot, Update, SendMessage> allergieliste = (mb, update) -> {
            try (InputStream in = BasicCommands.class.getResourceAsStream("/allergycode.ger.txt")) {
                Scanner s = new Scanner(in, "UTF-8");
                StringBuilder sb = new StringBuilder().append("No warranty given!\n\n");
                while (s.hasNextLine()) {
                    String l = s.nextLine();
                    String[] split = l.split("=", 2);
                    sb.append('*').append(split[0]).append("*: ");
                    sb.append(split[1]).append('\n');
                }
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText(sb.toString())
                    .setParseMode("markdown");
            } catch (IOException ex) {
                logger.error("Unable to load allergies!");
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText("Unable to load allergy codes!");
            }
        };

        map.put("allergycodes", allergieliste);
        map.put("allergieliste", allergieliste);
        map.put("allergene", allergieliste);

        BiFunction<MensaBot, Update, SendMessage> legend = (mb, update) -> {
            return new SendMessage()
                .setChatId(update.getMessage().getChatId())
                .setText(
                    "\ud83c\udf3b vegan\n"
                    + "\ud83c\uDF31 vegetarisch\n"
                    + "\ud83d\udc1f Fish\n"
                    + "\ud83d\udca1 Brainfood\n"
                    + "\ud83c\udf0a MSC"
                );
        };
        map.put("legende", legend);
        map.put("legend", legend);

        COMMANDS = map;
    }
}
