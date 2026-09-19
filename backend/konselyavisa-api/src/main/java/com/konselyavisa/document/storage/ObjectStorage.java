package com.konselyavisa.document.storage;

public interface ObjectStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    boolean exists(String key);

    void delete(String key);
}
