# WiFi Network Discovery Troubleshooting Guide

## Common Issues When Parent Can't Find Child Device

### 1. Network Configuration Issues

**Both devices must be on the same WiFi network:**
- Check WiFi settings on both devices
- Ensure they show the same network name (SSID)
- Both should have IP addresses in the same subnet (e.g., 192.168.1.x)

**Router/Network Issues:**
- Some routers block device-to-device communication (AP Isolation)
- Guest networks often isolate devices
- Corporate/public WiFi may block UDP broadcasts
- Check router settings for "AP Isolation" or "Client Isolation" - disable it

### 2. App Configuration Issues

**Child App Requirements:**
- Must be running and actively listening on port 8888
- Must respond with exact string: "CST_CHILD_RESPONSE"
- Must have network permissions in AndroidManifest.xml

**Parent App Requirements:**
- Must send broadcast to 255.255.255.255:8888
- Must send exact string: "CST_PARENT_DISCOVERY"
- Must listen for responses

### 3. Device/Android Issues

**Firewall/Security:**
- Some Android versions block UDP broadcasts
- Check if devices have any firewall apps
- Battery optimization might affect background networking

**WiFi Power Management:**
- Android may put WiFi to sleep
- Check WiFi "Keep WiFi on during sleep" setting

### 4. Testing Steps

**Step 1: Check Network Connectivity**
```bash
# On each device, check IP address in WiFi settings
# They should be in same subnet (e.g., 192.168.1.100 and 192.168.1.101)
```

**Step 2: Test with Network Scanner App**
- Install a network scanner app on parent device
- Scan for devices on the network
- Verify child device IP is visible

**Step 3: Check Android Logs**
- Connect parent device to computer
- Run: `adb logcat | grep "ParentApp"`
- Look for discovery messages and errors

**Step 4: Test with Simple Ping**
- If you know child device IP, try pinging from parent device
- Some network apps can test UDP connectivity

### 5. Expected Log Output (Parent App)

```
D/ParentApp: Starting device discovery...
D/ParentApp: Sending discovery message to 255.255.255.255:8888
D/ParentApp: Discovery message sent successfully
D/ParentApp: Listening for responses for 5 seconds...
D/ParentApp: Received response #1 from 192.168.1.101: 'CST_CHILD_RESPONSE'
D/ParentApp: Valid child response found from: 192.168.1.101
D/ParentApp: Added device to list: Child Device: 192.168.1.101
D/ParentApp: Discovery completed. Total responses received: 1
```

### 6. Quick Fix Attempts

1. **Restart both apps**
2. **Restart WiFi on both devices**  
3. **Move devices closer to router**
4. **Try different WiFi networks**
5. **Check if hotspot works** (create hotspot on one device, connect other)

### 7. Alternative Testing Method

If WiFi discovery fails, try USB debugging connection:
- Connect child device via USB
- Use `adb port forwarding`
- Test locally first
