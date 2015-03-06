package com.reelyactive.blesdk.application;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.reelyactive.blesdk.service.BleService;

import java.util.concurrent.atomic.AtomicInteger;

import hugo.weaving.DebugLog;

/**
 * This class provides a convenient way to make your application aware of any Reelceivers.
 * <p>
 * Register it using {@link android.app.Application#registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks)}<br/>
 * <p>
 * Extend it to customize the behaviour of your application.
 * <p>
 * The default behaviour is to bind to the {@link BleService} as soon as the app is created.
 *
 * @see android.app.Application.ActivityLifecycleCallbacks
 */
@SuppressWarnings("unused")
public class ReelyAwareApplicationCallback implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = ReelyAwareApplicationCallback.class.getSimpleName();
    private final Context context;
    private final ServiceConnection serviceConnection;
    private boolean isBound = false;
    private final AtomicInteger activityCount = new AtomicInteger();
    private Messenger toService;
    private final Messenger fromService = new Messenger(new IncomingHahdler());
    private Activity current;

    /**
     * As soon as the component is created, we bindBleService to the {@link BleService}
     * @param context The application's {@link Context}
     */
    public ReelyAwareApplicationCallback(Context context) {
        this.context = context;
        this.serviceConnection = new BleServiceConnection();
        bindBleService();
    }

    /**
     * The default behaviour is to check if a {@link ReelyAwareActivity} is running, and call a scan if so.<br/>
     * See {@link #startScan()} and {@link #getScanType()}
     * @param activity The resumed {@link Activity}
     */
    @Override
    public void onActivityResumed(Activity activity) {
        Log.d(TAG, "activity resumed");
        current = activity;
        if (isReelyAware(activity) && activityCount.incrementAndGet() == 1) {
            startScan();
        }
    }

    /**
     * The default behaviour is to check if any {@link ReelyAwareActivity} is still running, and call a scan if so.<br/>
     * See {@link #startScan()} and {@link #getScanType()}
     * @param activity The resumed {@link Activity}.
     */
    @Override
    public void onActivityPaused(Activity activity) {
        Log.d(TAG, "activity paused");
        current = null;
        if (isReelyAware(activity) && activityCount.decrementAndGet() <= 0) {
            startScan();
        }
    }

    /**
     * This method can be called to request a scan.<br/>
     * It makes a call to {@link #getScanType} in order to determine the type of scan.
     */
    protected void startScan() {
        Message scanRequest = Message.obtain(null, BleService.Command.START_SCAN.ordinal());
        scanRequest.arg1 = getScanType().ordinal();
        try {
            toService.send(scanRequest);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send scanRequest to service.", e);
        }
    }

    /**
     * Override this in order to chose which kind of scan is to be used when {@link #startScan} is called.
     *
     * @return The {@link BleService.ScanType} to be used in the current application state.
     */
    protected BleService.ScanType getScanType() {
        return activityCount.get() == 0 ? BleService.ScanType.LOW_POWER : BleService.ScanType.ACTIVE;
    }

    /**
     * Get the number of {@link ReelyAwareActivity ReelyAwareActivities} currently running (0 or 1 basically)
     *
     * @return The number of {@link ReelyAwareActivity ReelyAwareActivities} running
     */
    protected int getActivityCount() {
        return activityCount.get();
    }

    /**
     * This method is called when a {@link BleService.Event} is received.<br/>
     * Its default behaviour is to notify the currently running {@link ReelyAwareActivity} (if any).<br/>
     * Override this and you can customize the behaviour of the application.
     *
     * @param event The {@link BleService.Event} received from the {@link BleService}.
     */
    protected void onBleEvent(BleService.Event event) {
        switch (event) {
            case IN_REGION:
                Log.d(TAG, "Application entered region");
                if (isReelyAware(getCurrentActivity())) {
                    ((ReelyAwareActivity) getCurrentActivity()).onEnterRegion();
                }
                break;
            case OUT_REGION:
                Log.d(TAG, "Application left region");
                if (isReelyAware(getCurrentActivity())) {
                    ((ReelyAwareActivity) getCurrentActivity()).onLeaveRegion();
                }
                break;
        }
    }

    /**
     * Access the application {@link Context}.
     * @return The application {@link Context}.
     */
    protected Context getContext() {
        return context;
    }

    /**
     * Get currently running Activity.
     * @return The currently running Activity.
     */
    protected Activity getCurrentActivity() {
        return current;
    }

    /**
     * Find out if an {@link Activity} implements {@link ReelyAwareActivity}
     * @param activity The {@link Activity}
     * @return true if the {@link Activity} implements {@link ReelyAwareActivity}, false if not.
     */
    protected static boolean isReelyAware(Activity activity) {
        return activity != null && ReelyAwareActivity.class.isInstance(activity);
    }

    /**
     * This method is called when the {@link BleService is available}.<br/>
     * The default behaviour is to start a scan.
     */
    protected void onBleServiceBound() {
        startScan();
    }


    /**
     * ************* PRIVATE STUFF ******************
     */

    protected void bindBleService() {
        context.bindService(new Intent(context, BleService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }


    protected void unbindBleService() {
        if (isBound) {
            Message registration = Message.obtain(null, BleService.Command.UNREGISTER_CLIENT.ordinal());
            registration.replyTo = fromService;
            try {
                toService.send(registration);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send unregistration to service.", e);
            }
            context.unbindService(serviceConnection);
        }
    }


    final class BleServiceConnection implements ServiceConnection {
        @Override
        @DebugLog
        public void onServiceConnected(ComponentName name, IBinder service) {
            isBound = true;
            toService = new Messenger(service);
            Message registration = Message.obtain(null, BleService.Command.REGISTER_CLIENT.ordinal());
            registration.replyTo = fromService;
            try {
                toService.send(registration);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send registration to service.", e);
            }
            onBleServiceBound();
        }

        @Override
        @DebugLog
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    }

    final class IncomingHahdler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                BleService.Event event = BleService.Event.fromOrdinal(msg.what);
                onBleEvent(event);
            } catch (IllegalArgumentException e) {

            }
        }
    }


    /**
     * ************* METHODS NOT USED ******************
     */

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }


    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }
}
