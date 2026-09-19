package com.konselyavisa.document;

public record DocumentContent(String originalFilename, String contentType, byte[] bytes) {}
