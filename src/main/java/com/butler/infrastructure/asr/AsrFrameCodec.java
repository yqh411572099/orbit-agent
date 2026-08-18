package com.butler.infrastructure.asr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** 火山流式 ASR 二进制帧编解码（协议版本 V1）。 */
final class AsrFrameCodec {
    private AsrFrameCodec() {}

    // message type
    static final int FULL_REQUEST = 0b0001;
    static final int AUDIO_ONLY = 0b0010;
    static final int FULL_RESPONSE = 0b1001;
    static final int ERROR_RESPONSE = 0b1111;
    // flags
    static final int POS_SEQUENCE = 0b0001;
    static final int NEG_SEQUENCE = 0b0010;
    static final int NEG_WITH_SEQUENCE = 0b0011;
    static final int WITH_EVENT = 0b0100;
    // serialization / compression
    private static final int JSON = 0b0001;
    private static final int GZIP = 0b0001;

    static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(data); }
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] gunzip(byte[] data) {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gz.readAllBytes();
        } catch (Exception e) {
            return data;
        }
    }

    /** 构建一帧：4 字节头 + 4 字节 sequence(正数) + 4 字节 payload_size + gzip payload。 */
    static ByteBuffer build(int messageType, int flags, int seq, byte[] gzipPayload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((0b0001 << 4) | 1);                 // version=1, header size=1 (单位4字节)
        out.write((messageType << 4) | flags);
        out.write((JSON << 4) | GZIP);
        out.write(0x00);                               // reserved
        byte[] seqBuf = ByteBuffer.allocate(4).putInt(seq).array();
        out.write(seqBuf, 0, 4);
        byte[] sizeBuf = ByteBuffer.allocate(4).putInt(gzipPayload.length).array();
        out.write(sizeBuf, 0, 4);
        out.write(gzipPayload, 0, gzipPayload.length);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /** 解析上游返回帧。 */
    static Parsed parse(byte[] msg) {
        int headerSize = msg[0] & 0x0f;
        int messageType = (msg[1] >> 4) & 0x0f;
        int flags = msg[1] & 0x0f;
        int compression = msg[2] & 0x0f;
        int pos = headerSize * 4;
        int payloadSequence = 0;
        boolean last = false;
        int event = -1;
        if ((flags & POS_SEQUENCE) != 0) {
            payloadSequence = ByteBuffer.wrap(msg, pos, 4).getInt();
            pos += 4;
        }
        if ((flags & NEG_SEQUENCE) != 0) last = true;
        if ((flags & WITH_EVENT) != 0) {
            event = ByteBuffer.wrap(msg, pos, 4).getInt();
            pos += 4;
        }
        int code = 0;
        if (messageType == FULL_RESPONSE) {
            // size only
            pos += 4;
        } else if (messageType == ERROR_RESPONSE) {
            code = ByteBuffer.wrap(msg, pos, 4).getInt();
            pos += 8; // code + size
        }
        byte[] payload = new byte[msg.length - pos];
        System.arraycopy(msg, pos, payload, 0, payload.length);
        if (compression == GZIP && payload.length > 0) payload = gunzip(payload);
        String json = new String(payload, StandardCharsets.UTF_8);
        return new Parsed(messageType, code, last, event, payloadSequence, json);
    }

    record Parsed(int messageType, int code, boolean last, int event, int sequence, String json) {}
}
