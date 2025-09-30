package com.example.bleuartdemo3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLEScannerDemo3";
    private static final long SCAN_PERIOD = 10000; // 10 sec
    private static final int PERMISSION_REQUEST_CODE = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private Handler handler;
    private boolean isScanning = false;

    private TextView deviceList;
    private Button scanButton;

    private StringBuilder scanResults = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceList = findViewById(R.id.deviceList);
        scanButton = findViewById(R.id.scanButton);

        handler = new Handler();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            deviceList.setText("Bluetooth not supported on this device.");
            scanButton.setEnabled(false);
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        // Check and request permissions immediately
        if (checkBlePermissions()) {
            // Permissions are granted, setup the button functionality
            setupScanButton();
        } else {
            // Permissions are NOT granted, request them
            requestBlePermissions();
            // Button functionality will be set up in onRequestPermissionsResult
        }
    }

    // -------------------------------------------------------------------------
    // PERMISSION LOGIC
    // -------------------------------------------------------------------------

    private boolean checkBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) and higher
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 (API 30) and lower requires ACCESS_FINE_LOCATION for scanning
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBlePermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Requesting new specific permissions
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            // Requesting location for older devices
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        // Use ActivityCompat for runtime requests
        ActivityCompat.requestPermissions(MainActivity.this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                deviceList.setText("Permissions granted. Ready to scan.");
                // CRITICAL FIX: Only setup the scan button AFTER permissions are granted
                setupScanButton();
            } else {
                deviceList.setText("Permissions denied. Cannot scan.");
                scanButton.setEnabled(false);
                Toast.makeText(this, "Bluetooth permissions are required for scanning.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // -------------------------------------------------------------------------
    // SCANNER CONTROL LOGIC
    // -------------------------------------------------------------------------

    private void setupScanButton() {
        scanButton.setEnabled(true);
        scanButton.setText("START SCAN");
        scanButton.setOnClickListener(v -> {
            if (!bluetoothAdapter.isEnabled()) {
                deviceList.setText("Please enable Bluetooth first.");
                // Optionally request to enable Bluetooth here via Intent
                return;
            }

            if (isScanning) {
                scanLeDevice(false); // Stop scan
            } else {
                scanResults.setLength(0); // clear old results
                deviceList.setText("Scanning...");
                scanLeDevice(true); // Start scan
            }
        });
    }

    private void scanLeDevice(final boolean enable) {
        if (bluetoothLeScanner == null) return;

        if (enable) {
            // CRITICAL FIX: Explicitly check the *SCANNING* permission right before the call
            if (!checkScanPermission()) {
                deviceList.setText("Error: Missing BLUETOOTH_SCAN or ACCESS_FINE_LOCATION permission.");
                Log.e(TAG, "Attempted to start scan without required permission.");
                return;
            }

            isScanning = true;

            // Stop scanning after predefined scan period
            handler.postDelayed(this::stopScanning, SCAN_PERIOD);

            // This call is now immediately preceded by the check, resolving the error/warning
            bluetoothLeScanner.startScan(leScanCallback);
            scanButton.setText("STOP SCAN");


        } else {
            stopScanning();
        }
    }
    // Helper method to check ONLY the permission required for starting the scan
    private boolean checkScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            // For older devices, location permission is required for the scan operation
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }
    private void stopScanning() {
        if (!isScanning) return; // Already stopped or never started

        // Safety check before calling stopScan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            // If permission was revoked mid-scan, we just log and stop state change
        } else if (bluetoothLeScanner == null) {
            // Adapter issue
        } else {
            bluetoothLeScanner.stopScan(leScanCallback);
        }

        isScanning = false;
        handler.removeCallbacksAndMessages(null); // Remove any pending stop messages
        scanButton.setText("START SCAN");

        if (scanResults.length() == 0) {
            deviceList.setText("No devices found.");
        } else {
            // Leave the list of devices displayed
            deviceList.setText(scanResults.toString());
        }
    }

    // -------------------------------------------------------------------------
    // SCAN CALLBACK
    // -------------------------------------------------------------------------
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            // Check BLUETOOTH_CONNECT permission before using device details on API 31+
            // ContextCompat.checkSelfPermission is used for robust runtime check.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            // Retrieve device name and address
            String name = device.getName();
            String address = device.getAddress();

            if (name == null || name.isEmpty()) {
                name = "Unknown Device";
            }

            String deviceInfo = name + " (" + address + ") - RSSI: " + result.getRssi() + "\n";
            Log.d(TAG, "Found: " + deviceInfo);

            // Simple check to prevent duplicates in the list
            if (!scanResults.toString().contains(address)) {
                scanResults.append(deviceInfo);

                // Update UI on the main thread
                runOnUiThread(() -> deviceList.setText(scanResults.toString()));
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            // Optional: Implement this if you use batch scanning (which saves power).
            // For simplicity, we typically rely on onScanResult for real-time display.
            for (ScanResult result : results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            String errorDescription;
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                    errorDescription = "Scan already started.";
                    break;
                case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    // CRITICAL ERROR: Often means Location Services are disabled on Android 11/R and lower,
                    // or a permissions/system limit issue on Android 12+.
                    errorDescription = "App registration failed (Permissions/Location Services Issue).";
                    break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorDescription = "BLE advertising/scanning unsupported on this device.";
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                    errorDescription = "Internal error (e.g., Bluetooth stack crash).";
                    break;
                default:
                    errorDescription = "Unknown error code: " + errorCode;
                    break;
            }

            Log.e(TAG, "Scan Failed: " + errorDescription);

            // Update UI and stop scanning state
            runOnUiThread(() -> {
                deviceList.setText("Scan failed: " + errorDescription);
                // Ensure stopScanning exists and is accessible
                stopScanning();
            });
        }
    };
}