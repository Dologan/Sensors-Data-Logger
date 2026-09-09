package com.pjinkim.sensors_data_logger;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.security.KeyException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WifiSession implements Runnable {
    public interface WifiScannerCallback {
        void displayWifiScanMeasurements(final int currentApNums, final float currentScanInterval, final String nameSSID, final int RSSI);
    }

    // properties
    private final static String LOG_TAG = WifiSession.class.getName();

    private final static int DEFAULT_INTERVAL = 30000;
    private int mScanInterval = DEFAULT_INTERVAL;

    private MainActivity mContext;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private AtomicBoolean mIsRunning = new AtomicBoolean(false);
    private AtomicBoolean mIsWritingFile = new AtomicBoolean(false);

    private WifiManager mWifiManager;
    private WifiResultStreamer mFileStreamer;
    private BroadcastReceiver mScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // scan wifi signals with WifiManager
            if (!mIsRunning.get()) {
                return;
            }
            if (!hasScanPermission()) {
                Log.w(LOG_TAG, "onReceive: location permission not granted; scan results "
                        + "are unavailable on Android 10 and later.");
                return;
            }
            List<ScanResult> results;
            try {
                results = mWifiManager.getScanResults();
            } catch (SecurityException e) {
                Log.e(LOG_TAG, "onReceive: denied access to scan results", e);
                return;
            }
            Log.i(LOG_TAG, "onReceive: Scan result received. Number of AP: " + String.valueOf(results.size()));

            // save the wifi scan results to text file
            if (mIsWritingFile.get()) {
                try {
                    mFileStreamer.addWifiRecord(results);
                } catch (IOException | KeyException e) {
                    Log.e(LOG_TAG, "onReceive: Cannot add the scan results to file");
                    e.printStackTrace();
                }
            }

            // display Wifi scan results in main class
            float currentScanInterval = ((float) mScanInterval / 1000.0f);
            String firstSsid = results.isEmpty() ? "(no APs)" : results.get(0).SSID;
            int firstLevel = results.isEmpty() ? 0 : results.get(0).level;
            mContext.displayWifiScanMeasurements(results.size(), currentScanInterval, firstSsid, firstLevel);
        }
    };


    // constructor
    public WifiSession(@NonNull MainActivity context, int interval) {
        this.mContext = context;
        this.mScanInterval = interval;
        mWifiManager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    WifiSession(@NonNull MainActivity context) {
        this(context, DEFAULT_INTERVAL);
    }


    // methods
    public void startSession(final String streamFolder) {

        // check wifi hardware is turned on
        if (!mWifiManager.isWifiEnabled()) {
            mContext.showAlertAndStop("Please turn on Wifi first!");
            return;
        }

        // initialize text file stream
        mIsRunning.set(true);
        ContextCompat.registerReceiver(mContext, mScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        if (streamFolder != null) {
            try {
                mFileStreamer = new WifiResultStreamer(mContext, streamFolder);
                mIsWritingFile.set(true);
            } catch (IOException e) {
                mContext.showToast("Cannot create file for Wifi scans");
                e.printStackTrace();
            }
        }
        run();
    }


    public void stopSession() {

        // close text file and reset variables
        if (mIsWritingFile.get()) {
            try {
                mFileStreamer.endFiles();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mIsWritingFile.set(false);
        mIsRunning.set(false);
        mContext.unregisterReceiver(mScanReceiver);
        mHandler.removeCallbacks(this);
    }


    /**
     * Wi-Fi scan results are gated on location access: NEARBY_WIFI_DEVICES from API 33, and
     * ACCESS_FINE_LOCATION before that. Without it getScanResults throws, or returns an empty
     * list.
     */
    private boolean hasScanPermission() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION;
        return ContextCompat.checkSelfPermission(mContext, permission)
                == PackageManager.PERMISSION_GRANTED;
    }


    public void singleScan() {
        if (mWifiManager.startScan()) {
            Log.i(LOG_TAG, "singleScan: Scan request sent.");
        } else {
            // Since API 28 the platform throttles foreground apps to 4 scans per 2 minutes.
            // Cached results still arrive via the receiver, they are just older than requested.
            Log.w(LOG_TAG, "singleScan: Scan request rejected (likely platform scan throttling). "
                    + "Cached results will still be delivered.");
        }
    }


    @Override
    public void run() {
        if (!mWifiManager.isWifiEnabled()) {
            // Apps cannot toggle Wi-Fi from API 29 onward; the user has to do it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mContext.showToast("Wi-Fi is off — scanning is paused.");
            } else {
                mWifiManager.setWifiEnabled(true);
            }
        }
        singleScan();
        if (mIsRunning.get()) {
            mHandler.postDelayed(this, mScanInterval);
        }
    }


    // definition of 'WifiResultStreamer' class
    class WifiResultStreamer extends FileStreamer {

        // properties
        private BufferedWriter mWriter;


        // constructor
        WifiResultStreamer(final Context context, final String outputFolder) throws IOException {
            super(context, outputFolder);
            addFile("wifi", "wifi.txt");
            mWriter = getFileWriter("wifi");
        }


        // methods
        public void addWifiRecord(final List<ScanResult> results) throws IOException, KeyException {

            // execute the block with only one thread
            synchronized (this) {

                // check 'mWriter' variable
                if (mWriter == null) {
                    throw new KeyException("File writer wifi not found.");
                }

                // record wifi-related information in text file
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(results.size());
                stringBuilder.append('\n');
                for (ScanResult eachResult : results) {
                    stringBuilder.append(eachResult.timestamp);
                    stringBuilder.append('\t');
                    stringBuilder.append(eachResult.BSSID);
                    stringBuilder.append('\t');
                    stringBuilder.append(String.valueOf(eachResult.level));
                    stringBuilder.append('\n');
                }
                mWriter.write(stringBuilder.toString());
            }
        }

        @Override
        public void endFiles() throws IOException {

            // execute the block with only one thread
            synchronized (this) {
                mWriter.write("-1");
                mWriter.flush();
                mWriter.close();
            }
        }
    }


    // getter and setter
    public void setScanInterval(int newInterval) {
        mScanInterval = newInterval;
    }

    public boolean isRunning() {
        return mIsRunning.get();
    }

    public boolean isWritingFile() {
        return mIsWritingFile.get();
    }
}
