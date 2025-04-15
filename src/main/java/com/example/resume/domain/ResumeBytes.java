package com.example.resume.domain;

import akka.util.ByteString;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public record ResumeBytes(
        @JsonSerialize(using = ByteStringSerializer.class)
        @JsonDeserialize(using = ByteStringDeserializer.class)
        ByteString bytes) {}
