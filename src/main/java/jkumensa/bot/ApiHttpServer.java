package jkumensa.bot;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import jkumensa.api.serialization.OrgjsonMensaSerialization;
import jkumensa.bot.datahandling.DataProvider;
import lombok.Value;

/**
 * Creates a mini http webserver exposing the current mensa data as json.
 */
public class ApiHttpServer extends NanoHTTPD {
    private final DataProvider dataProvider;
    private volatile DataHolder data = new DataHolder(null, null);

    public static void main(String[] args) throws IOException, InterruptedException {
        new ApiHttpServer(4555, null).start(5000, false);
    }

    public ApiHttpServer(int port, DataProvider dataProvider) {
        super(port);
        this.dataProvider = dataProvider;
        dataProvider.setOnUpdate(this::update);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getUri().equals("/")) {
            return serveHelp(session);
        } else if (session.getUri().equals("/get")) {
            return serveData(session);
        } else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 NOT FOUND");
        }
    }

    private Response serveData(IHTTPSession session) {
        DataHolder data = this.data;
        if (data == null || data.getJsonCache() == null) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 INTERNAL SERVER ERROR");
        } else if (data.getEtag().equals(session.getHeaders().get("if-none-match"))) {
            Response r = newFixedLengthResponse(Response.Status.NOT_MODIFIED, null, null);
            //r.setKeepAlive(false);//is overwritten in any case...
            r.addHeader("Connection", "close");
            r.addHeader("Access-Control-Allow-Origin", "*");
            return r;
        } else {
            Response r = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", data.getJsonCache());
            r.addHeader("Access-Control-Allow-Origin", "*");
            r.addHeader("Etag", data.getEtag());
            r.addHeader("Connection", "close");
            return r;
        }
    }

    private void update() {
        OrgjsonMensaSerialization s = new OrgjsonMensaSerialization();
        String json = s.toJson(dataProvider.getMensaData());
        data = new DataHolder(String.valueOf(json.hashCode()), json);
    }

    private Response serveHelp(IHTTPSession session) {
        return newFixedLengthResponse(
            Response.Status.OK,
            MIME_HTML,
            "The data is at <a href=\"get\">/get</a><br/>Etag is supported!"
        );
    }

    @Value
    private static class DataHolder {
        String etag;
        String jsonCache;
    }
}
