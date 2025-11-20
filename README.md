# Oelo Lights Hubitat Driver

Custom Hubitat driver for controlling Oelo Lights outdoor lighting controllers via HTTP REST API.

## Features

- **Multi-Zone Control**: Control up to 6 independent zones (requires separate virtual device per zone)
- **77 Predefined Patterns**: Holiday and seasonal patterns (Christmas, Halloween, Fourth of July, etc.)
- **Custom Patterns**: Configure up to 6 custom patterns with your own names
- **Simple Commands**: Easy-to-use commands for setting patterns and turning lights on/off
- **Auto-Polling**: Automatic status polling with configurable intervals
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
- **Custom Patterns**: Configure up to 6 custom patterns (see [Custom Patterns](#custom-patterns) below)
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

- **`setCustomPattern()`**: Set a custom pattern chosen from the Custom Pattern Selection dropdown
- **`setStandardPattern()`**: Set a standard/predefined pattern chosen from the Standard Pattern Selection dropdown
- **`off()`**: Turn off the lights
- **`refresh()`**: Get current state from controller

### Using Pattern Selection

1. **Select Pattern in Preferences**:
   - Go to device preferences
   - **Custom Pattern Selection**: Choose from your configured custom patterns
   - **Standard Pattern Selection**: Choose from 77 predefined patterns
   - Save preferences

2. **Execute Command**:
   - Use `setCustomPattern()` command to apply selected custom pattern
   - Use `setStandardPattern()` command to apply selected standard pattern
   - Commands appear in device tile or can be called from automations

### Custom Patterns

Configure up to 6 custom patterns per device:

1. Go to device preferences → **Custom Patterns** section
2. Enter pattern name for each custom pattern (e.g., "Front Porch Red")
3. Pattern names are used as `patternType` - controller has pattern settings stored
4. Custom patterns appear in **Custom Pattern Selection** dropdown
5. Use `setCustomPattern()` command after selecting a custom pattern

**Note**: Custom patterns use the pattern name directly - the controller stores the pattern configuration.

### Standard Patterns

77 predefined patterns are available, organized by category:

- **Holiday Patterns**: Christmas, Halloween, Fourth of July, Thanksgiving, etc.
- **Seasonal Patterns**: Various themed patterns for different occasions
- **Pattern Types**: march, stationary, river, chase, twinkle, split, fade, sprinkle, takeover, streak, bolt

Standard patterns appear in **Standard Pattern Selection** dropdown. Use `setStandardPattern()` command after selecting a pattern.

### Example Automations

**Example 1: Turn on Christmas pattern at sunset**
- Create Rule Machine rule
- Trigger: Sunset
- Action: Call `setStandardPattern()` command (after selecting "Christmas: Candy Cane Glimmer" in preferences)

**Example 2: Turn off lights at midnight**
- Create Rule Machine rule
- Trigger: Time (12:00 AM)
- Action: Call `off()` command

**Example 3: Turn on custom pattern at specific time**
- Configure custom pattern in device preferences
- Create Rule Machine rule
- Trigger: Time (7:00 PM)
- Action: Call `setCustomPattern()` command (after selecting custom pattern in preferences)

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
- **Custom Patterns**: Ensure pattern names are entered in Custom Patterns section
- **Standard Patterns**: All 77 patterns should appear automatically
- Try refreshing device preferences page

### Commands not working
- Verify pattern is selected in appropriate dropdown (Custom or Standard)
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
- `effectList`: Comma-separated list of available patterns
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
- [DRIVER_PLAN.md](./DRIVER_PLAN.md) - Implementation plan

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

See [packageManifest.json](./packageManifest.json) for version history and release notes.
