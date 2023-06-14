package edu.ncsu.lab.ast_tagger.connection;

import java.io.IOException;

public abstract class ServerConnection {

    public ServerConnection(String hostname, int portNumber) throws IOException {
    }

    public ServerConnection() {
    }

    public abstract void send(org.capnproto.MessageBuilder message) throws IOException;
}
