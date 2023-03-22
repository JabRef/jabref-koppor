package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.util.FileUpdateMonitor;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharelatexConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SharelatexConnector.class);

    private final String contentType = "application/json; charset=utf-8";
    private final JsonParser parser = new JsonParser();
    private final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:80.0) Gecko/20100101 Firefox/80.0";
    private Map<String, String> loginCookies = new HashMap<>();
    private String server;
    private String loginUrl;
    private String csrfToken;
    private String projectUrl;
    private final WebSocketClientWrapper client = new WebSocketClientWrapper();

    private static final class AccessData {
        public final String _csrf;
        public final String email;
        public final String password;

        private AccessData(String csrfToken, String user, String password) {
            _csrf = csrfToken;
            email = user;
            this.password = password;
        }
    }

    public String connectToServer(String serverUri, String user, String password) throws IOException {

        this.server = serverUri;
        this.loginUrl = server + "/login";
        Connection.Response crsfResponse;

        crsfResponse = Jsoup.connect(loginUrl).method(Method.GET)
                .execute();

        Document welcomePage = crsfResponse.parse();
        Map<String, String> welcomCookies = crsfResponse.cookies();

        csrfToken = welcomePage.select("input[name=_csrf]").attr("value");

        AccessData accessData = new AccessData(csrfToken, user, password);
        Gson gson = new Gson();
        String json = gson.toJson(accessData);

        Connection.Response loginResponse = Jsoup.connect(loginUrl)
                .header("Content-Type", contentType)
                .header("Accept", "application/json, text/plain, */*")
                .cookies(welcomCookies)
                .method(Method.POST)
                .requestBody(json)
                .followRedirects(true)
                .ignoreContentType(true)
                .userAgent(userAgent)
                .execute();
        LOGGER.debug("Body: {}", loginResponse.body());
        ///Error handling block
        if (contentType.equals(loginResponse.contentType())) {
            if (loginResponse.body().contains("message")) {
                JsonElement jsonTree = parser.parse(loginResponse.body());
                JsonObject obj = jsonTree.getAsJsonObject();
                JsonObject message = obj.get("message").getAsJsonObject();
                String errorMessage = message.get("text").getAsString();
                LOGGER.error("error {}", errorMessage);
                return errorMessage;
            }
        }

        loginCookies = loginResponse.cookies();
        if (loginCookies.isEmpty()) {
            loginCookies = welcomCookies;
        }

        return "";
    }

    public Optional<JsonObject> getProjects() throws IOException {
        projectUrl = server + "/project";
        Connection.Response projectsResponse = Jsoup.connect(projectUrl)
                .referrer(loginUrl).cookies(loginCookies).method(Method.GET).userAgent(userAgent).execute();

        var projectCookies = projectsResponse.cookies();
        this.loginCookies = projectCookies;

        Optional<Element> scriptContent = Optional
                .ofNullable(projectsResponse.parse().select("script#data").first());

        if (scriptContent.isPresent()) {
            String data = scriptContent.get().data();
            JsonElement jsonTree = parser.parse(data);

            JsonObject obj = jsonTree.getAsJsonObject();

            return Optional.of(obj);
        }
        return Optional.empty();
    }

    public void startWebsocketListener(String projectId, BibDatabaseContext database, ImportFormatPreferences prefs, FileUpdateMonitor fileMonitor)
            throws URISyntaxException {
        long millis = System.currentTimeMillis();
        LOGGER.trace("millis {}", millis);
        String socketioUrl = server + "/socket.io/1";
        String scheme = server.contains("https://") ? "wss" : "ws";
        try {
            Connection.Response webSocketresponse = Jsoup.connect(socketioUrl)
                    .cookies(loginCookies)
                    .data("t", String.valueOf(millis)).method(Method.GET).execute();

            LOGGER.trace("body {}", webSocketresponse.body());

            String resp = webSocketresponse.body();

            var cookies = webSocketresponse.cookies();
            String channel = resp.substring(0, resp.indexOf(":"));

            URI webSocketchannelUri = new URIBuilder(socketioUrl + "/websocket/" + channel).setScheme(scheme).build();
            LOGGER.debug("WebSocketChannelUrl {}", webSocketchannelUri);
            client.setImportFormatPrefs(prefs, fileMonitor);
            client.setServerNameOrigin(server);
            client.setCookies(loginCookies);
            client.createAndConnect(webSocketchannelUri, projectId, database);

            setDatabaseName(database);
        } catch (IOException e) {
            LOGGER.error("Problem starting websocket", e);
        }
    }

    public void sendNewDatabaseContent(String newContent) throws InterruptedException {
        client.sendNewDatabaseContent(newContent);
    }

    public void registerListener(Object listener) {
        client.registerListener(listener);
    }

    public void unregisterListener(Object listener) {
        client.unregisterListener(listener);
    }

    public void disconnectAndCloseConn() {
        try {
            client.leaveDocAndCloseConn();
        } catch (IOException e) {
            LOGGER.error("Problem leaving document and closing websocket", e);
        }
    }

    private void setDatabaseName(BibDatabaseContext database) {
        String dbName = database.getDatabasePath().map(Path::getFileName).map(Path::toString).orElse("");
        client.setDatabaseName(dbName);
    }
}
