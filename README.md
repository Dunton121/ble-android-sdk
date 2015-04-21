# reelyactive-ble-android-sdk
This SDK allows you to scan beacons and advertise as a beacon.

The SDK is compatible with all versions of Android supporting BLE (ie. 4.3+).

## Gradle


Add it to your project :

```groovy
compile 'com.reelyactive:blesdk:0.3.2'
```

## Scan with a simple API

### Basics
Add this call in your Application class :

```java
registerActivityLifecycleCallbacks(new ReelyAwareApplicationCallback(this) {
    // let your IDE add what's missing
});
```

And make sure any activity - which needs to get notified about bluetooth event - implements [ReelyAwareActivity](library/src/main/java/com/reelyactive/blesdk/application/ReelyAwareActivity.java) :
```java
public class ReelyAwareScanActivity extends Activity implements ReelyAwareActivity {

    @Override
    public void onScanStarted() {}

    @Override
    public void onScanStopped() {}

    @Override
    public void onEnterRegion(ScanResult beacon) {}

    @Override
    public void onLeaveRegion(ScanResult beacon) {}
}
```

This way, Bluetooth scanning is triggered when your Activity is resumed, and stopped when it is paused.<br/>
The [ScanResult](library/src/main/java/com/reelyactive/blesdk/support/ble/ScanResult.java) class is a clone of [Lollipop's ScanResult class](http://developer.android.com/reference/android/bluetooth/le/ScanResult.html), offering identical APIs.

### Scan start

The scanning behaviour can be changed by overriding the other methods of [ReelyAwareApplicationCallback](library/src/main/java/com/reelyactive/blesdk/application/ReelyAwareApplicationCallback.java), such as "shouldStartScan" :
```java
public class MyReelyAwareApplicationCallback extends ReelyAwareApplicationCallback {
    @Override
    protected boolean shouldStartScan() {
        return isBound(); // always check this at least
    }

    @Override
    public boolean onBleEvent(BleService.Event event, Object data) {
        if(!super.onBleEvent(event, data)) {
            // do your background stuff here
        }
        return true;
    }

}
```

### Beacon filter
The default filter for the scan is the following :
```java
    protected ScanFilter getScanFilter() {
        return new ScanFilter.Builder().setServiceUuid(
            ParcelUuid.fromString("7265656C-7941-6374-6976-652055554944")
        ).build();
    }
```
Override this method and you can set the filter you want !

## Scan with the support API
This is API is similar to what you usually find in a support package : the same APIs as on the latest Android version (Lollipop here), but on a different set of classes.

This API differs from the previous one, in the sens that you have to completely handle the scan lifecycle.

First, get a [BluetoothLeScannerCompat](library/src/main/java/com/reelyactive/blesdk/support/ble/BluetoothLeScannerCompat.java), and decide what you'll do in a [ScanCallback](library/src/main/java/com/reelyactive/blesdk/support/ble/ScanCallback.java) :
```java
BluetoothLeScannerCompat scanner = BluetoothLeScannerCompatProvider.getBluetoothLeScannerCompat(mContext);
ScanCallback scanCallback = new ScanCallback() {
    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        if (callbackType == ScanSettings.CALLBACK_TYPE_FIRST_MATCH) {
            // FOUND A BEACON
        } else {
            // LOST A BECON
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        // FAILED TO START SCANNING
    }
};
```

Then, start a scan :
```java
scanner.startScan(
    // Create a filter
    Arrays.asList(new ScanFilter.Builder().setServiceUuid(
        ParcelUuid.fromString("7265656c-7941-6374-6976-652055554944"))
        .build()
    ),
    // Use specific scan settings
    new ScanSettings.Builder() //
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH |
                ScanSettings.CALLBACK_TYPE_MATCH_LOST) //
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //
            .setScanResultType(ScanSettings.SCAN_RESULT_TYPE_FULL) //
            .build(),
    scanCallback
);
```
When you are done, stop the scan :
```java
scanner.stopScan(scanCallback);
```
