package jkumensa.bot;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import jkumensa.bot.workaround.CombinedBot;
import jkumensa.parser.i.Category;
import jkumensa.parser.i.Mensa;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.CallbackQuery;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.exceptions.TelegramApiException;

public class MensaBot implements CombinedBot {
    private static final Logger logger = LoggerFactory.getLogger(MensaBot.class);

    @Getter
    private final UserFeedback userFeedback;

    private final InlineKeyboardMarkup inlineKeyboardMensaSelection;
    private final DataProvider dataProvider;

    private final String botToken;
    private final String botUsername;
    private DefaultAbsSender botInterface;
    private final String botUrl;

    private final MensaMenuFormatter mensaMenuFormatter = new MensaMenuFormatter();

    public MensaBot(Properties p) throws IOException {
        botToken = p.getProperty("bot.token");
        botUsername = p.getProperty("bot.username");
        botUrl = p.getProperty("bot.url");
        long feedbackChatId = Long.parseLong(p.getProperty("bot.feedbackChatId"));

        userFeedback = new UserFeedback(feedbackChatId);

        dataProvider = new DataProvider();
        dataProvider.update();
        dataProvider.start();
        {
            inlineKeyboardMensaSelection = new InlineKeyboardMarkup()
                .setKeyboard(
                    Arrays.asList(
                        Arrays.asList(
                            new InlineKeyboardButton("CLASSIC").setCallbackData("newmensamenu:CLASSIC"),
                            new InlineKeyboardButton("CHOICE").setCallbackData("newmensamenu:CHOICE")
                        ),
                        Arrays.asList(
                            new InlineKeyboardButton("KHG").setCallbackData("newmensamenu:KHG")
                        //new InlineKeyboardButton("RAAB").setCallbackData("newmensamenu:RAAB")
                        )
                    )
                );
        }
    }

    @Override
    @SneakyThrows
    public void onUpdateReceived(Update update) {
        execute(onWebhookUpdateReceived(update));
    }

    @Override
    @SneakyThrows
    public BotApiMethod onWebhookUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            return handleBasicMessage(update);
        } else if (update.hasCallbackQuery()) {
            CallbackQuery q = update.getCallbackQuery();

            if (q.getData().startsWith("mensamenu")) { //convert to new system
                EditMessageText e = generateMensaMenu(q.getData(), q.getMessage().getChatId());
                return new SendMessage()
                    .setChatId(q.getMessage().getChatId())
                    .setText(e.getText())
                    .setReplyMarkup(e.getReplyMarkup());
            }

            if (q.getData().startsWith("newmensamenu")) {
                EditMessageText message = generateMensaMenu(q.getData(), q.getMessage().getChatId());

                if (q.getInlineMessageId() == null) {
                    message.setChatId(q.getMessage().getChatId());
                    message.setMessageId(q.getMessage().getMessageId());
                } else {
                    message.setInlineMessageId(q.getInlineMessageId());
                }

                return message;
            } else {
                throw new IllegalArgumentException("Unknown query \"" + q.getData() + "\"");
            }
        } else {
            //unknown message type
            return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText("Unknown");
        }
    }

    private BotApiMethod handleBasicMessage(Update update) {
        String text = update.getMessage().getText();
        if (text.startsWith("/") && text.length() > 2) {
            int firstSpacePos = text.indexOf(' ');
            if (firstSpacePos == -1) {
                firstSpacePos = text.length();
            }

            String command = text.substring(1, firstSpacePos);
            return BasicCommands.COMMANDS.get(command.toLowerCase()).apply(this, update);
        } else {
            //No command message by user
            return new SendMessage()
                .setChatId(update.getMessage().getChatId())
                .setText("Unknown command, use /help");
        }
    }

    @SneakyThrows
    EditMessageText generateMensaMenu(String query, Long chatId) {
        BreadcrumbNavigator bn = BreadcrumbNavigator.fromString(query);
        EditMessageText m = new EditMessageText();

        if (bn.depth() == 1 && bn.get(0).equals("newmensamenu")) {
            m.setText("Select a mensa:");
            m.setReplyMarkup(inlineKeyboardMensaSelection);
            return m;
        } else if (bn.depth() == 2) {
            String mensaName = bn.getCurrent();

            Mensa mensa;
            try {
                mensa = Mensa.valueOf(mensaName);
            } catch (IllegalArgumentException ex) {
                logger.info("Served: unknown of {}", query);
                m.setChatId(chatId)
                    .setParseMode("Markdown")
                    .disableWebPagePreview()
                    .setText(mensaMenuFormatter.getInternalError(this));

                EditMessageText e = generateMensaMenu(bn.navigateToParent(), chatId);
                execute(new SendMessage()
                    .setChatId(chatId)
                    .setText(e.getText())
                    .setReplyMarkup(e.getReplyMarkup())
                );
                m.setChatId(chatId)
                    .setParseMode("Markdown")
                    .setText(mensaMenuFormatter.getSplitter());
                return m;
            }

            DataProvider.MensaData data = dataProvider.getMensaData().get(mensa);
            if (data == null) {
                logger.info("Served: No data available", query);

                m.setChatId(chatId)
                    .setParseMode("Markdown")
                    .setText(mensaMenuFormatter.getSplitter());

                execute(new SendMessage().setChatId(chatId)
                    .setParseMode("Markdown")
                    .disableWebPagePreview()
                    .setText(mensaMenuFormatter.getUnavalilableText(this)));

                EditMessageText e = generateMensaMenu(bn.navigateToParent(), chatId);
                execute(new SendMessage()
                    .setChatId(chatId)
                    .setText(e.getText())
                    .setReplyMarkup(e.getReplyMarkup())
                );
                return m;
            } else {
                logger.info("Served: {}", query);
                //edit old selection to title
                //(might be problematic to edit (very) old messages though)
                m.setChatId(chatId)
                    .setParseMode("Markdown")
                    .setText(mensaMenuFormatter.getSplitter());

                execute(new SendMessage()
                    .setChatId(chatId)
                    .setParseMode("Markdown")
                    .setText(mensaMenuFormatter.getMensaTitle(data.getMensa().toString(), data.getDate()))
                );

                for (Category cat : data.getCategories().values()) {
                    String txt = mensaMenuFormatter.getCategory(cat);
                    execute(new SendMessage()
                        .setChatId(chatId)
                        .setParseMode("Markdown")
                        .setText(txt)
                    );
                }

                //Send new selection below
                EditMessageText e = generateMensaMenu(bn.navigateToParent(), chatId);
                execute(new SendMessage()
                    .setChatId(chatId)
                    .setText(e.getText())
                    .setReplyMarkup(e.getReplyMarkup())
                );
            }
        }

        /*
            ???
        if (split.length == 1) {
            m = generateMensaMenu("mensamenu:" + Arrays.stream(split).limit(split.length - 1).collect(Collectors.joining(":")), chatId);
        }
         */
        return m;
    }

    public <T extends Serializable, Method extends BotApiMethod<T>> T execute(Method method) throws TelegramApiException {
        return botInterface.execute(method);
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void setBotInterface(DefaultAbsSender das) {
        this.botInterface = das;
    }

    @Override
    public String getBotPath() {
        return botUrl;
    }

    @Override
    public void stop() {
        dataProvider.stop();
    }

    public String getMensaLinks() {
        return "[JKU](http://menu.mensen.at/index/index/locid/1), "
            + "[KHG](https://www.dioezese-linz.at/institution/8075/essen/menueplan), "
            + "[RAAB](http://www.sommerhaus-hotel.at/de/linz#speiseplan)\n";
    }
}
