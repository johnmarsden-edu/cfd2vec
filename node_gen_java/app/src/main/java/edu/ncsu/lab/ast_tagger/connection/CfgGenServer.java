package edu.ncsu.lab.ast_tagger.connection;

import java.io.IOException;

public class CfgGenServer {
    public final static String HOSTNAME = "localhost";
    public final static int PORT = 9271;

    public static ServerConnection getConnection() {
        try {
            return new CfgGenServerConnection(HOSTNAME, PORT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
