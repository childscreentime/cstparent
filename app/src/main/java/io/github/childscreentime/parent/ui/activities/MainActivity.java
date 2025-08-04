package io.github.childscreentime.parent.ui.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import io.github.childscreentime.parent.core.ParentEncryptionManager;

public class MainActivity extends Activity {
    private static final int DISCOVERY_PORT = 8888;
    private static final String DISCOVERY_MESSAGE = "CST_PARENT_DISCOVERY";
    private static final String EXPECTED_RESPONSE = "CST_CHILD_RESPONSE";
    private static final String COMMAND_PREFIX = "CST_CMD:";
    private static final String RESPONSE_PREFIX = "CST_RESP:";
    
    // SharedPreferences constants
    private static final String PREFS_NAME = "parent_app_prefs";
    private static final String KEY_DEVICES = "discovered_devices";
    private static final String KEY_ADDRESSES = "device_addresses";
    private static final String KEY_DEVICE_IDS = "device_ids";
    private static final String KEY_SELECTED_DEVICE = "selected_device";
    
    private ListView deviceList;
    private Button scanButton;
    private Button getTimeButton;
    private Button lockDeviceButton;
    private Button extendTimeButton;
    private Button sendCommandButton;
    private TextView statusText;
    private TextView advancedToggle;
    private LinearLayout advancedSection;
    private EditText extendMinutesInput;
    private EditText commandInput;
    private ArrayAdapter<String> deviceAdapter;
    private List<String> discoveredDevices;
    private Map<String, String> deviceAddresses; // Maps display name to IP address
    private Map<String, String> deviceIds; // Maps display name to device ID
    private ExecutorService executorService;
    private Handler mainHandler;
    private String selectedDeviceAddress;
    private String selectedDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());
        
        initializeViews();
        setupDeviceList();
        setupButtons();
        
        executorService = Executors.newCachedThreadPool();
        mainHandler = new Handler(Looper.getMainLooper());
        deviceAddresses = new HashMap<>();
        deviceIds = new HashMap<>();
        
        // Load saved device selection
        loadSavedDeviceSelection();
    }
    
    private int getLayoutId() {
        return getResources().getIdentifier("activity_main", "layout", getPackageName());
    }
    
    private void initializeViews() {
        deviceList = findViewById(getResources().getIdentifier("device_list", "id", getPackageName()));
        scanButton = findViewById(getResources().getIdentifier("scan_button", "id", getPackageName()));
        getTimeButton = findViewById(getResources().getIdentifier("get_time_button", "id", getPackageName()));
        lockDeviceButton = findViewById(getResources().getIdentifier("lock_device_button", "id", getPackageName()));
        extendTimeButton = findViewById(getResources().getIdentifier("extend_time_button", "id", getPackageName()));
        sendCommandButton = findViewById(getResources().getIdentifier("send_command_button", "id", getPackageName()));
        statusText = findViewById(getResources().getIdentifier("status_text", "id", getPackageName()));
        advancedToggle = findViewById(getResources().getIdentifier("advanced_toggle", "id", getPackageName()));
        advancedSection = findViewById(getResources().getIdentifier("advanced_section", "id", getPackageName()));
        extendMinutesInput = findViewById(getResources().getIdentifier("extend_minutes_input", "id", getPackageName()));
        commandInput = findViewById(getResources().getIdentifier("command_input", "id", getPackageName()));
    }
    
    private void setupDeviceList() {
        discoveredDevices = new ArrayList<>();
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, discoveredDevices);
        deviceList.setAdapter(deviceAdapter);
        
        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDevice = discoveredDevices.get(position);
            selectedDeviceAddress = deviceAddresses.get(selectedDevice);
            
            // Check if we already have a device ID for this device
            if (deviceIds.containsKey(selectedDevice)) {
                selectedDeviceId = deviceIds.get(selectedDevice);
                onDeviceSelected(selectedDevice);
            } else {
                // Prompt for device ID
                promptForDeviceId(selectedDevice);
            }
        });
    }
    
    private void setupButtons() {
        scanButton.setOnClickListener(v -> performDeviceScan());
        
        getTimeButton.setOnClickListener(v -> sendCommand("GET_TIME_LEFT"));
        lockDeviceButton.setOnClickListener(v -> sendCommand("LOCK_DEVICE"));
        extendTimeButton.setOnClickListener(v -> {
            String minutesStr = extendMinutesInput.getText().toString().trim();
            if (TextUtils.isEmpty(minutesStr)) {
                Toast.makeText(this, "Please enter minutes to extend", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int minutes = Integer.parseInt(minutesStr);
                if (minutes <= 0 || minutes > 1440) {
                    Toast.makeText(this, "Minutes must be between 1 and 1440", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendCommand("EXTEND_TIME:" + minutes);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
            }
        });
        sendCommandButton.setOnClickListener(v -> {
            String command = commandInput.getText().toString().trim();
            if (TextUtils.isEmpty(command)) {
                Toast.makeText(this, "Please enter a command", Toast.LENGTH_SHORT).show();
                return;
            }
            sendCommand(command);
        });
        
        // Advanced section toggle
        advancedToggle.setOnClickListener(v -> toggleAdvancedSection());
        
        // Disable all command buttons until device is selected
        getTimeButton.setEnabled(false);
        lockDeviceButton.setEnabled(false);
        extendTimeButton.setEnabled(false);
        sendCommandButton.setEnabled(false);
    }
    
    private void performDeviceScan() {
        statusText.setText("Scanning for child devices...");
        scanButton.setEnabled(false);
        discoveredDevices.clear();
        deviceAddresses.clear();
        deviceIds.clear(); // Clear saved device IDs
        deviceAdapter.notifyDataSetChanged();
        selectedDeviceAddress = null;
        selectedDeviceId = null;
        getTimeButton.setEnabled(false);
        lockDeviceButton.setEnabled(false);
        extendTimeButton.setEnabled(false);
        sendCommandButton.setEnabled(false);
        
        // Clear saved preferences when scanning
        clearSavedDeviceSelection();
        
        executorService.execute(this::performDeviceDiscovery);
    }
    
    private void sendCommand(String command) {
        if (selectedDeviceId == null || selectedDeviceId.trim().isEmpty()) {
            Toast.makeText(this, "No device selected or device ID missing", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedDeviceAddress == null) {
            Toast.makeText(this, "No device address available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Create encryption manager with the selected device ID
                ParentEncryptionManager encryptionManager = new ParentEncryptionManager(selectedDeviceId.trim());
                
                String encryptedCommand = encryptionManager.encrypt(command, selectedDeviceId.trim());
                String message = COMMAND_PREFIX + encryptedCommand;
                
                mainHandler.post(() -> statusText.setText("Sending command: " + command));
                Log.d("ParentApp", "Sending command: " + command + " to " + selectedDeviceAddress);
                
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(5000);
                
                byte[] messageBytes = message.getBytes();
                InetAddress address = InetAddress.getByName(selectedDeviceAddress);
                DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, address, DISCOVERY_PORT);
                
                socket.send(packet);
                Log.d("ParentApp", "Command sent successfully");
                
                // Listen for response
                byte[] responseBuffer = new byte[1024];
                DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                socket.receive(responsePacket);
                
                String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
                Log.d("ParentApp", "Received response: " + response);
                
                if (response.startsWith(RESPONSE_PREFIX)) {
                    String encryptedResponse = response.substring(RESPONSE_PREFIX.length());
                    String decryptedResponse = encryptionManager.decrypt(encryptedResponse, selectedDeviceId.trim());
                    
                    mainHandler.post(() -> {
                        statusText.setText("Response: " + decryptedResponse);
                        Toast.makeText(MainActivity.this, "Command successful: " + decryptedResponse, Toast.LENGTH_LONG).show();
                    });
                } else {
                    mainHandler.post(() -> {
                        statusText.setText("Unexpected response format");
                        Toast.makeText(MainActivity.this, "Unexpected response format", Toast.LENGTH_SHORT).show();
                    });
                }
                
                socket.close();
                
            } catch (Exception e) {
                Log.e("ParentApp", "Error sending command", e);
                mainHandler.post(() -> {
                    statusText.setText("Error: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Error sending command: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void toggleAdvancedSection() {
        if (advancedSection.getVisibility() == View.GONE) {
            advancedSection.setVisibility(View.VISIBLE);
            advancedToggle.setText("▲ Advanced / Custom Command");
        } else {
            advancedSection.setVisibility(View.GONE);
            advancedToggle.setText("▼ Advanced / Custom Command");
        }
    }
    
    private void disableAllCommandButtons() {
        getTimeButton.setEnabled(false);
        lockDeviceButton.setEnabled(false);
        extendTimeButton.setEnabled(false);
        sendCommandButton.setEnabled(false);
    }
    
    private void enableAllCommandButtons() {
        if (selectedDeviceAddress != null) {
            getTimeButton.setEnabled(true);
            lockDeviceButton.setEnabled(true);
            extendTimeButton.setEnabled(true);
            sendCommandButton.setEnabled(true);
        }
    }
    
    private void performDeviceDiscovery() {
        try {
            android.util.Log.d("ParentApp", "Starting device discovery...");
            
            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(5000); // 5 second timeout
            
            // Send discovery broadcast
            byte[] messageBytes = DISCOVERY_MESSAGE.getBytes();
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(
                messageBytes, messageBytes.length, broadcastAddress, DISCOVERY_PORT);
            
            android.util.Log.d("ParentApp", "Sending broadcast to " + broadcastAddress.getHostAddress() + ":" + DISCOVERY_PORT);
            socket.send(packet);
            android.util.Log.d("ParentApp", "Broadcast sent successfully. Listening for responses...");
            
            // Listen for responses
            byte[] buffer = new byte[1024];
            long startTime = System.currentTimeMillis();
            int responseCount = 0;
            
            while (System.currentTimeMillis() - startTime < 5000) {
                try {
                    DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(responsePacket);
                    responseCount++;
                    
                    String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    String senderIP = responsePacket.getAddress().getHostAddress();
                    
                    android.util.Log.d("ParentApp", "Response #" + responseCount + " from " + senderIP + ": '" + response + "'");
                    
                    if (EXPECTED_RESPONSE.equals(response)) {
                        String deviceIP = responsePacket.getAddress().getHostAddress();
                        String displayName = "Child Device: " + deviceIP;
                        
                        android.util.Log.d("ParentApp", "✓ Valid child device found: " + deviceIP);
                        
                        mainHandler.post(() -> {
                            if (!discoveredDevices.contains(displayName)) {
                                discoveredDevices.add(displayName);
                                deviceAddresses.put(displayName, deviceIP);
                                deviceAdapter.notifyDataSetChanged();
                            }
                        });
                    } else {
                        android.util.Log.d("ParentApp", "✗ Unexpected response (expected: '" + EXPECTED_RESPONSE + "')");
                    }
                } catch (SocketTimeoutException e) {
                    // Continue listening until total timeout
                }
            }
            
            android.util.Log.d("ParentApp", "Discovery completed. Responses received: " + responseCount + ", Devices found: " + discoveredDevices.size());
            
            socket.close();
            
            android.util.Log.d("ParentApp", "Socket closed. Final device count: " + discoveredDevices.size());
            
            mainHandler.post(() -> {
                statusText.setText(discoveredDevices.isEmpty() ? 
                    "No child devices found. Check WiFi and child app." : 
                    "Found " + discoveredDevices.size() + " device(s). Select one and enter Device ID.");
                scanButton.setEnabled(true);
            });
            
        } catch (Exception e) {
            android.util.Log.e("ParentApp", "Discovery error: " + e.getMessage(), e);
            mainHandler.post(() -> {
                statusText.setText("Discovery failed: " + e.getMessage());
                scanButton.setEnabled(true);
            });
        }
    }
    
    private String formatResponse(String response) {
        // Format responses to be more user-friendly
        if (response.startsWith("TIME_LEFT|")) {
            String[] parts = response.split("\\|");
            if (parts.length >= 4) {
                try {
                    long remainingMinutes = Long.parseLong(parts[1]);
                    String status = parts[2];
                    long totalMinutes = Long.parseLong(parts[3]);
                    
                    return String.format("Time Left: %d min (Total: %d min) - Status: %s", 
                        remainingMinutes, totalMinutes, status);
                } catch (NumberFormatException e) {
                    return response;
                }
            }
        } else if (response.startsWith("TIME_EXTENDED|")) {
            return "✓ " + response.substring("TIME_EXTENDED|".length());
        } else if (response.startsWith("DEVICE_LOCKED|")) {
            String message = response.substring("DEVICE_LOCKED|".length());
            return message.startsWith("ERROR") ? "✗ " + message : "✓ " + message;
        } else if (response.startsWith("ERROR|")) {
            return "✗ " + response.substring("ERROR|".length());
        }
        
        return response;
    }
    
    private void promptForDeviceId(String selectedDevice) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Device ID");
        builder.setMessage("Please enter the Device ID for " + selectedDevice);
        
        final EditText input = new EditText(this);
        input.setHint("Device ID");
        builder.setView(input);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            String deviceId = input.getText().toString().trim();
            if (!deviceId.isEmpty()) {
                selectedDeviceId = deviceId;
                deviceIds.put(selectedDevice, deviceId);
                saveDeviceSelection(selectedDevice, selectedDeviceAddress, deviceId);
                onDeviceSelected(selectedDevice);
            } else {
                Toast.makeText(this, "Device ID cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void onDeviceSelected(String selectedDevice) {
        Toast.makeText(this, "Selected: " + selectedDevice, Toast.LENGTH_SHORT).show();
        
        // Enable command sending once device is selected
        getTimeButton.setEnabled(true);
        lockDeviceButton.setEnabled(true);
        extendTimeButton.setEnabled(true);
        sendCommandButton.setEnabled(true);
    }
    
    private void loadSavedDeviceSelection() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedDevice = prefs.getString(KEY_SELECTED_DEVICE, "");
        
        if (!savedDevice.isEmpty()) {
            // Extract stored data
            String[] parts = savedDevice.split("\\|");
            if (parts.length == 3) {
                String deviceName = parts[0];
                String deviceAddress = parts[1];
                String deviceId = parts[2];
                
                // Restore selection
                selectedDeviceAddress = deviceAddress;
                selectedDeviceId = deviceId;
                deviceAddresses.put(deviceName, deviceAddress);
                deviceIds.put(deviceName, deviceId);
                
                // Add to discovered devices list
                discoveredDevices.add(deviceName);
                deviceAdapter.notifyDataSetChanged();
                
                onDeviceSelected(deviceName);
            }
        }
    }
    
    private void saveDeviceSelection(String deviceName, String deviceAddress, String deviceId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Store as "deviceName|deviceAddress|deviceId"
        String deviceData = deviceName + "|" + deviceAddress + "|" + deviceId;
        editor.putString(KEY_SELECTED_DEVICE, deviceData);
        editor.apply();
    }
    
    private void clearSavedDeviceSelection() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_SELECTED_DEVICE);
        editor.apply();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
