package jkumensa.bot.workaround;

import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.DefaultAbsSender;

/**
 * Combine the two different bot types together to a single interface.
 */
public interface CombinedBot {
    void setBotInterface(DefaultAbsSender das);

    String getBotToken();

    String getBotUsername();

    void onUpdateReceived(Update update);

    BotApiMethod onWebhookUpdateReceived(Update update);

    String getBotPath();
    
    void stop();
}
