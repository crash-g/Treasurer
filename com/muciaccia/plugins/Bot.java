package com.muciaccia.plugins;

public interface Bot {
    public Object retrieveValue(String key);
    public void storeValue(String key, Object value);
    public void sendText(String text);
}
