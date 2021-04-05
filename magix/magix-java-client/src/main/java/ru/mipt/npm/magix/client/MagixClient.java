package ru.mipt.npm.magix.client;

import kotlinx.serialization.json.JsonElement;
import space.kscience.dataforge.magix.api.MagixMessage;

import java.io.IOException;
import java.util.concurrent.Flow;

/**
 * See https://github.com/waltz-controls/rfc/tree/master/2
 *
 * @param <T>
 */
public interface MagixClient<T> {
    void broadcast(MagixMessage<T> msg) throws IOException;

    Flow.Publisher<MagixMessage<T>> subscribe();

    static MagixClient<JsonElement> rSocketTcp(String host, int port) {
        return ControlsMagixClient.Companion.rSocketTcp(host, port);
    }

    static MagixClient<JsonElement> rSocketWs(String host, int port, String path) {
        return ControlsMagixClient.Companion.rSocketWs(host, port, path);
    }
}
