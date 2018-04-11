package jkumensa.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.api.methods.ForwardMessage;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.exceptions.TelegramApiException;

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
            "feedback",
            (mb, update) -> {
                String[] split = update.getMessage().getText().split(" ", 2);
                if(split.length < 2) {
                    return new SendMessage()
                        .setChatId(update.getMessage().getChatId())
                        .setText("You need to say something!");
                }
                String text = split[1];

                if (text.length() < 5 || text.length() > 1000) {
                    return new SendMessage()
                        .setChatId(update.getMessage().getChatId())
                        .setText("A reasonable sized feedback would be nice!");
                }

                SendMessage message = new SendMessage()
                    .setChatId(update.getMessage().getChatId());

                ForwardMessage forwardMsg = mb.getUserFeedback().writeUserFeedback(mb, update.getMessage());
                if (forwardMsg != null) {
                    try {
                        mb.execute(forwardMsg);
                        message.setText("Got the feedback!");
                    } catch (TelegramApiException ex) {
                        logger.error("Unable to send feedback!", ex);
                        message.setText("Error sending feedback!");
                    }
                } else {
                    message.setText("DENIED!");
                }
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
                        + "/feedback <text> \u27a1 Might be read at some point, eventually, probably, maybe\n"
                        + "/start \u27a1 Initial command, gives menu"
                    );
            }
        );

        COMMANDS = map;
    }
}
