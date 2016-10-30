package com.muciaccia.plugins;

import com.muciaccia.plugins.Message;

public interface Plugin {
    public void pluginHasBeenLoaded();
    public void handleMessage(Message message);
}
