package middleware;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_LEN = 10_000_000;

    private long tag;
    private Byte serviceId; 
    private Byte methodId;  
    private byte[] payload; 

    private Message() {}

    public static Message request(long tag, byte serviceId, byte methodId, byte[] payload) {
        Message m = new Message();
        m.tag = tag;
        m.serviceId = serviceId;
        m.methodId = methodId;
        m.payload = payload;
        return m;
    }

    public static Message response(long tag, byte[] payload) {
        Message m = new Message();
        m.tag = tag;
        m.payload = payload;
        return m;
    }

    public boolean isRequest() { return serviceId != null; }
    public boolean isResponse() { return serviceId == null; }

    public long getTag() { return tag; }

    public byte getServiceId() {
        if (serviceId == null) throw new IllegalStateException("ServiceId só existe em requests");
        return serviceId;
    }

    public byte getMethodId() {
        if (methodId == null) throw new IllegalStateException("MethodId só existe em requests");
        return methodId;
    }

    public byte[] getPayload() { return payload; }

    public byte[] serialize() throws IOException {
        if (payload != null && payload.length > MAX_LEN) {
            throw new IOException("payload demasiado grande: " + payload.length);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeLong(tag);
        out.writeBoolean(isRequest());

        if (isRequest()) {
            out.writeByte(serviceId);
            out.writeByte(methodId);
        }

        if (payload != null && payload.length > 0) {
            out.writeInt(payload.length);
            out.write(payload);
        } else {
            out.writeInt(0);
        }

        out.flush();
        return baos.toByteArray();
    }

    public static Message deserialize(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        Message m = new Message();
        m.tag = in.readLong();
        boolean isRequest = in.readBoolean();

        if (isRequest) {
            m.serviceId = in.readByte();
            m.methodId = in.readByte();
        }

        int len = in.readInt();
        if (len < 0 || len > MAX_LEN) throw new IOException("payloadLen inválido: " + len);

        if (len == 0) {
            m.payload = null;
        } else {
            m.payload = new byte[len];
            in.readFully(m.payload);
        }

        return m;
    }
}