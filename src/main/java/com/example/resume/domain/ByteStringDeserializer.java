package com.example.resume.domain;

import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Base64;

public class ByteStringDeserializer extends JsonDeserializer<ByteString> {
        @Override
        public ByteString deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            byte[] decoded = Base64.getDecoder().decode(p.getValueAsString());
            return ByteString.fromArray(decoded);
        }
}