package network.protocol;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String senderId;
    private final String content;
    private final long timestamp;
    private final MessageType type;

    public Message(String senderId, String content, MessageType type) {
        this.senderId = senderId;
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public MessageType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Message{" +
               "senderId='" + senderId + '\'' +
               ", content='" + content + '\'' +
               ", type=" + type +
               ", timestamp=" + timestamp +
               '}';
    }

    public enum MessageType {
        TEXT,
        HANDSHAKE,
        ACK,
        DISCONNECT
    }
}
