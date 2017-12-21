package jkumensa.bot.workaround;

import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

public class PollRelayBot extends TelegramLongPollingBot {
    private final CombinedBot bot;

    public PollRelayBot(CombinedBot cb) {
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
    public void onUpdateReceived(Update update) {
        bot.onUpdateReceived(update);
    }
}
