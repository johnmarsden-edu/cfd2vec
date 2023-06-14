package edu.ncsu.lab.ast_tagger.connection;

import edu.ncsu.lab.cfg_gen.api.CfgGenerator;
import org.capnproto.MessageBuilder;

import java.util.HashMap;

public class MockServerConnection extends ServerConnection {
    public MockServerConnection() {
        this.numMessagesSent = new HashMap<>();
    }

    public HashMap<String, Integer> getNumMessagesSent() {
        return numMessagesSent;
    }

    private final HashMap<String, Integer> numMessagesSent;

    @Override
    public void send(MessageBuilder message) {
        var canStrat = message
                .getRoot(CfgGenerator.Message.factory)
                .getProgramGroup()
                .toString()
                .substring(0, 2);
        this.numMessagesSent.put(canStrat, this.numMessagesSent.getOrDefault(canStrat, 0) + 1);
    }
}
