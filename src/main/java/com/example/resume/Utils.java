package com.example.resume;

import akka.util.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ByteString readByteString(Path path) {
        return ByteString.fromArray(readBytes(path));
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) { logger.error(e.getMessage()); }
    }
}
