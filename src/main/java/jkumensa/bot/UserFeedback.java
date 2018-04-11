package jkumensa.bot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.telegram.telegrambots.api.methods.ForwardMessage;
import org.telegram.telegrambots.api.objects.Message;

public class UserFeedback {
    private final Map<Long, Integer> userFeedback = new ConcurrentHashMap<>();
    private final long forwardChartId;

    public UserFeedback(long forwardChartId) {
        this.forwardChartId = forwardChartId;

    }

    public ForwardMessage writeUserFeedback(MensaBot mb, Message m) {
        Integer tries = userFeedback.computeIfAbsent(m.getChatId(), k -> 1);
        if (tries != null && tries > 3) {
            return null;
        }
        synchronized (userFeedback) {
            ForwardMessage sm = new ForwardMessage(forwardChartId, m.getChatId(), m.getMessageId());
            userFeedback.merge(m.getChatId(), 1, (a, b) -> a+b);
            return sm;
        }
    }
}
