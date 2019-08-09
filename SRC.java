// Server REST Communication

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

class SRC {
    public static String serverURL = "http://localhost:8080/MyProject_war_exploded/";

    public static Message httpRequest(String resource, String method, Map<String, String> header, String content) {

        // request

        URL url = null;
        HttpURLConnection huc = null;

        try {
            url = new URL(serverURL + resource);
        } catch (MalformedURLException exc) {
            exc.printStackTrace();
        }

        try {
            huc = (HttpURLConnection) url.openConnection();
        } catch (IOException exc) {
            exc.printStackTrace();
        }

        try {
            huc.setRequestMethod(method);
        } catch (ProtocolException exc) {
            exc.printStackTrace();
        }

        if (header != null) {
            for (Map.Entry<String, String> obj : header.entrySet()) {
                huc.setRequestProperty(obj.getKey(), obj.getValue());
            }
        }

        if (content != null) {
            huc.setDoOutput(true);
            try {
                OutputStream out = huc.getOutputStream();
                out.write(content.getBytes());
                out.close();
            } catch (IOException exc) {
                exc.printStackTrace();
            }
        }

        // response

        if (huc.getContentLengthLong() > 0) {
            String response = "";
            int tmp;

            try {
                InputStream in = huc.getInputStream();
                while ((tmp = in.read()) != -1) {
                    response += (char) tmp;
                }
                in.close();
            } catch (IOException exc) {
                exc.printStackTrace();
            }

            return (Message) Json.fromJson(response, Message.class);
        }

        return null;
    }

    /* **************************************** USERS **************************************** */

    public static MessageType addUser(User user) {
        String content = Json.toJson(user);
        HashMap<String, String> header = new HashMap<String, String>();
        header.put("Content-Type", "text/plain");
        header.put("Content-Length", Integer.toString(content.getBytes().length));

        Message response = httpRequest("add_user", "POST", header, content);
        return response == null ? null : response.getType();
    }

    public static MessageType removeUser(String key) {
        Message response = httpRequest("user_list/" + key, "DELETE", null, null);
        return response == null ? null : response.getType();
    }

    /* **************************************** GAMES **************************************** */

    public static Game[] gameList() {
        Message response = httpRequest("game_list", "GET", null, null);
        return (Game[]) Json.fromJson(response.getBody(), Game[].class);
    }

    public static MessageType addGame(Game game) {
        String content = Json.toJson(game);
        HashMap<String, String> header = new HashMap<String, String>();
        header.put("Content-Type", "text/plain");
        header.put("Content-Length", Integer.toString(content.getBytes().length));

        Message response = httpRequest("add_game", "POST", header, content);
        return response == null ? null : response.getType();
    }

    public static Message viewGame(String name) {
        return httpRequest("game_list/" + name, "GET", null, null);
    }

    public static MessageType addUser(String name, User user) {
        String content = Json.toJson(user);
        HashMap<String, String> header = new HashMap<String, String>();
        header.put("Content-Type", "text/plain");
        header.put("Content-Length", Integer.toString(content.getBytes().length));

        Message response = httpRequest("game_list/" + name, "PUT", header, content);
        return response == null ? null : response.getType();
    }

    public static MessageType removeUser(String game, String user) {
        Message response = httpRequest("game_list/" + game + "/" + user, "DELETE", null, null);
        return response == null ? null : response.getType();
    }
}