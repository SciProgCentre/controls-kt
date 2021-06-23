package ru.mipt.npm.magix.client;

import kotlinx.serialization.json.JsonElement;
import ru.mipt.npm.magix.api.MagixMessage;

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

    /**
     * Create a magix endpoint client using RSocket with raw tcp connection
     * @param host host name of magix server event loop
     * @param port port of magix server event loop
     * @return the client
     */
    static MagixClient<JsonElement> rSocketTcp(String host, int port) {
        return ControlsMagixClient.Companion.rSocketTcp(host, port, JsonElement.Companion.serializer());
    }

    /**
     *
     * @param host host name of magix server event loop
     * @param port port of magix server event loop
     * @param path
     * @return
     */
    static MagixClient<JsonElement> rSocketWs(String host, int port, String path) {
        return ControlsMagixClient.Companion.rSocketWs(host, port, JsonElement.Companion.serializer(), path);
    }
}