package jkumensa.bot;

import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.objects.Update;

public interface RelayBot {

    public String getBotToken();

    public String getBotUsername();
    
    public void onUpdateReceived(Update update);

    public BotApiMethod onWebhookUpdateReceived(Update update);

    public String getBotPath();
}
