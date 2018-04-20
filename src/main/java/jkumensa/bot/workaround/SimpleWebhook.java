package jkumensa.bot.workaround;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.util.HashMap;
import lombok.SneakyThrows;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.generics.Webhook;
import org.telegram.telegrambots.generics.WebhookBot;

public class SimpleWebhook extends NanoHTTPD implements Webhook {
    private final ObjectMapper mapper = new ObjectMapper();
    private WebhookBot bot;

    public SimpleWebhook(int port) {
        super(port);
    }

    public SimpleWebhook(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    public void startServer() throws TelegramApiRequestException {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException ex) {
            throw new TelegramApiRequestException("Unable to start webserver", ex);
        }
    }

    public void stopServer() {
        stop();
    }

    @Override
    public void registerWebhook(WebhookBot callback) {
        this.bot = callback;
    }

    @Override
    @SneakyThrows
    public Response serve(IHTTPSession session) {

        if (session.getMethod() == Method.POST) {
            HashMap<String, String> files = new HashMap<>();
            session.parseBody(files);
            String postBody = files.get("postData");

            Update update = mapper.readValue(postBody, Update.class);

            BotApiMethod responseMethod = bot.onWebhookUpdateReceived(update);

            String responseString;
            if (responseMethod != null) {
                responseMethod.validate();
                responseString = mapper.writeValueAsString(responseMethod);
            } else {
                responseString = "";
            }

            return newFixedLengthResponse(Response.Status.OK, "application/json", responseString);
        } else {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "500");
        }

    }

    @Override
    public void setInternalUrl(String internalUrl) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setKeyStore(String keyStore, String keyStorePassword) throws TelegramApiRequestException {
        throw new UnsupportedOperationException("Not supported");
    }
}
