package unit.protocol;

import network.protocol.Message;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testMessageCreation() {
        String senderId = "peer-123";
        String content = "Hello";
        Message.MessageType type = Message.MessageType.TEXT;

        Message message = new Message(senderId, content, type);

        assertEquals(senderId, message.getSenderId());
        assertEquals(content, message.getContent());
        assertEquals(type, message.getType());
        assertTrue(message.getTimestamp() > 0);
    }

    @Test
    void testMessageTypes() {
        Message textMsg = new Message("peer1", "text", Message.MessageType.TEXT);
        Message handshakeMsg = new Message("peer2", "hello", Message.MessageType.HANDSHAKE);
        Message ackMsg = new Message("peer3", "ack", Message.MessageType.ACK);
        Message disconnectMsg = new Message("peer4", "bye", Message.MessageType.DISCONNECT);

        assertEquals(Message.MessageType.TEXT, textMsg.getType());
        assertEquals(Message.MessageType.HANDSHAKE, handshakeMsg.getType());
        assertEquals(Message.MessageType.ACK, ackMsg.getType());
        assertEquals(Message.MessageType.DISCONNECT, disconnectMsg.getType());
    }

    @Test
    void testMessageSerialization() throws IOException, ClassNotFoundException {
        Message original = new Message("peer-456", "Test content", Message.MessageType.TEXT);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.flush();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Message deserialized = (Message) ois.readObject();

        assertEquals(original.getSenderId(), deserialized.getSenderId());
        assertEquals(original.getContent(), deserialized.getContent());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    void testMessageToString() {
        Message message = new Message("peer-789", "Test", Message.MessageType.HANDSHAKE);
        String toString = message.toString();

        assertTrue(toString.contains("peer-789"));
        assertTrue(toString.contains("Test"));
        assertTrue(toString.contains("HANDSHAKE"));
    }

    @Test
    void testEmptyContent() {
        Message message = new Message("peer-empty", "", Message.MessageType.TEXT);
        assertEquals("", message.getContent());
    }

    @Test
    void testLongContent() {
        String longContent = "a".repeat(10000);
        Message message = new Message("peer-long", longContent, Message.MessageType.TEXT);
        assertEquals(longContent, message.getContent());
    }
}
