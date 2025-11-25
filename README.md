# Oelo Lights Hubitat Driver

Custom Hubitat driver for controlling Oelo Lights outdoor lighting controllers via HTTP REST API.

## Features

- **Multi-Zone Control**: Control up to 6 independent zones (requires separate virtual device per zone)
- **Pattern Capture**: Capture patterns created/edited in the Oelo app and save them for future use (up to 200 patterns)
- **Pattern Types**: Support for spotlight plans and non-spotlight plans with automatic type detection
- **Spotlight Plans**: Customize which LEDs are active in spotlight patterns via Spotlight Plan Lights setting
- **Simple Commands**: Easy-to-use commands for setting patterns and turning lights on/off
- **Auto-Polling**: Automatic status polling with configurable intervals
- **Pattern Validation**: Full validation of captured patterns ensures reliability
- **No Authentication Required**: Simple HTTP API with no credentials needed

## Installation

### Method 1: Hubitat Package Manager (HPM) - Recommended

1. Ensure [Hubitat Package Manager](https://github.com/HubitatCommunity/HubitatPublic/tree/master/apps/Package%20Manager) is installed on your hub
2. Open HPM and select "Install"
3. Search for "Oelo Lights"
4. Follow the prompts to complete installation

### Method 2: Manual Installation

1. Open your Hubitat web interface
2. Navigate to **Drivers Code** in the menu
3. Click **+ New Driver**
4. Click **Import** and paste this URL:
   ```
   https://raw.githubusercontent.com/curtiside/oelo-lights-hubitat/main/drivers/oelo-lights-zone.groovy
   ```
5. Click **Import** and then **Save**

## Configuration

### Required Settings

1. **Controller IP Address** (Required)
   - IP address of the Oelo controller on your local network
   - Format: IPv4 address (e.g., `192.168.1.100`)
   - Automatically set when discovered via `scanForController()` command, or can be manually entered
   - Driver validates IP by attempting to connect to `/getController` endpoint
   - See [Finding Controller IP](#finding-controller-ip) below

2. **Zone Number** (Required)
   - Zone number (1-6) that this driver instance controls
   - Each zone requires a separate virtual device instance
   - Range: 1-6

### Optional Settings

3. **Subnet to Scan** (Optional)
   - Subnet prefix for controller discovery (e.g., '192.168.1' or '10.16.1')
   - Leave empty to use your Hubitat hub's subnet (shown in description)
   - Used by `scanForController()` command

4. **Poll Interval** (Optional, Default: 300 seconds)
   - How often to poll controller status
   - Range: 10-3600 seconds
   - Recommended: 300 seconds (5 minutes)

5. **Enable Auto Polling** (Optional, Default: Enabled)
   - Automatically poll controller status
   - Disable to reduce network traffic

6. **Patterns** (Optional, Up to 200)
   - Capture and manage up to 200 patterns
   - Patterns captured via `getPattern()` command
   - Patterns can be renamed and deleted via preferences
   - See [Patterns](#patterns) below for details

7. **Spotlight Plan Lights** (Optional)
   - Comma-delimited list of LED indices for spotlight plans (e.g., "1,2,3,4,8,9,10,11")
   - Automatically normalized (duplicates removed, sorted)
   - Default includes common spotlight LED positions

8. **Maximum LEDs per Zone** (Optional, Default: 500)
   - Maximum number of LEDs in this zone

9. **Verify Commands** (Optional, Default: Disabled)
   - Verify commands by checking controller status after sending
   - Polls controller to ensure command was applied

10. **Verification Retries** (Optional, Default: 3)
    - Number of times to retry verification
    - Range: 1-10

11. **Verification Delay** (Optional, Default: 2 seconds)
    - Seconds to wait between verification attempts
    - Range: 1-10 seconds

12. **Verification Timeout** (Optional, Default: 30 seconds)
    - Maximum time to wait for verification
    - Range: 10-120 seconds

13. **Command Timeout** (Optional, Default: 10 seconds)
    - HTTP request timeout
    - Range: 5-30 seconds

14. **Enable Debug Logging** (Optional, Default: Disabled)
    - Enable detailed logging for troubleshooting

### Authentication & Security

**No Credentials Required**

The Oelo Lights controller uses an open HTTP API with no authentication.

- No username/password required
- No API keys or tokens
- No encryption (HTTP only, not HTTPS)
- Simple GET requests to controller IP

**Security Considerations:**
- Controller should be on a trusted local network
- Consider firewall rules to restrict access
- Controller is accessible to anyone on the network who knows the IP

### Finding Controller IP

#### Method 1: Controller Discovery (Recommended)

The driver includes a network scanning feature to discover Oelo controllers:

1. **Use `scanForController()` Command:**
   - Open device preferences
   - Click the **Scan for Controller** button
   - The driver will scan your network subnet
   - When found, the controller IP address is **automatically set** in the preferences
   - The discovered IP address will be:
     - **Logged in the device logs**: `=== CONTROLLER DISCOVERED ===` followed by `IP Address: x.x.x.x`
     - Shown in the **Controller IP Address** preference description: `(Discovered: x.x.x.x)`
     - **Automatically entered** into the Controller IP Address field
   - The device will work immediately with the discovered IP address
   - **Note:** Refresh the preferences page to see the updated IP address in the field (the device works immediately even before refresh)

2. **Subnet Configuration:**
   - The **Subnet to Scan** field shows your Hubitat hub's subnet in the description if empty
   - You can manually enter a subnet if needed (e.g., '192.168.1' or '10.16.1')
   - Leave empty to use the hub's subnet during scanning

3. **Stop Scanning:**
   - Use the **Stop Scan** command to stop an active scan

**How Network Scanning Works:**
- Scans IP addresses in the configured subnet (e.g., 192.168.1.1-254)
- Tests each IP with HTTP GET to `/getController`
- Identifies Oelo controllers by checking for valid JSON response
- Logs progress every 10 IP addresses scanned
- When controller is found, IP address is automatically set in preferences
- Device is automatically initialized with the discovered IP address

**Limitations:**
- Scanning can be slow (tests each IP sequentially)
- May trigger security alerts on some networks
- Requires controller to be powered on and responding
- Works best when controller and Hubitat are on same subnet

**Recommendation:**
- Try `scanForController()` first for easiest setup
- Fall back to manual methods if scanning doesn't find the controller
- Use manual IP entry if you know the IP address

#### Method 2: Manual Methods

If automatic discovery doesn't work, you can manually find the IP address:

**Oelo Evolution App:**
1. Open the Oelo Evolution app on your smartphone/tablet
2. Navigate to Settings or Device Information
3. Find the IP address listed there

**Router Admin Interface:**
1. Log into your router's web interface
2. Navigate to "Connected Devices" or "DHCP Client List"
3. Look for device named "Oelo" or similar
4. Note the IP address assigned

**Network Scanner Tools:**
Use network scanning software to scan your local network:
- **Fing** (mobile app)
- **Angry IP Scanner** (desktop)
- **nmap** (command line): `nmap -sn 192.168.1.0/24`
- **Advanced IP Scanner** (Windows)

Look for devices responding on port 80 (HTTP) that return JSON from `/getController`

**Controller Display:**
Some Oelo controllers may display their IP address on a built-in screen or LED display.

### IP Address Validation

The driver validates the IP address by:

1. **Format Validation**
   - Checks if input is a valid IPv4 address format
   - Rejects invalid formats immediately

2. **Connection Test**
   - Attempts HTTP GET request to `http://{IP}/getController`
   - Timeout: 10 seconds (configurable)
   - Expects HTTP 200 response

3. **Response Validation**
   - Verifies response is valid JSON
   - Checks response is an array (expected format)
   - Confirms device is an Oelo controller

**If validation fails:**
- Error message displayed to user
- Driver will not initialize until valid IP is provided
- User must correct IP address and try again

### Configuration Workflow

#### Initial Setup

1. **Create Virtual Device**
   - Hubitat → Devices → Add Virtual Device
   - Select "Oelo Lights Zone" driver
   - Configure device:
     - Use `scanForController()` command to automatically discover and set Controller IP Address, or manually enter IP address
     - Set Zone Number (1-6)
     - Configure optional settings as needed

3. **Validate Connection**
   - Driver attempts to connect on initialization
   - Check device status - should show "Online"
   - Test by turning zone on/off

4. **Repeat for Additional Zones**
   - Create separate virtual device for each zone (1-6)
   - Use same Controller IP Address
   - Set different Zone Number for each
   - **Tip**: You can also create multiple devices for the same zone, each with a different saved pattern, to quickly switch between patterns via automations or scenes

#### Reconfiguration

- IP address can be changed in device preferences
- Driver re-validates connection when preferences are updated
- Zone number can be changed (creates new device instance)

### Configuration Summary

| Configuration Item | Required | Default | Notes |
|-------------------|----------|---------|-------|
| Controller IP Address | ✅ Yes | None | Automatically set by scan command, or manually entered |
| Zone Number | ✅ Yes | 1 | Range: 1-6 |
| Subnet to Scan | ❌ No | Empty | Shows hub subnet in description if empty |
| Poll Interval | ❌ No | 300 sec | Range: 10-3600 sec |
| Enable Auto Polling | ❌ No | Enabled | Can be disabled |
| Patterns | ❌ No | None | Up to 200 patterns |
| Spotlight Plan Lights | ❌ No | Default list | Comma-delimited LED indices |
| Maximum LEDs per Zone | ❌ No | 500 | Maximum LEDs in zone |
| Verify Commands | ❌ No | Disabled | Verify commands after sending |
| Verification Retries | ❌ No | 3 | Range: 1-10 |
| Verification Delay | ❌ No | 2 sec | Range: 1-10 sec |
| Verification Timeout | ❌ No | 30 sec | Range: 10-120 sec |
| Command Timeout | ❌ No | 10 sec | Range: 5-30 sec |
| Enable Debug Logging | ❌ No | Disabled | Enable for troubleshooting |
| **Credentials** | ❌ **No** | **None** | **No authentication required** |

**Key Points:**
- ✅ No credentials/authentication required
- ✅ Controller discovery via `scanForController()` command
- ✅ Manual IP entry also supported
- ✅ IP validation on connection
- ✅ Simple HTTP API (no encryption)
- ✅ Up to 200 patterns can be captured and managed

## Usage

### Commands

The driver provides the following commands (ordered as they appear in Hubitat):

- **`on()`**: Turn on lights using the last used pattern or selected pattern
- **`off()`**: Turn off the lights
- **`applyPattern()`**: Apply pattern chosen from Pattern Selection dropdown
- **`getPattern()`**: Capture the current pattern from the controller and save it
- **`refresh()`**: Get current state from controller
- **`scanForController()`**: Scan network to discover Oelo controller IP address
- **`stopScan()`**: Stop the controller discovery scan

### Using Pattern Selection

1. **Select Pattern in Preferences**:
   - Go to device preferences
   - **Pattern Selection**: Choose from your configured patterns
   - Save preferences

2. **Execute Command**:
   - Use `applyPattern()` command to apply selected pattern
   - Commands appear in device tile or can be called from automations

### Patterns

Capture and save patterns created/edited in the Oelo app:

**Workflow:**
1. **Edit Pattern in Oelo App**: Use the Oelo Evolution app to create or modify a pattern on your controller
2. **Set Pattern on Controller**: Apply the pattern to your zone using the Oelo app
3. **Capture in Hubitat**: Use the `getPattern()` command to capture the current pattern from the controller
4. **Pattern Saved**: The pattern is automatically saved with a stable ID (generated from pattern type and parameters) and an initial display name
5. **Use Saved Pattern**: Select the captured pattern from **Pattern Selection** dropdown and use `applyPattern()` command

**Pattern Types:**

The driver supports two types of patterns:

- **Spotlight Plans**: Patterns with specific LEDs turned on and all others off
  - Automatically detected when `patternType=spotlight`
  - Can be customized via **Spotlight Plan Lights** setting to control which LEDs are active
  - Uses colors from the original captured pattern for the specified LEDs

- **Non-Spotlight Plans**: All other pattern types (march, stationary, river, chase, twinkle, split, fade, sprinkle, takeover, streak, bolt, custom, etc.)
  - Standard pattern types with various effects
  - Existing behavior unchanged

**Pattern Identification:**
- **Pattern ID**: Stable identifier generated from pattern type, key parameters, and first non-zero RGB color values (e.g., "march_dirR_spd3_6colors_rgb255-92-0")
  - Same parameters always generate the same ID
  - Includes pattern type, direction, speed, number of colors, and RGB color values
  - Prevents duplicate patterns automatically
  - Cannot be changed (stays stable even if pattern is renamed)
  - Displayed in parentheses next to pattern name in dropdowns (e.g., "My Pattern (march_dirR_spd3_6colors_rgb255-92-0)")
- **Pattern Name**: Display name shown in dropdowns (initially same as ID, but editable)
  - Can be renamed via **Select Pattern to Rename** and **New Pattern Name** preferences
  - Renaming doesn't affect the pattern ID
- **Plan Type**: Automatically detected and stored (spotlight or non-spotlight)
  - Used internally for pattern processing

**Pattern Management:**
- Up to **200 patterns** can be stored per device
- **Duplicate Prevention**: Patterns with identical parameters are automatically treated as the same pattern
- **Rename Patterns**: Use **Select Pattern to Rename** dropdown and **New Pattern Name** text field in device preferences, then save
- **Delete Patterns**: Use the **Delete Pattern** dropdown in device preferences, then save
- Patterns appear in **Pattern Selection** dropdown for easy selection
- Pattern ID is displayed in parentheses next to pattern name in dropdowns (e.g., "My Pattern (march_dirR_spd3_6colors_rgb255-92-0)")

**Spotlight Plan Customization:**
The Oelo controller only returns 40 lights from the getController command.  As a result, spotlight plans are not fully represented in the response.  To counter this limitation, the Spotlight Plan Lights can be set with a list of the lights that should be turned on.  

For spotlight plans, you can customize which LEDs are active:

1. **Capture Spotlight Plan**: Use `getPattern()` to capture a spotlight pattern from the controller
2. **Configure Spotlight Plan Lights**: In device preferences, set **Spotlight Plan Lights** to a comma-delimited list of LED indices (1-based, e.g., "1,2,3,4,8,9,10,11")
   - The setting is automatically normalized (duplicates removed, sorted, invalid indices skipped)
   - Current normalized value is displayed in the preference description
   - Default value includes common spotlight LED positions
3. **Automatic Modification**: When you use a spotlight plan, only the LEDs specified in **Spotlight Plan Lights** will be turned on, using the colors from the original captured pattern
4. **Setting Changes**: If you change **Spotlight Plan Lights**, all saved spotlight plans are automatically updated to use the new LED list

**Note**: The Oelo app is the primary tool for creating and editing patterns. Hubitat captures these patterns so they can be reused in automations and scenes. All captured patterns are validated before being saved to ensure reliability.

### Example Automations

**Example 1: Turn on pattern at sunset**
- Create/edit pattern in Oelo Evolution app and apply it to the zone
- In Hubitat, use `getPattern()` command to capture the pattern
- Create Rule Machine rule
- Trigger: Sunset
- Action: Call `applyPattern()` command (after selecting the captured pattern in preferences)

**Example 2: Turn off lights at midnight**
- Create Rule Machine rule
- Trigger: Time (12:00 AM)
- Action: Call `off()` command

**Example 3: Capture and reuse a favorite pattern**
- Set up your favorite pattern in the Oelo app
- Use `getPattern()` command in Hubitat to save it
- The pattern is now available in the Pattern Selection dropdown for use in automations

## Troubleshooting

### "Controller IP address not configured"
- **Solution**: Enter IP address in device preferences → Controller Settings

### "Invalid zone number"
- **Solution**: Zone number must be 1-6

### "Cannot connect to controller"
- **Possible causes:**
  - Incorrect IP address
  - Controller is offline
  - Network connectivity issues
  - Firewall blocking connection
  - Controller on different network/VLAN
- **Solutions:**
  - Verify IP address is correct
  - Ping controller: `ping Controller_IP_Address`
  - Check controller is powered on
  - Verify Hubitat and controller are on same network
  - Check firewall rules

### "Device responded but doesn't appear to be an Oelo controller"
- **Possible causes:**
  - IP address points to different device
  - Controller firmware version incompatible
- **Solutions:**
  - Verify correct IP address
  - Test manually: `curl http://{IP}/getController`
  - Should return JSON array

### Patterns not appearing in dropdown
- Ensure patterns have been captured using `getPattern()` command
- Try refreshing device preferences page
- Check device logs for any errors

### Commands not working
- Verify pattern is selected in Pattern Selection dropdown
- Check device logs for error messages
- Enable Debug Logging for detailed information
- Verify controller IP and zone number are correct

### Device shows offline
- Check controller IP address is correct
- Verify network connectivity
- Try `refresh()` command
- Check Hubitat logs for connection errors

## Attributes & Capabilities

The driver exposes various attributes for monitoring device state and includes the `Switch` capability for use in automations. See the driver code documentation for complete technical details.

## Requirements

- Hubitat Elevation hub (firmware 2.1.9 or later)
- Oelo Lights controller on local network
- Controller IP address (can be manually entered or discovered via `scanForController()`)

## Additional Documentation

For technical implementation details, protocol specifications, and developer information, see the code documentation in the driver file header.

## License

MIT License

## Credits

- Based on [Oelo Lights Home Assistant Integration](https://github.com/Cinegration/Oelo_Lights_HA)
- Original Python implementation by Cinegration

## Support

For issues, questions, or contributions:

- **GitHub Issues**: [Report an issue](https://github.com/curtiside/oelo-lights-hubitat/issues)
- **Hubitat Community**: [Hubitat Community Forum](https://community.hubitat.com)

