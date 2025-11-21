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

1. **Controller IP Address**
   - Enter the IPv4 address of your Oelo controller (e.g., `192.168.1.100`)
   - Must be manually entered (no auto-discovery)
   - See [Finding Controller IP](#finding-controller-ip) below

2. **Zone Number**
   - Select zone number (1-6) that this device instance controls
   - Each zone requires a separate virtual device

### Optional Settings

- **Poll Interval**: How often to poll controller status (default: 30 seconds)
- **Auto Polling**: Automatically poll controller status (default: enabled)
- **Patterns**: Capture and manage up to 200 patterns (see [Patterns](#patterns) below)
- **Spotlight Plan Lights**: Comma-delimited list of LED indices to turn on for spotlight plans (e.g., "1,2,3,4,8,9,10,11")
- **Max LEDs**: Maximum number of LEDs in this zone (default: 500)
- **Debug Logging**: Enable detailed logging for troubleshooting
- **Command Timeout**: HTTP request timeout (default: 10 seconds)

### Finding Controller IP

The Oelo controller does not support automatic discovery. You must manually find the IP address using one of these methods:

- **Oelo Evolution App**: Settings → Device Info
- **Router Admin**: Check "Connected Devices" or "DHCP Client List"
- **Network Scanner**: Use Fing, Angry IP Scanner, or nmap
- **Controller Display**: Some controllers display IP on built-in screen

See [CONFIGURATION.md](./CONFIGURATION.md) for detailed configuration instructions.

## Usage

### Creating Virtual Devices

1. Navigate to **Devices** → **Add Virtual Device**
2. Select **Oelo Lights Zone** as the driver
3. Configure device:
   - **Controller IP Address**: Your Oelo controller IP
   - **Zone Number**: 1-6 (one per device)
   - Configure optional settings as needed
4. Repeat for each zone (1-6) you want to control

### Commands

The driver provides the following commands:

- **`setPattern()`**: Set a pattern chosen from the Pattern Selection dropdown
- **`getPattern()`**: Capture the current pattern from the controller and save it
- **`off()`**: Turn off the lights
- **`refresh()`**: Get current state from controller

### Using Pattern Selection

1. **Select Pattern in Preferences**:
   - Go to device preferences
   - **Pattern Selection**: Choose from your configured patterns
   - Save preferences

2. **Execute Command**:
   - Use `setPattern()` command to apply selected pattern
   - Commands appear in device tile or can be called from automations

### Patterns

Capture and save patterns created/edited in the Oelo app:

**Workflow:**
1. **Edit Pattern in Oelo App**: Use the Oelo Evolution app to create or modify a pattern on your controller
2. **Set Pattern on Controller**: Apply the pattern to your zone using the Oelo app
3. **Capture in Hubitat**: Use the `getPattern()` command to capture the current pattern from the controller
4. **Pattern Saved**: The pattern is automatically saved with a stable ID (generated from pattern type and parameters) and an initial display name
5. **Use Saved Pattern**: Select the captured pattern from **Pattern Selection** dropdown and use `setPattern()` command

**Pattern Types:**

The driver supports two types of patterns:

- **Spotlight Plans**: Patterns with specific LEDs turned on and all others off
  - Automatically detected when `patternType=spotlight`
  - Can be customized via **Spotlight Plan Lights** setting to control which LEDs are active
  - Uses colors from the original captured pattern for the specified LEDs
  - Displayed with `[spotlight]` suffix in pattern lists

- **Non-Spotlight Plans**: All other pattern types (march, stationary, river, chase, twinkle, split, fade, sprinkle, takeover, streak, bolt, custom, etc.)
  - Standard pattern types with various effects
  - Displayed with `[non-spotlight]` suffix in pattern lists
  - Existing behavior unchanged

**Pattern Identification:**
- **Pattern ID**: Stable identifier generated from pattern type and key parameters (e.g., "spotlight" or "spotlight_156colors")
  - Same parameters always generate the same ID
  - Prevents duplicate patterns automatically
  - Cannot be changed (stays stable even if pattern is renamed)
- **Pattern Name**: Display name shown in dropdowns (initially same as ID, but editable)
  - Can be renamed via **Select Pattern to Rename** and **New Pattern Name** preferences
  - Renaming doesn't affect the pattern ID or prevent duplicates
- **Plan Type**: Automatically detected and stored (spotlight or non-spotlight)
  - Shown in pattern lists and dropdowns for easy identification
  - Existing patterns without plan type are automatically evaluated when first used

**Pattern Management:**
- Up to **200 patterns** can be stored per device
- **Duplicate Prevention**: If a pattern with the same ID already exists, it will be updated with new parameters (keeps your custom name if you renamed it)
- **Rename Patterns**: Use **Select Pattern to Rename** dropdown and **New Pattern Name** text field in device preferences, then save
- **Delete Patterns**: Use the **Delete Pattern** dropdown in device preferences, then save
- Patterns appear in **Pattern Selection** dropdown for easy selection
- Pattern type is displayed alongside pattern name (e.g., "My Pattern [spotlight]")

**Spotlight Plan Customization:**

For spotlight plans, you can customize which LEDs are active:

1. **Capture Spotlight Plan**: Use `getPattern()` to capture a spotlight pattern from the controller
2. **Configure Spotlight Plan Lights**: In device preferences, set **Spotlight Plan Lights** to a comma-delimited list of LED indices (1-based, e.g., "1,2,3,4,8,9,10,11")
   - The setting is automatically normalized (duplicates removed, sorted, invalid indices skipped)
   - Current normalized value is displayed in the preference description
   - Default value includes common spotlight LED positions
3. **Automatic Modification**: When you use a spotlight plan, only the LEDs specified in **Spotlight Plan Lights** will be turned on, using the colors from the original captured pattern
4. **Setting Changes**: If you change **Spotlight Plan Lights**, all saved spotlight plans are automatically updated to use the new LED list

**Pattern Validation:**

All captured patterns are validated before being saved:
- Colors string validation (correct number of RGB triplets, valid RGB values)
- Complete pattern URL validation (all required parameters present, valid ranges)
- Full string logged if validation fails for debugging

**Note**: The Oelo app is the primary tool for creating and editing patterns. Hubitat captures these patterns so they can be reused in automations and scenes. The pattern ID ensures that patterns with identical parameters are treated as the same pattern, even if you rename them.

### Example Automations

**Example 1: Turn on pattern at sunset**
- Create/edit pattern in Oelo Evolution app and apply it to the zone
- In Hubitat, use `getPattern()` command to capture the pattern
- Create Rule Machine rule
- Trigger: Sunset
- Action: Call `setPattern()` command (after selecting the captured pattern in preferences)

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

### "Cannot connect to controller"
- Verify IP address is correct
- Check controller is powered on and connected to network
- Verify Hubitat and controller are on same network
- Test connectivity: `ping <controller-ip>`
- Check firewall rules

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

## Attributes

The driver exposes the following attributes:

- `zone`: Zone number (1-6)
- `controllerIP`: Controller IP address
- `lastCommand`: Last command URL sent
- `currentPattern`: Current pattern string from controller (e.g., "march", "off", "custom")
- `effectName`: Current pattern name if it matches a saved pattern (empty if not found or off)
- `availablePatterns`: Comma-separated list of all saved patterns with their plan types
- `verificationStatus`: Command verification status (if enabled)
- `driverVersion`: Current driver version
- `switch`: Current switch state ("on" or "off")

## Capabilities

- `Refresh`: Standard Hubitat refresh capability

## Requirements

- Hubitat Elevation hub (firmware 2.1.9 or later)
- Oelo Lights controller on local network
- Controller IP address (must be manually entered)

## Protocol

The driver communicates with Oelo controllers via HTTP REST API:

- **Status**: `GET http://{IP}/getController` - Returns JSON array of zone statuses
- **Commands**: `GET http://{IP}/setPattern?{params}` - Sets pattern/color for zones

See [PROTOCOL_SUMMARY.md](./PROTOCOL_SUMMARY.md) for detailed protocol documentation.

## Additional Documentation

- [CONFIGURATION.md](./CONFIGURATION.md) - Complete configuration guide
- [PROTOCOL_SUMMARY.md](./PROTOCOL_SUMMARY.md) - Protocol details
- [VALIDATION.md](./VALIDATION.md) - Driver validation guidelines
- [PUBLISHING.md](./PUBLISHING.md) - Publishing information

## License

MIT License

## Credits

- Based on [Oelo Lights Home Assistant Integration](https://github.com/Cinegration/Oelo_Lights_HA)
- Original Python implementation by Cinegration

## Support

For issues, questions, or contributions:

- **GitHub Issues**: [Report an issue](https://github.com/curtiside/oelo-lights-hubitat/issues)
- **Hubitat Community**: [Hubitat Community Forum](https://community.hubitat.com)

## Version History

### Version 0.9.0 (Current)
- Pattern capture and management: Capture up to 200 patterns from the Oelo controller
- Pattern types: Automatic detection of spotlight vs non-spotlight plans
- Pattern management: Rename and delete patterns via device preferences
- Pattern validation: Full validation ensures captured patterns are valid before saving
- Spotlight plan support: Customize which LEDs are active in spotlight patterns to deal with 40 light getController issue
- Auto-polling: Configurable automatic status polling
- Command verification: Optional feature (disabled by default) that polls the controller after sending commands to verify they were applied successfully

### Previous Versions
See [packageManifest.json](./packageManifest.json) for complete version history and release notes.
