package edu.ncsu.lab.ast_tagger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ServerConnection {
    private final SocketChannel channel;
    private boolean isConnected;

    private final ByteBuffer lengthBuffer;

    public ServerConnection(String hostname, int portNumber) throws IOException {
        var socketAddress = new InetSocketAddress(hostname, portNumber);
        this.channel = SocketChannel.open();
        this.isConnected = this.channel.connect(socketAddress);
        this.lengthBuffer = ByteBuffer.allocate(4);
    }

    private static int calculateMessageLength(org.capnproto.MessageBuilder message) {
        int length = 0;

        var segments = message.getSegmentsForOutput();
        // Calculate segment table length
        int tableSize = (segments.length + 2) & (~1);

        length += 4 * tableSize;

        // Calculate length of buffers
        for (var buffer : segments) {
            length += buffer.remaining();
        }

        return length;
    }

    public void send(org.capnproto.MessageBuilder message) throws IOException {
        while (!this.isConnected) {
            this.isConnected = this.channel.finishConnect();
        }
        lengthBuffer.rewind();
        int message_length = calculateMessageLength(message);
        lengthBuffer.putInt(message_length);
        lengthBuffer.rewind();
        //        System.out.println(lengthBuffer.getInt());
        lengthBuffer.rewind();
        this.channel.write(lengthBuffer);
        org.capnproto.Serialize.write(channel, message);
    }
}
