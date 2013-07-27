package com.labs.adk.commons.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author Amir Lazarovich
 */
public class Utils {
    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////

    public static byte[] floatToBytes(float value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
    }

    public static float bytesToFloat(byte bytes[]) {
        if (bytes == null || bytes.length != 4) {
            throw new IllegalArgumentException("bytes size must be exactly 4");
        }

        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    public static float bytesToFloat(byte bytes[], int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }
}
