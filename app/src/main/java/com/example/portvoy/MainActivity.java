package com.prabhaav.portvoy;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.*;
import android.widget.ProgressBar;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private ImageView statusImageView;
    private TextView statusTextView, logTextView;
    private ScrollView scrollView;
    private MaterialButton refreshButton, openTabButton, stopButton;
    private ImageButton settingsButton;
    private ExecutorService executorService;
    private Handler handler;
    private int port = 8082;
    private int timeout = 200; // Default timeout
    private String deviceName = "Device"; // Default device name
    private static final String TAG = "NetworkScan";
    private String logs = "", foundIpAddress = null;
    private boolean isScanRunning = false;
    private boolean stopRequested = false; // Flag to stop the scan

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_PORT = "port";
    private static final String KEY_TIMEOUT = "timeout";
    private static final String KEY_DEVICE_NAME = "deviceName";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeUI();
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (savedInstanceState != null) {
            logs = savedInstanceState.getString("logs", "");
            logTextView.setText(logs);
            foundIpAddress = savedInstanceState.getString("foundIpAddress", null);
        }

        // Load settings or prompt user to set them
        if (!sharedPreferences.contains(KEY_PORT) || !sharedPreferences.contains(KEY_TIMEOUT)) {
            // Prompt user to set settings
            openSettings();
        } else {
            // Load settings
            port = sharedPreferences.getInt(KEY_PORT, 8082);
            timeout = sharedPreferences.getInt(KEY_TIMEOUT, 200);
            deviceName = sharedPreferences.getString(KEY_DEVICE_NAME, "Device");
            statusTextView.setText(getString(R.string.tap_start_scan, deviceName));
        }

        // Set initial state of openTabButton
        openTabButton.setEnabled(foundIpAddress != null);

        // Set onClickListeners
        refreshButton.setOnClickListener(v -> refreshScan());
        openTabButton.setOnClickListener(v -> openWebPage());
        stopButton.setOnClickListener(v -> stopScan());
        settingsButton.setOnClickListener(v -> openSettings());
    }

    private void initializeUI() {
        progressBar = findViewById(R.id.progressBar);
        statusImageView = findViewById(R.id.statusImageView);
        statusTextView = findViewById(R.id.statusTextView);
        logTextView = findViewById(R.id.logTextView);
        scrollView = findViewById(R.id.scrollView);
        refreshButton = findViewById(R.id.refreshButton);
        openTabButton = findViewById(R.id.openTabButton);
        stopButton = findViewById(R.id.stopButton);
        settingsButton = findViewById(R.id.settingsButton);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("logs", logs);
        outState.putString("foundIpAddress", foundIpAddress);
    }

    private void refreshScan() {
        if (isScanRunning) {
            Toast.makeText(this, getString(R.string.scan_already_running), Toast.LENGTH_SHORT).show();
            return;
        }

        resetUIForNewScan();

        executorService.execute(() -> {
            String foundIp = performNetworkScan();
            handleScanResult(foundIp);
        });
    }

    private void resetUIForNewScan() {
        logs = getString(R.string.log_initial);
        updateLog(logs);
        statusTextView.setText(getString(R.string.refreshing));
        stopRequested = false;
        progressBar.setVisibility(View.VISIBLE);
        statusImageView.setVisibility(View.GONE); // Hide image during scan
        isScanRunning = true;
        foundIpAddress = null;
        handler.post(() -> openTabButton.setEnabled(false));
    }

    private void handleScanResult(String foundIp) {
        handler.post(() -> {
            progressBar.setVisibility(View.GONE);
            isScanRunning = false;
            if (foundIp != null) {
                statusTextView.setText(getString(R.string.found_ip, foundIp));
                logs += getString(R.string.success_ip, foundIp);
                updateLog(logs);
                foundIpAddress = foundIp;
                statusImageView.setImageResource(R.drawable.check_mark); // Display check mark
                statusImageView.setVisibility(View.VISIBLE);
                openTabButton.setEnabled(true);
                openWebPage();
            } else {
                statusTextView.setText(getString(R.string.no_server_found));
                statusImageView.setImageResource(R.drawable.red_x); // Display red X
                statusImageView.setVisibility(View.VISIBLE);
                openTabButton.setEnabled(false);
            }
        });
    }

    private void stopScan() {
        if (isScanRunning) {
            stopRequested = true;
            executorService.shutdownNow();
            executorService = Executors.newSingleThreadExecutor(); // Reinitialize after shutdown
            progressBar.setVisibility(View.GONE);
            statusTextView.setText(getString(R.string.scan_stopped));
            isScanRunning = false;
            handler.post(() -> openTabButton.setEnabled(foundIpAddress != null));
        }
    }

    private void openWebPage() {
        if (foundIpAddress != null) {
            String url = "http://" + foundIpAddress + ":" + port;
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
            customTabsIntent.launchUrl(this, Uri.parse(url));
        } else {
            Toast.makeText(this, getString(R.string.no_server_found_to_open), Toast.LENGTH_SHORT).show();
        }
    }

    private String performNetworkScan() {
        Enumeration<NetworkInterface> networkInterfaces = getNetworkInterfaces();
        if (networkInterfaces == null || !networkInterfaces.hasMoreElements()) {
            handler.post(() -> {
                logs += getString(R.string.no_network_interfaces) + "\n";
                updateLog(logs);
            });
            return null;
        }

        List<String> remainingInterfaces = new ArrayList<>();
        StringBuilder interfaceLog = new StringBuilder(getString(R.string.network_interfaces_found));

        // store strings to avoid calling getString() in background thread
        String ipAddressesLabel = getString(R.string.ip_addresses);
        String noneLabel = getString(R.string.none);

        for (NetworkInterface networkInterface : Collections.list(networkInterfaces)) {
            String interfaceName = networkInterface.getName();
            String interfaceInfo = getInterfaceInfo(networkInterface, ipAddressesLabel, noneLabel);

            interfaceLog.append(String.format("Interface: %s\n%s\n", interfaceName, interfaceInfo));

            if (!interfaceName.equals("swlan0") && !interfaceName.equals("wlan0")) {
                remainingInterfaces.add(interfaceName);
            }
        }

        handler.post(() -> {
            logs += interfaceLog.toString();
            updateLog(logs);
        });

        // Prioritize swlan0, wlan0, and then the remaining interfaces
        for (String interfaceName : new String[]{"swlan0", "wlan0"}) {
            String ip = scanInterface(interfaceName);
            if (ip != null) return ip;
        }

        for (String interfaceName : remainingInterfaces) {
            String ip = scanInterface(interfaceName);
            if (ip != null) return ip;
        }

        return null;
    }

    private String scanInterface(String interfaceName) {
        String ip = getIPAddressForInterface(interfaceName);
        return ip != null ? scanSubnet(ip) : null;
    }

    private String getIPAddressForInterface(String interfaceName) {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.getName().equals(interfaceName)) {
                    for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                        if (inetAddress instanceof java.net.Inet4Address && !inetAddress.isLoopbackAddress()) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP for " + interfaceName, e);
        }
        return null;
    }

    private String scanSubnet(String ipAddress) {
        String subnet = ipAddress.substring(0, ipAddress.lastIndexOf('.') + 1);
        ExecutorService scanExecutor = Executors.newFixedThreadPool(50);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 1; i <= 254; i++) {
            if (stopRequested) {
                scanExecutor.shutdownNow();
                return null;
            }

            String testIp = subnet + i;
            futures.add(scanExecutor.submit(() -> {
                if (stopRequested) return null;

                // Update UI from main thread
                handler.post(() -> statusTextView.setText(getString(R.string.pinging, testIp)));

                return isPortOpen(testIp, port, timeout) ? testIp : null;
            }));
        }

        scanExecutor.shutdown();

        try {
            for (Future<String> future : futures) {
                String result = future.get();
                if (result != null) {
                    stopRequested = true;
                    return result;
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Scan was interrupted", e);
            Thread.currentThread().interrupt(); // Restore interrupt status.
        } catch (ExecutionException e) {
            Log.e(TAG, "Error during subnet scan", e);
        }

        return null;
    }

    private boolean isPortOpen(String ip, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Enumeration<NetworkInterface> getNetworkInterfaces() {
        try {
            return NetworkInterface.getNetworkInterfaces();
        } catch (Exception e) {
            handler.post(() -> {
                logs += getString(R.string.error_message, e.getMessage());
                updateLog(logs);
            });
            return null;
        }
    }

    private String getInterfaceInfo(NetworkInterface networkInterface, String ipAddressesLabel, String noneLabel) {
        StringBuilder info = new StringBuilder(ipAddressesLabel + " ");
        boolean hasAddress = false;
        for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
            if (inetAddress instanceof java.net.Inet4Address && !inetAddress.isLoopbackAddress()) {
                info.append(inetAddress.getHostAddress()).append(" ");
                hasAddress = true;
            }
        }
        return hasAddress ? info.toString() : noneLabel;
    }

    private void updateLog(String log) {
        handler.post(() -> {
            logTextView.setText(log);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void openSettings() {
        // Inflate the settings dialog layout
        View settingsView = getLayoutInflater().inflate(R.layout.settings_dialog, null);
        EditText deviceNameEditText = settingsView.findViewById(R.id.deviceNameEditText);
        EditText portEditText = settingsView.findViewById(R.id.portEditText);
        EditText timeoutEditText = settingsView.findViewById(R.id.timeoutEditText);
        TextView guidanceTextView = settingsView.findViewById(R.id.guidanceTextView);


        // Prepopulate the fields with existing settings if they exist
        deviceNameEditText.setText(sharedPreferences.getString(KEY_DEVICE_NAME, ""));
        portEditText.setText(String.valueOf(sharedPreferences.getInt(KEY_PORT, 8082)));
        timeoutEditText.setText(String.valueOf(sharedPreferences.getInt(KEY_TIMEOUT, 200)));

        // Set guidance text
        guidanceTextView.setText(getString(R.string.guidance_text));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings))
                .setView(settingsView)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    String deviceNameInput = deviceNameEditText.getText().toString().trim();
                    String portInput = portEditText.getText().toString().trim();
                    String timeoutInput = timeoutEditText.getText().toString().trim();

                    if (deviceNameInput.isEmpty() || portInput.isEmpty() || timeoutInput.isEmpty()) {
                        Toast.makeText(this, getString(R.string.all_fields_required), Toast.LENGTH_SHORT).show();
                        openSettings(); // Reopen settings dialog
                        return;
                    }

                    try {
                        int portValue = Integer.parseInt(portInput);
                        int timeoutValue = Integer.parseInt(timeoutInput);

                        if (portValue < 1 || portValue > 65535 || timeoutValue <= 0) {
                            Toast.makeText(this, getString(R.string.invalid_values), Toast.LENGTH_SHORT).show();
                            openSettings(); // Reopen settings dialog
                            return;
                        }

                        // Save settings
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(KEY_DEVICE_NAME, deviceNameInput);
                        editor.putInt(KEY_PORT, portValue);
                        editor.putInt(KEY_TIMEOUT, timeoutValue);
                        editor.apply();

                        // Update variables
                        deviceName = deviceNameInput;
                        port = portValue;
                        timeout = timeoutValue;

                        // Reset foundIpAddress
                        foundIpAddress = null;
                        openTabButton.setEnabled(false);

                        // Update statusTextView
                        statusTextView.setText(getString(R.string.tap_start_scan, deviceName));
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, getString(R.string.invalid_number_format), Toast.LENGTH_SHORT).show();
                        openSettings(); // Reopen settings dialog

                    }
                })

                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    // If settings are not set yet, close the app
                    if (!sharedPreferences.contains(KEY_PORT) || !sharedPreferences.contains(KEY_TIMEOUT)) {
                        finish();
                    }
                })
                .show();
    }
}
