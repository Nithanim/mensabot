package jkumensa.bot;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import jkumensa.bot.workaround.CombinedBot;
import jkumensa.parser.data.CategoryData;
import jkumensa.parser.data.MealData;
import jkumensa.parser.data.SubCategoryData;
import jkumensa.parser.i.FoodCharacteristic;
import jkumensa.parser.i.Priced;
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
    private static Logger logger = LoggerFactory.getLogger(MensaBot.class);

    private static final DateTimeFormatter printFormat = DateTimeFormatter.ofPattern("EE dd.MM.yyyy", Locale.GERMAN);
    private final Map<String, Integer> userFeedback = new ConcurrentHashMap<>();

    private final InlineKeyboardMarkup inlineKeyboardMensaSelection;
    private final Map<String, InlineKeyboardMarkup> inlineKeyboardCategorySelection;
    private final DataProvider dataProvider;

    private final String botToken;
    private final String botUsername;
    private DefaultAbsSender botInterface;
    private final String botUrl;

    public MensaBot(Properties p) throws IOException {
        botToken = p.getProperty("bot.token");
        botUsername = p.getProperty("bot.username");
        botUrl = p.getProperty("bot.url");

        dataProvider = new DataProvider();
        dataProvider.update();
        dataProvider.start();
        {
            inlineKeyboardMensaSelection = new InlineKeyboardMarkup()
                .setKeyboard(
                    Arrays.asList(
                        Arrays.asList(
                            new InlineKeyboardButton("JKU").setCallbackData("mensamenu:jku")
                        )/*,
                        Arrays.asList(
                            new InlineKeyboardButton("KHG").setCallbackData("Mensa:khg"),
                            new InlineKeyboardButton("RAAB").setCallbackData("Mensa:raab")
                        )*/
                    )
                );
        }
        inlineKeyboardCategorySelection = new HashMap<>();
        {
            inlineKeyboardCategorySelection.put(
                "jku",
                new InlineKeyboardMarkup()
                    .setKeyboard(
                        Arrays.asList(
                            Arrays.asList(
                                new InlineKeyboardButton("CLASSIC").setCallbackData("mensamenu:jku:Classic"),
                                new InlineKeyboardButton("CHOICE").setCallbackData("mensamenu:jku:Choice")
                            ),
                            Arrays.asList(
                                new InlineKeyboardButton("BACK").setCallbackData("mensamenu:")
                            )
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
            String text = update.getMessage().getText();
            if (text.startsWith("/start")) {
                Long chatId = update.getMessage().getChatId();
                EditMessageText e = generateMensaMenu("mensamenu:", chatId);
                SendMessage message = new SendMessage()
                    .setChatId(chatId)
                    .setText(e.getText())
                    .setReplyMarkup(e.getReplyMarkup());
                return message;
            } else if (text.startsWith("/feedback")) {
                if (text.length() < 20 || text.length() > 5000) {
                    return new SendMessage()
                        .setChatId(update.getMessage().getChatId())
                        .setText("What are you doing?");
                }
                String user = update.getMessage().getChat().getUserName();
                String feedback = text.substring(10);

                SendMessage message = new SendMessage()
                    .setChatId(update.getMessage().getChatId());
                if (writeUserFeedback(user, feedback)) {
                    message.setText("Got the feedback!");
                } else {
                    message.setText("DENIED!");
                }
                return message;
            } else if (text.startsWith("/mensen")) {
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .disableWebPagePreview()
                    .setParseMode("Markdown")
                    .setText(getMensaLinks());
            } else if (text.startsWith("/testcommand")) {
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText("This works! Bailing out, you are now on your own. Good luck.");
            } else if (text.startsWith("/help")) {
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText(
                        "/help \u27a1 This help\n"
                        + "/mensen \u27a1 Links to menu plans on the official websites\n"
                        + "/feedback <text> \u27a1 Might be read at some point, eventually, probably, maybe\n"
                        + "/start \u27a1 Initial command, gives menu"
                    );
            } else {
                //Normal message by user
                return new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText("Unknown command, use /help");
            }
        } else if (update.hasCallbackQuery()) {
            CallbackQuery q = update.getCallbackQuery();
            if (q.getData().startsWith("mensamenu")) {
                EditMessageText message = generateMensaMenu(q.getData(), q.getMessage().getChatId());

                if (q.getInlineMessageId() == null) {
                    message.setChatId(q.getMessage().getChatId());
                    message.setMessageId(q.getMessage().getMessageId());
                } else {
                    message.setInlineMessageId(q.getInlineMessageId());
                }

                //try {
                return message;
                //} catch (TelegramApiRequestException ex) {
                //    if (!(ex.getErrorCode() == 400 && ex.getApiResponse().contains("message is not modified"))) {
                //        throw ex;
                //    }
                //}
            }
        }

        return null;
    }

    @SneakyThrows
    private EditMessageText generateMensaMenu(String query, Long chatId) {
        EditMessageText m = new EditMessageText();

        String[] split = query.substring("mensamenu:".length()).split(":");
        if (split.length == 1 && split[0].isEmpty()) {
            m.setText("Select a mensa:");
            m.setReplyMarkup(inlineKeyboardMensaSelection);
            return m;
        } else if (split.length == 1) {
            InlineKeyboardMarkup keyboard = inlineKeyboardCategorySelection.get(split[0]);
            if (keyboard != null) {
                m.setText("Select a category:");
                m.setReplyMarkup(keyboard);
                return m;
            }
        } else if (split.length == 2) {
            String cat = split[1];
            CategoryData catData = dataProvider.getMensaData().getCategories().get(cat);
            if (catData != null) {
                logger.info("Served: {}", query);
                //edit old selection to title
                //(might be problematic to edit (very) old messages though)
                m.setChatId(chatId)
                    .setParseMode("Markdown")
                    .setText(
                        "\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\n"
                        + "\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\n"
                        + "\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\u2b1b\n"
                    );

                execute(new SendMessage()
                    .setChatId(chatId)
                    .setParseMode("Markdown")
                    .setText("```" + "\n##### " + catData.getTitle() + " #####\n" + printFormat.format(dataProvider.getMensaData().getDate()) + "```")
                );

                //send subcategories
                for (SubCategoryData subcat : catData.getSubCategories()) {
                    StringBuilder sb = new StringBuilder();

                    sb.append("*~~~~~ ").append(subcat.getTitle());
                    String catFoodCharacteristics = generateFoodCharacteristicsString(subcat.getAttachments());
                    if (!catFoodCharacteristics.isEmpty()) {
                        sb.append("  ");
                        sb.append(catFoodCharacteristics);
                    }
                    sb.append(" ~~~~~*");

                    String catPrice = generatePriceString(subcat);
                    if (!catPrice.isEmpty()) {
                        sb.append("   ").append(catPrice);
                    }

                    sb.append('\n');

                    for (MealData meal : subcat.getMeals()) {
                        appendMeal(sb, meal);
                    }

                    execute(new SendMessage()
                        .setChatId(chatId)
                        .setParseMode("Markdown")
                        .setText(sb.toString())
                    );
                }

                //Send new selection below
                EditMessageText e = generateMensaMenu("mensamenu:" + Arrays.stream(split).limit(split.length - 1).collect(Collectors.joining(":")), chatId);
                execute(new SendMessage()
                    .setChatId(chatId)
                    .setText(e.getText())
                    .setReplyMarkup(e.getReplyMarkup())
                );
            } else {
                logger.info("Served: unknown of {}", query);
                m.setChatId(chatId)
                    .setParseMode("Markdown")
                    .disableWebPagePreview()
                    .setText(
                        "\u26a1 \u26a1 \u26a1 `Internal Error, sorry` \u26a1 \u26a1 \u26a1 \n"
                        + "Visit the sites directly insted: \n"
                        + getMensaLinks()
                    );

                EditMessageText e = generateMensaMenu("mensamenu:" + Arrays.stream(split).limit(split.length - 1).collect(Collectors.joining(":")), chatId);
                execute(new SendMessage()
                    .setChatId(chatId)
                    .setText(e.getText())
                    .setReplyMarkup(e.getReplyMarkup())
                );
            }
        }

        if (split.length == 1) {
            m = generateMensaMenu("mensamenu:" + Arrays.stream(split).limit(split.length - 1).collect(Collectors.joining(":")), chatId);
        }

        return m;
    }

    private void appendMeal(StringBuilder sb, MealData meal) {
        sb.append(meal.getTitle());

        String mealPrice = generatePriceString(meal);
        if (!mealPrice.isEmpty()) {
            sb.append(" ").append(mealPrice).append("  ");
        }

        String mealFoodCharacteristics = generateFoodCharacteristicsString(meal.getAttachments());
        if (!mealFoodCharacteristics.isEmpty()) {
            sb.append(mealFoodCharacteristics);
            sb.append("   ");
        }

        String allergyString = meal.getAllergyCodes().stream().map(c -> c.name()).collect(Collectors.joining(", "));
        if (!allergyString.isEmpty()) {
            sb.append(" (");
            sb.append(allergyString);
            sb.append(')');
        }
        sb.append('\n');
    }

    private String generatePriceString(Priced priced) {
        StringBuilder sb = new StringBuilder();
        if (priced.getPriceStudent() > 0 || priced.getPriceStudentBonus() > 0) { //assume discount
            sb.append(priced.getPriceGuest()).append('/');
            sb.append(priced.getPriceStudent()).append('/');
            sb.append(priced.getPriceStudentBonus()).append('€');
            return sb.toString();
        } else if (priced.getPriceGuest() > 0) {
            sb.append(priced.getPriceGuest()).append('€');
            return sb.toString();
        } else {
            return "";
        }
    }

    private String generateFoodCharacteristicsString(Set<FoodCharacteristic> fcs) {
        StringBuilder sb = new StringBuilder();
        for (FoodCharacteristic fc : fcs) {
            switch (fc) {
                case VEGAN:
                    //sb.append("\ud83c\udf33");
                    sb.append("\ud83c\udf3b ");
                    break;
                case FISH:
                    sb.append("\ud83d\udc1f ");
                    break;
                case VEGETARIAN:
                    sb.append("\ud83c\uDF31 ");
                    break;
                case BRAINFOOD:
                    sb.append("\ud83d\udca1 ");
                    break;
                case MSC:
                    sb.append("\ud83c\udf0a ");
                    break;
            }
        }
        return sb.toString();
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

    private boolean writeUserFeedback(String user, String feedback) {
        Integer tries = userFeedback.computeIfAbsent(user, k -> 1);
        if (tries != null && tries > 3) {
            return false;
        }
        synchronized (userFeedback) {
            Path p = Paths.get("./feedback.txt");

            byte[] bytes = (user + "<:>" + feedback + "<:>\n").getBytes(StandardCharsets.UTF_8);

            try {
                Files.write(p, bytes, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                userFeedback.put(user, tries + 1);
                return true;
            } catch (IOException ex) {
                logger.error("Unable to write feedback", ex);
                return false;
            }
        }
    }

    @Override
    public void stop() {
        dataProvider.stop();
    }

    private String getMensaLinks() {
        return "[JKU](http://menu.mensen.at/index/index/locid/1), "
            + "[KHG](https://www.dioezese-linz.at/institution/8075/essen/menueplan), "
            + "[RAAB](http://www.sommerhaus-hotel.at/de/linz#speiseplan)\n";
    }

}
