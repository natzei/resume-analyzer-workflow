package com.example.resume.domain;

import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Base64;

public class ByteStringSerializer extends JsonSerializer<ByteString> {
        @Override
        public void serialize(ByteString value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(Base64.getEncoder().encodeToString(value.toArray()));
        }
}