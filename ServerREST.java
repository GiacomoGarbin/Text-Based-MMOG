import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Iterator;

@Path("/")
public class ServerREST {
    // resources
    private static ArrayList<User> users = new ArrayList<User>();
    private static ArrayList<Game> games = new ArrayList<Game>();

    /* **************************************** USERS **************************************** */

    @GET
    @Path("/user_list")
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String userList() {
        Message message = new Message();
        message.setType(MessageType.OK);

        message.setBody(Json.toJson(users));

        return Json.toJson(message);
    }

    @GET
    @Path("/user_list/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String viewUser(@PathParam("key") String key) {
        Message message = new Message();
        message.setType(MessageType.OK);

        AddressPort tmp = new AddressPort(key.split("-")[0], Integer.parseInt(key.split("-")[1]));

        User user = getUserByAddressPort(tmp);

        if (user == null) {
            message.setType(MessageType.USER_NOT_EXIST);
        } else {
            message.setBody(Json.toJson(user));
        }

        return Json.toJson(message);
    }

    @POST
    @Path("/add_user")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String addUser(String json) {
        User user = (User) Json.fromJson(json, User.class);
        Message message = new Message();
        message.setType(MessageType.OK);

        boolean available = (getUserByAddressPort(new AddressPort(user.getAddress(), user.getPort())) == null);
        if (available) {
            users.add(user);
        }

        if (!available) message.setType(MessageType.USER_ADDRESSPORT_UNAVAILABLE);

        return Json.toJson(message);
    }

    @DELETE
    @Path("/user_list/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String removeUser(@PathParam("key") String key) {
        Message message = new Message();
        message.setType(MessageType.OK);

        AddressPort tmp = new AddressPort(key.split("-")[0], Integer.parseInt(key.split("-")[1]));

        User user = getUserByAddressPort(tmp);

        if (user == null) {
            message.setType(MessageType.USER_NOT_EXIST);
        } else {
            users.remove(user);
        }

        return Json.toJson(message);
    }

    /* **************************************** GAMES **************************************** */

    @GET
    @Path("/game_list")
    @Produces("text/plain")
    public synchronized String gameList() {
        Message message = new Message();
        message.setType(MessageType.OK);

        message.setBody(Json.toJson(games));

        return Json.toJson(message);
    }

    @GET
    @Path("/game_list/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String viewGame(@PathParam("name") String name) {
        Message message = new Message();
        message.setType(MessageType.OK);

        Game game = getGameByName(name);

        if (game == null) {
            message.setType(MessageType.GAME_NOT_EXIST);
        } else {
            message.setBody(Json.toJson(game));
        }

        return Json.toJson(message);
    }

    @POST
    @Path("/add_game")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String addGame(String json) {
        Game game = (Game) Json.fromJson(json, Game.class);
        Message message = new Message();
        message.setType(MessageType.OK);

        boolean available = (getGameByName(game.getName()) == null);
        if (available) {
            games.add(game);
        }

        if (!available) message.setType(MessageType.GAME_NAME_UNAVAILABLE);

        return Json.toJson(message);
    }

    @DELETE
    @Path("/game_list/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String removeGame(@PathParam("name") String name) {
        Message message = new Message();
        message.setType(MessageType.OK);

        Game game = getGameByName(name);

        if (game == null) {
            message.setType(MessageType.GAME_NOT_EXIST);
        } else {
            games.remove(game);
        }

        return Json.toJson(message);
    }

    @PUT
    @Path("/game_list/{name}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String addUser(@PathParam("name") String name, String json) {
        User user = (User) Json.fromJson(json, User.class);
        Message message = new Message();
        message.setType(MessageType.OK);

        Game game = getGameByName(name);

        if (game == null) {
            message.setType(MessageType.GAME_NOT_EXIST);
        } else {
            for (User tmp : game.getUsers()) {
                if (tmp.getName().equals(user.getName())) {
                    message.setType(MessageType.GAME_USER_DUPLICATION);
                    break;
                }
            }
        }

        if (message.getType() == MessageType.OK) {
            game.getUsers().add(user);
        }

        return Json.toJson(message);
    }

    @DELETE
    @Path("/game_list/{game}/{user}")
    @Produces(MediaType.TEXT_PLAIN)
    public synchronized String removeUser(@PathParam("game") String name, @PathParam("user") String user) {
        Message message = new Message();
        message.setType(MessageType.OK);

        Game game = getGameByName(name);

        if (game == null) {
            message.setType(MessageType.GAME_NOT_EXIST);
        } else {
            Iterator<User> iter = game.getUsers().iterator();

            while (iter.hasNext()) {
                User tmp = iter.next();

                if (tmp.getName().equals(user)) {
                    iter.remove();
                    break;
                }
            }

            if (game.getUsers().isEmpty()) {
                games.remove(game);
            }
        }

        return Json.toJson(message);
    }

    /* *************************************************************************************** */

    private static User getUserByAddressPort(AddressPort key) {
        for (User user : users) {
            if (key.equals(new AddressPort(user.getAddress(), user.getPort()))) {
                return user;
            }
        }
        return null;
    }

    private static Game getGameByName(String name) {
        for (Game game : games) {
            if (game.getName().equals(name)) {
                return game;
            }
        }
        return null;
    }
}