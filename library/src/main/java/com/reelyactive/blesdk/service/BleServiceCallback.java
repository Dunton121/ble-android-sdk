package com.reelyactive.blesdk.service;

/**
 * Created by saiimons on 15-03-30.
 */
public interface BleServiceCallback {
    public void onBleEvent(BleService.Event event, Object data);
}
