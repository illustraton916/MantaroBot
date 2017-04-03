package net.kodehawa.mantarobot.utils.data;

import br.com.brjdevs.network.*;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.MantaroShard;
import net.kodehawa.mantarobot.data.ConnectionWatcherData;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.KryoUtils;
import net.kodehawa.mantarobot.utils.UnsafeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static br.com.brjdevs.java.utils.extensions.CollectionUtils.random;

public class ConnectionWatcherDataManager implements DataManager<ConnectionWatcherData> {
    public static final int CLOSE_CODE_OK = 1600;
    private static final Logger LOGGER = LoggerFactory.getLogger("ConnectionWatcherDataManager");

    private final Client client;

    public ConnectionWatcherDataManager(int port) {
        try {
            client = new Client(new URI("wss://localhost:" + port), new PacketRegistry(), new SocketListenerAdapter() {
                @Override
                public Object onPacket(Connection connection, int id, Object packet) {
                    if(packet instanceof JSONPacket) {
                        JSONObject json = ((JSONPacket) packet).getJSON();
                        if(json.has("action")) {
                            switch(json.getString("action")) {
                                case "shutdown":
                                    MantaroBot.getInstance().getAudioManager().getMusicManagers().forEach((s, musicManager) -> {
                                        if (musicManager.getTrackScheduler() != null) musicManager.getTrackScheduler().stop();
                                    });

                                    Arrays.stream(MantaroBot.getInstance().getShards()).forEach(MantaroShard::prepareShutdown);

                                    Arrays.stream(MantaroBot.getInstance().getShards()).forEach(mantaroShard -> mantaroShard.getJDA().shutdown(true));
                                    System.exit(0);
                                    break;
                            }
                        }
                    }
                    return null;
                }

                @Override
                public void onClose(Connection connection, int id, int code, String message) {
                    if(code != CLOSE_CODE_OK) {
                        LOGGER.error("Connection closed with unexpected code " + code + ": " + message);
                    }
                }
            });
            client.getPacketClient().connectBlocking();
            client.getPacketClient().waitForValidation();
        } catch(Exception e) {
            UnsafeUtils.throwException(e);
            throw new InternalError();
        }
    }

    public void reboot(boolean hardReboot) {
        client.getPacketClient().sendPacket(new JSONPacket("{\"command\":\"cw.reboot(" + hardReboot + ")\""));
        client.getPacketClient().getConnection().close(CLOSE_CODE_OK);
    }

    public Object[] eval(String code) {
        client.getPacketClient().sendPacket(new JSONPacket(new JSONObject().put("command", code).toString()));
        JSONObject response = client.getPacketClient().readPacketBlocking(JSONPacket.class).getJSON();
        if(response.has("error")) {
            throw new RuntimeException(response.getString("error"));
        }
        JSONArray returns = response.getJSONArray("returns");
        Object[] ret = new Object[returns.length()];
        for(int i = 0; i < ret.length; i++) {
            Object o = returns.get(i);
            if(o instanceof JSONObject) {
                o = KryoUtils.unserialize(Base64.getDecoder().decode(((JSONObject) o).getString("data")));
            }
            ret[i] = o;
        }
        return ret;
    }

    @Override
    public void save() {
    }

    @Override
    public void close() {
        client.getPacketClient().getConnection().close(CLOSE_CODE_OK);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConnectionWatcherData get() {
        AbstractClient client = this.client.getPacketClient();
        client.sendPacket(new JSONPacket("{\"command\":\"return cw.getJdaPing()\"}"));
        int ping = client.readPacketBlocking(JSONPacket.class).getJSON().getJSONArray("returns").getInt(0);
        client.sendPacket(new JSONPacket("{\"command\":\"return cw.getReboots()\"}"));
        int reboots = client.readPacketBlocking(JSONPacket.class).getJSON().getJSONArray("returns").getInt(0);
        client.sendPacket(new JSONPacket("{\"command\":\"return cw.getOwners()\"}"));
        String owners = client.readPacketBlocking(JSONPacket.class).getJSON().getJSONArray("returns").getJSONObject(0).getString("data");
        client.sendPacket(new JSONPacket("{\"command\":\"return cw.getJvmArgs()\"}"));
        String jvmargs = client.readPacketBlocking(JSONPacket.class).getJSON().getJSONArray("returns").getJSONObject(0).getString("data");
        return new ConnectionWatcherData(
                (List<String>) KryoUtils.unserialize(Base64.getDecoder().decode(owners)),
                (List<String>) KryoUtils.unserialize(Base64.getDecoder().decode(jvmargs)),
                reboots,
                ping);
    }
}
