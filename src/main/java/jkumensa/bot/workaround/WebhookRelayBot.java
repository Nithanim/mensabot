package jkumensa.bot.workaround;

import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramWebhookBot;

public class WebhookRelayBot extends TelegramWebhookBot {
    private final CombinedBot bot;

    public WebhookRelayBot(CombinedBot cb) {
        bot = cb;
    }

    @Override
    public String getBotToken() {
        return bot.getBotToken();
    }

    @Override
    public String getBotUsername() {
        return bot.getBotUsername();
    }

    @Override
    public BotApiMethod onWebhookUpdateReceived(Update update) {
        return bot.onWebhookUpdateReceived(update);
    }

    @Override
    public String getBotPath() {
        return bot.getBotPath();
    }
}
