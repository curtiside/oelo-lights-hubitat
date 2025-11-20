/**
 * Oelo Lights Zone Driver for Hubitat
 * 
 * Controls a single zone of an Oelo Lights controller via HTTP REST API.
 * Designed for use with Hubitat's Simple Automation Rules app for scheduled
 * pattern-based lighting control.
 * 
 * ## Overview
 * 
 * This driver provides Hubitat integration for Oelo Lights controllers, allowing
 * you to control up to 6 zones independently. Each zone requires a separate
 * virtual device instance with this driver.
 * 
 * ## Configuration Requirements
 * 
 * **Required Settings:**
 * - Controller IP Address: IPv4 address of Oelo controller (must be manually entered)
 * - Zone Number: Zone number (1-6) that this driver instance controls
 * 
 * **Optional Settings:**
 * - Poll Interval: How often to poll controller status (default: 30 seconds)
 * - Auto Polling: Automatically poll controller status (default: enabled)
 * - Custom Patterns: Up to 20 user-defined patterns with custom names
 * - Debug Logging: Enable detailed logging for troubleshooting
 * - Command Timeout: HTTP request timeout (default: 10 seconds)
 * 
 * **No Authentication Required:**
 * The Oelo controller uses an open HTTP API with no authentication. No username,
 * password, API keys, or tokens are needed.
 * 
 * See CONFIGURATION.md for complete configuration details.
 * 
 * ## Protocol
 * 
 * **Base URL:** `http://{IP_ADDRESS}/`
 * 
 * **Endpoints:**
 * - `GET /getController` - Returns JSON array of zone statuses
 * - `GET /setPattern?{params}` - Sets pattern/color for zones
 * 
 * **Status Response Format:**
 * JSON array with zone objects containing:
 * - `num`: Zone number (1-6)
 * - `pattern`: Current pattern name or "off"
 * - `isOn`: Boolean indicating if zone is on
 * - Additional fields: `enabled`, `chipID`, `fw`, `ledCnt`, `name`, `speed`, `gap`, etc.
 * 
 * See PROTOCOL_SUMMARY.md for detailed protocol documentation.
 * 
 * ## Commands
 * 
 * **Primary Commands:**
 * - `setCustomPattern()` - Set custom pattern chosen from Custom Pattern Selection dropdown
 * - `setStandardPattern()` - Set standard/predefined pattern chosen from Standard Pattern Selection dropdown
 * - `off()` - Turn off lights
 * - `refresh()` - Get current state from controller
 * 
 * **Pattern Selection:**
 * - Custom patterns: Configured in device preferences (up to 20 patterns)
 * - Standard patterns: 77 predefined patterns from Home Assistant integration
 * - Patterns are selected via dropdown menus in device preferences
 * - Commands use the selected pattern from their respective dropdown
 * 
 * ## Pattern Support
 * 
 * **Predefined Patterns:**
 * - 77 standard patterns organized by category (Christmas, Halloween, Fourth of July, etc.)
 * - Patterns include full URL parameters (colors, speed, direction, etc.)
 * - Pattern names and URLs are defined in PATTERNS map
 * 
 * **Custom Patterns:**
 * - Up to 20 custom patterns can be configured per device
 * - Custom patterns use pattern name as patternType (controller has settings stored)
 * - Custom pattern names are user-defined
 * - Custom patterns appear in Custom Pattern Selection dropdown only
 * 
 * ## Attributes
 * 
 * - `zone`: Zone number (1-6)
 * - `controllerIP`: Controller IP address
 * - `lastCommand`: Last command URL sent
 * - `currentPattern`: Current pattern string from controller (e.g., "march", "off", "custom")
 * - `effectName`: Current pattern name if it matches a predefined pattern (empty if custom or off)
 * - `verificationStatus`: Command verification status (if enabled)
 * - `driverVersion`: Current driver version
 * - `switch`: Current switch state ("on" or "off")
 * 
 * ## Capabilities
 * 
 * - `Refresh`: Standard Hubitat refresh capability
 * 
 * ## Hubitat Groovy Sandbox Restrictions
 * 
 * The following functions/methods are NOT available in Hubitat's sandboxed Groovy environment:
 * 
 * - `getClass()` - Cannot use .getClass() or ?.getClass() on any object
 *   Error: "Expression [MethodCallExpression] is not allowed"
 *   Workaround: Use instanceof checks instead of getClass().name
 * 
 * - `response.json` - HttpResponseDecorator does not have a 'json' property
 *   Error: "No such property: json for class: groovyx.net.http.HttpResponseDecorator"
 *   Workaround: Use response.data instead (may be String, List, or Map depending on content-type)
 * 
 * - Fully qualified class names in instanceof checks - Cannot use java.io.InputStream, etc.
 *   Error: "Expression [ClassExpression] is not allowed: java.io.InputStream"
 *   Workaround: Use duck typing - try accessing properties/methods (e.g., .text) instead of instanceof checks
 * 
 * - Dynamic introspection methods - Limited reflection capabilities
 *   Workaround: Use explicit type checks (instanceof) rather than dynamic inspection
 * 
 * ## References
 * 
 * - Based on Oelo Lights Home Assistant integration: https://github.com/Cinegration/Oelo_Lights_HA
 * - Hubitat Developer Documentation: https://docs2.hubitat.com/en/developer/overview
 * - See README.md, CONFIGURATION.md, PROTOCOL_SUMMARY.md, and DRIVER_PLAN.md for additional documentation
 * 
 * @author Curtis Ide
 * @version 0.7.3
 */

// Pattern Definitions - Must be defined before metadata block
// All predefined patterns from Home Assistant integration
final Map PATTERNS = [
    "American Liberty: Marching with Red White and Blue": "setPattern?patternType=march&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,0,0,255,0,0,255,255,255,255,255,0,0,255,0,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "American Liberty: Standing with Red White and Blue": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,0,0,255,0,0,255,255,255,255,255,0,0,255,0,0,&direction=R&speed=10&gap=0&other=0&pause=0",
    "Birthdays: Birthday Cake": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=14&colors=255,0,0,255,255,255,255,92,0,255,255,255,255,184,0,255,255,255,97,255,0,255,255,255,0,10,255,255,255,255,189,0,255,255,255,255,255,0,199,255,255,255,&direction=R&speed=20&gap=0&other=0&pause=0",
    "Birthdays: Birthday Confetti": "setPattern?patternType=river&num_zones=1&zones={zone}&num_colors=14&colors=255,0,0,255,255,255,255,92,0,255,255,255,255,184,0,255,255,255,97,255,0,255,255,255,0,10,255,255,255,255,189,0,255,255,255,255,255,0,199,255,255,255,&direction=R&speed=20&gap=0&other=0&pause=0",
    "Canadian Strong: O Canada": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=8&colors=237,252,255,237,252,255,237,252,255,255,0,0,255,0,0,255,255,255,255,0,0,255,0,0,&direction=R&speed=20&gap=0&other=0&pause=0",
    "Christmas: Candy Cane Glimmer": "setPattern?patternType=river&num_zones=1&zones={zone}&num_colors=4&colors=255,255,255,255,0,0,255,255,255,255,0,0,&direction=R&speed=20&gap=0&other=0&pause=0",
    "Christmas: Candy Cane Lane": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,255,255,255,255,255,255,255,0,0,255,0,0,255,0,0,&direction=R&speed=4&gap=0&other=0&pause=0",
    "Christmas: Christmas Glow": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,255,255,255,255,255,255,255,153,0,255,153,0,255,153,0,&direction=R&speed=2&gap=0&other=0&pause=0",
    "Christmas: Christmas at Oelo": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=7&colors=26,213,255,26,213,255,26,213,255,26,213,255,26,213,255,255,34,0,255,34,0,&direction=R&speed=2&gap=0&other=0&pause=0",
    "Christmas: Decorating the Christmas Tree": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=5&colors=0,219,11,0,219,11,0,219,11,255,153,0,255,255,255,&direction=R&speed=2&gap=0&other=0&pause=0",
    "Christmas: Dreaming of a White Christmas": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=5&colors=238,252,255,237,252,255,237,252,255,0,0,0,0,0,0,&direction=R&speed=10&gap=0&other=0&pause=0",
    "Christmas: Icicle Chase": "setPattern?patternType=chase&num_zones=1&zones={zone}&num_colors=3&colors=255,255,255,0,183,245,0,73,245,&direction=R&speed=5&gap=0&other=0&pause=0",
    "Christmas: Icicle Shimmer": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=4&colors=255,255,255,0,204,255,0,70,255,0,70,255,&direction=R&speed=4&gap=0&other=0&pause=0",
    "Christmas: Icicle Stream": "setPattern?patternType=river&num_zones=1&zones={zone}&num_colors=4&colors=255,255,255,0,204,255,0,70,255,0,70,255,&direction=R&speed=4&gap=0&other=0&pause=0",
    "Christmas: Saturnalia Christmas": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=9&colors=255,255,255,255,255,255,255,255,255,0,255,47,0,255,47,0,255,47,255,0,0,255,0,0,255,0,0,&direction=R&speed=2&gap=0&other=0&pause=0",
    "Christmas: The Grinch Stole Christmas": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=8&colors=15,255,0,15,255,0,15,255,0,15,255,0,255,0,0,255,0,0,255,255,255,255,255,255,&direction=R&speed=2&gap=0&other=0&pause=0",
    "Cinco De Mayo: Furious Fiesta": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,0,255,0,0,255,0,0,255,0,&direction=R&speed=10&gap=0&other=0&pause=0",
    "Cinco De Mayo: Mexican Spirit": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,0,255,0,0,255,0,0,255,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Cinco De Mayo: Salsa Line": "setPattern?patternType=march&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,0,255,0,0,255,0,0,255,0,&direction=R&speed=5&gap=0&other=0&pause=0",
    "Day of the Dead: Calaveras Dash": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=4&colors=77,248,255,255,77,209,41,144,255,255,246,41,&direction=R&speed=4&gap=0&other=0&pause=0",
    "Day of the Dead: Calaveras Shimmer": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=4&colors=40,255,200,255,40,200,40,120,255,255,246,40,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Day of the Dead: Marigold Breeze": "setPattern?patternType=river&num_zones=1&zones={zone}&num_colors=4&colors=255,138,0,255,138,0,255,34,0,255,34,0,&direction=R&speed=4&gap=0&other=0&pause=0",
    "Day of the Dead: Sugar Skull Still": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=9&colors=255,255,255,255,255,255,225,0,250,255,255,255,255,255,255,5,180,255,255,255,255,255,255,255,255,142,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Easter: Delicate Dance": "setPattern?patternType=march&num_zones=1&zones={zone}&num_colors=9&colors=213,50,255,213,50,255,213,50,255,50,255,184,50,255,184,50,255,184,255,149,50,255,149,50,255,149,50,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Easter: Pastel Unwind": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=9&colors=144,50,255,144,50,255,144,50,255,213,50,255,213,50,255,213,50,255,80,205,255,80,205,255,80,205,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Election Day: A More Perfect Union": "setPattern?patternType=split&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,0,4,255,0,39,255,0,39,255,255,255,255,255,255,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Election Day: We The People": "setPattern?patternType=march&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Fathers Day: Fresh Cut Grass": "setPattern?patternType=sprinkle&num_zones=1&zones={zone}&num_colors=1&colors=7,82,0,&direction=R&speed=1&gap=1&other=0&pause=0",
    "Fathers Day: Grilling Time": "setPattern?patternType=takeover&num_zones=1&zones={zone}&num_colors=6&colors=0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Fourth of July: Fast Fireworks": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,0,0,255,0,0,255,255,255,255,255,0,0,255,0,0,&direction=R&speed=10&gap=0&other=0&pause=0",
    "Fourth of July: Founders Endurance": "setPattern?patternType=split&num_zones=1&zones={zone}&num_colors=3&colors=255,0,0,0,39,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Halloween: Candy Corn Glow": "setPattern?patternType=march&num_zones=1&zones={zone}&num_colors=6&colors=255,215,0,255,155,0,255,64,0,255,54,0,255,74,0,255,255,255,&direction=R&speed=3&gap=0&other=0&pause=0",
    "Halloween: Goblin Delight": "setPattern?patternType=takeover&num_zones=1&zones={zone}&num_colors=6&colors=176,0,255,176,0,255,176,0,255,53,255,0,53,255,0,53,255,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Halloween: Goblin Delight Trance": "setPattern?patternType=streak&num_zones=1&zones={zone}&num_colors=6&colors=176,0,255,176,0,255,176,0,255,53,255,0,53,255,0,53,255,0,&direction=R&speed=3&gap=0&other=0&pause=0",
    "Halloween: Halloween Dancing Bash": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=3&colors=255,155,0,240,81,0,255,155,0,&direction=R&speed=3&gap=0&other=0&pause=0",
    "Halloween: Hocus Pocus": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=6&colors=176,0,255,176,0,255,176,0,255,255,85,0,255,85,0,255,85,0,&direction=R&speed=3&gap=0&other=0&pause=0",
    "Halloween: Hocus Pocus Takeover": "setPattern?patternType=takeover&num_zones=1&zones={zone}&num_colors=6&colors=176,0,255,176,0,255,176,0,255,255,85,0,255,85,0,255,85,0,&direction=R&speed=3&gap=0&other=0&pause=0",
    "Halloween: Pumpkin Patch": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=4&colors=255,54,0,255,64,0,0,28,2,0,0,0,&direction=R&speed=3&gap=0&other=0&pause=0",
    "Hanukkah: Eight Days Of Lights": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,255,255,255,255,255,255,0,0,255,0,0,255,0,0,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Hanukkah: Hanukkah Glide": "setPattern?patternType=river&num_zones=1&zones={zone}&num_colors=4&colors=255,255,255,0,0,255,0,0,255,255,255,255,&direction=R&speed=4&gap=0&other=0&pause=0",
    "Labor Day: Continued Progress": "setPattern?patternType=bolt&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Labor Day: United Strong": "setPattern?patternType=fade&num_zones=1&zones={zone}&num_colors=6&colors=255,0,0,0,0,0,255,255,255,0,0,0,0,0,255,0,0,0,&direction=R&speed=8&gap=0&other=0&pause=0",
    "Memorial Day: In Honor Of Service": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Memorial Day: Unity Of Service": "setPattern?patternType=takeover&num_zones=1&zones={zone}&num_colors=3&colors=255,0,0,0,0,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Mothers Day: Breakfast In Bed": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=9&colors=100,20,255,100,20,255,100,20,255,230,20,255,230,20,255,230,20,255,20,205,255,20,205,255,20,205,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Mothers Day: Love For A Mother": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=2&colors=180,10,255,255,0,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Mothers Day: Twinkling Memories": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=2&colors=255,10,228,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "New Years: Golden Shine": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=2&colors=255,255,255,255,161,51,&direction=R&speed=1&gap=0&other=0&pause=0",
    "New Years: River of Gold": "setPattern?patternType=river&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,255,145,15,255,255,255,255,145,15,255,255,255,255,145,15,&direction=R&speed=5&gap=0&other=0&pause=0",
    "New Years: Sliding Into the New Year": "setPattern?patternType=streak&num_zones=1&zones={zone}&num_colors=2&colors=255,255,255,255,145,15,&direction=R&speed=1&gap=0&other=0&pause=0",
    "New Years: Year of Change": "setPattern?patternType=fade&num_zones=1&zones={zone}&num_colors=3&colors=255,255,255,255,145,15,255,145,15,&direction=R&speed=5&gap=0&other=0&pause=0",
    "Presidents Day: Flight Of The President": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Presidents Day: The Presidents March": "setPattern?patternType=march&num_zones=1&zones={zone}&num_colors=9&colors=255,0,0,255,0,0,255,0,0,0,0,255,0,0,255,0,0,255,255,255,255,255,255,255,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Pride: Split": "setPattern?patternType=split&num_zones=1&zones={zone}&num_colors=6&colors=255,0,0,255,50,0,255,240,0,0,255,0,0,0,255,125,0,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Quinceanera: Perfectly Pink": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=6&colors=255,61,183,255,46,228,255,10,164,255,46,149,255,46,228,255,46,129,&direction=R&speed=9&gap=0&other=0&pause=0",
    "Quinceanera: Twinkle Eyes": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=2&colors=255,10,228,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Quinceanera: Vibrant Celebration": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=2&colors=180,10,255,255,0,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "St. Patricks Day: Follow The Rainbow": "setPattern?patternType=split&num_zones=1&zones={zone}&num_colors=6&colors=255,0,5,255,50,0,255,230,0,63,255,0,0,136,255,100,0,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "St. Patricks Day: Sprinkle Of Dust": "setPattern?patternType=sprinkle&num_zones=1&zones={zone}&num_colors=2&colors=97,255,0,173,255,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Thanksgiving: Thanksgiving Apple Pie": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=6&colors=255,31,0,255,31,0,255,31,0,255,94,0,255,94,0,255,94,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Thanksgiving: Thanksgiving Turkey": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=1&colors=255,94,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Valentines: Adorations Smile": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=3&colors=255,10,228,255,0,76,255,143,238,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Valentines: Cupids Twinkle": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=2&colors=255,10,228,255,255,255,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Valentines: My Heart Is Yours": "setPattern?patternType=fade&num_zones=1&zones={zone}&num_colors=3&colors=255,10,228,255,255,255,255,0,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Valentines: Powerful Love": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=2&colors=180,10,255,255,0,0,&direction=R&speed=1&gap=0&other=0&pause=0"
]

metadata {
    definition(name: "Oelo Lights Zone", namespace: "pizzaman383", author: "Curtis Ide", importUrl: "") {
        capability "Refresh"
        
        // Custom attributes
        attribute "zone", "number"
        attribute "controllerIP", "string"
        attribute "lastCommand", "string"
        attribute "currentPattern", "string"
        attribute "effectName", "string"
        attribute "verificationStatus", "string"
        attribute "driverVersion", "string"
        attribute "switch", "string"
        
        // Custom commands
        command "setCustomPattern"
        command "setStandardPattern"
        command "getPattern"
        command "off"
    }
    
    preferences {
        section("Controller Settings") {
            input name: "controllerIP", type: "text", title: "Controller IP Address", required: true, description: "IP address of Oelo controller"
            input name: "zoneNumber", type: "number", title: "Zone Number", range: "1..6", required: true, defaultValue: 1, description: "Zone number (1-6)"
        }
        
        section("Standard Pattern Selection") {
            input name: "selectedStandardPattern", type: "enum", title: "Select Standard Pattern", 
                options: (["": "-- Select Pattern --"] + PATTERNS.collectEntries { [it.key, it.key] }).sort(), 
                required: false, description: "Choose a standard/predefined pattern to set"
        }
        
        section("Custom Pattern Selection") {
            input name: "selectedPattern", type: "enum", title: "Select Custom Pattern", 
                options: getCustomPatternOptions(), 
                required: false, description: "Choose a custom pattern to set"
        }
        
        section("Custom Pattern Management") {
            input name: "renamePattern", type: "enum", title: "Select Pattern to Rename", 
                options: getCustomPatternOptions(), 
                required: false, description: "Select a custom pattern to rename"
            input name: "newPatternName", type: "text", title: "New Pattern Name", 
                required: false, description: "Enter new name for the selected pattern (pattern will be renamed when preferences are saved)"
            input name: "deletePattern", type: "enum", title: "Delete Custom Pattern", 
                options: getCustomPatternOptions(), 
                required: false, description: "Select a custom pattern to delete (pattern will be deleted when preferences are saved)"
        }
        
        section("Polling") {
            input name: "pollInterval", type: "number", title: "Poll Interval (seconds)", range: "10..300", defaultValue: 30, description: "How often to poll controller status"
            input name: "autoPoll", type: "bool", title: "Enable Auto Polling", defaultValue: true, description: "Automatically poll controller status"
        }
        
        section("Command Verification") {
            input name: "verifyCommands", type: "bool", title: "Verify Commands", defaultValue: false, description: "Verify commands by checking controller status after sending"
            input name: "verificationRetries", type: "number", title: "Verification Retries", range: "1..10", defaultValue: 3, description: "Number of times to retry verification"
            input name: "verificationDelay", type: "number", title: "Verification Delay (seconds)", range: "1..10", defaultValue: 2, description: "Seconds to wait between verification attempts"
            input name: "verificationTimeout", type: "number", title: "Verification Timeout (seconds)", range: "5..60", defaultValue: 30, description: "Maximum time to wait for verification"
        }
        
        section("Advanced") {
            input name: "logEnable", type: "bool", title: "Enable Debug Logging", defaultValue: false, description: "Enable detailed logging"
            input name: "commandTimeout", type: "number", title: "Command Timeout (seconds)", range: "5..30", defaultValue: 10, description: "HTTP request timeout"
        }
    }
}

// Driver lifecycle methods

def installed() {
    log.info "Oelo Lights Zone driver installed"
    // Set driver version immediately
    setDriverVersion()
    initialize()
}

def updated() {
    log.warn "updated() preference action called"
    log.info "Oelo Lights Zone driver updated"
    // Set driver version immediately
    setDriverVersion()
    
    // Store PATTERNS in state if not already stored (try to populate it here too)
    if (PATTERNS && !state.patternsMap) {
        state.patternsMap = PATTERNS
        log.debug "Stored PATTERNS map in state during updated() (${PATTERNS.size()} patterns)"
    }
    
    // Handle pattern renaming if renamePattern and newPatternName preferences were set
    if (settings.renamePattern && settings.renamePattern != "" && settings.newPatternName && settings.newPatternName.trim() != "") {
        def oldPatternName = settings.renamePattern
        def newPatternName = settings.newPatternName.trim()
        
        def customPatterns = state.customPatterns ?: []
        
        // Check if pattern with old name exists - if not, it was already renamed or doesn't exist
        def patternToRename = customPatterns.find { it && it.name == oldPatternName }
        if (!patternToRename) {
            // Pattern with old name doesn't exist - already renamed or never existed, skip
            log.debug "Pattern '${oldPatternName}' not found - already renamed or doesn't exist, skipping rename action"
        } else if (patternToRename.name == newPatternName) {
            // Check if pattern already has the new name (no change needed)
            log.debug "Pattern '${oldPatternName}' already has name '${newPatternName}', skipping rename action"
        } else {
            log.warn "Preference action: Renaming custom pattern '${oldPatternName}' to '${newPatternName}'"
        log.info "Renaming custom pattern: '${oldPatternName}' to '${newPatternName}'"
        
        // Check if new name already exists (and it's not the same pattern)
        def nameExists = customPatterns.find { it && it.name == newPatternName && it != patternToRename }
        if (nameExists) {
            log.warn "Cannot rename: Pattern name '${newPatternName}' already exists"
        } else {
            patternToRename.name = newPatternName
            state.customPatterns = customPatterns
            log.info "Renamed pattern '${oldPatternName}' to '${newPatternName}'"
        }
        }
    }
    
    // Handle pattern deletion if deletePattern preference was set
    if (settings.deletePattern && settings.deletePattern != "") {
        def patternName = settings.deletePattern
        log.warn "Preference action: Deleting custom pattern '${patternName}'"
        log.info "Deleting custom pattern: ${patternName}"
        
        def customPatterns = state.customPatterns ?: []
        def indexToDelete = -1
        
        // Find pattern to delete
        for (int i = 0; i < customPatterns.size(); i++) {
            if (customPatterns[i] && customPatterns[i].name == patternName) {
                indexToDelete = i
                break
            }
        }
        
        if (indexToDelete != -1) {
            // Remove pattern and compact list
            customPatterns.remove(indexToDelete)
            // Remove trailing nulls
            while (customPatterns.size() > 0 && customPatterns[customPatterns.size() - 1] == null) {
                customPatterns.remove(customPatterns.size() - 1)
            }
            
            state.customPatterns = customPatterns
            log.info "Deleted pattern '${patternName}' and compacted list"
            
            // Clear the deletePattern setting (can't use app.updateSetting in driver, will clear on next save)
            // User will need to save preferences again to clear the selection
        } else {
            log.warn "Pattern '${patternName}' not found for deletion"
        }
    }
    
    initialize()
}

// Set driver version in state and attribute (called unconditionally)
def setDriverVersion() {
    def driverVersion = "0.7.3"
    // Always update both state and attribute to ensure they match
    state.driverVersion = driverVersion
    sendEvent(name: "driverVersion", value: driverVersion)
    if (state.driverVersion != driverVersion) {
        log.info "Driver version updated to: ${driverVersion}"
    }
}

def initialize() {
    // Set driver version (in case initialize is called directly)
    setDriverVersion()
    
    // Store PATTERNS in state so it's accessible at runtime (same approach as custom patterns)
    // PATTERNS works in metadata block but not in functions at runtime, so store it in state
    if (PATTERNS && !state.patternsMap) {
        state.patternsMap = PATTERNS
        log.debug "Stored PATTERNS map in state (${PATTERNS.size()} patterns)"
    } else if (!state.patternsMap) {
        log.warn "PATTERNS not accessible during initialization - standard patterns may not work"
    }
    
    // Initialize custom patterns storage if not exists
    if (!state.customPatterns) {
        state.customPatterns = []
    }
    
    // Migrate old settings-based patterns to state (one-time migration)
    migrateOldCustomPatterns()
    
    if (!controllerIP) {
        log.error "Controller IP address not configured"
        return
    }
    
    // Validate IP address format
    if (!isValidIP(controllerIP)) {
        log.error "Invalid IP address format: ${controllerIP}"
        return
    }
    
    if (!zoneNumber || zoneNumber < 1 || zoneNumber > 6) {
        log.error "Invalid zone number: ${zoneNumber}. Must be 1-6"
        return
    }
    
    sendEvent(name: "zone", value: zoneNumber)
    sendEvent(name: "controllerIP", value: controllerIP)
    
    // Start polling if enabled
    if (autoPoll) {
        unschedule()
        def interval = pollInterval ?: 30
        runIn(interval, poll)
        log.info "Auto-polling enabled, interval: ${interval} seconds"
    }
    
    // Initial refresh
    refresh()
}

// Validate IP address format (basic IPv4 validation)
def isValidIP(String ip) {
    if (!ip) return false
    
    // Basic IPv4 format check (xxx.xxx.xxx.xxx)
    def pattern = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/
    def matcher = ip =~ pattern
    
    if (!matcher.matches()) return false
    
    // Check each octet is 0-255
    for (int i = 1; i <= 4; i++) {
        def octet = Integer.parseInt(matcher.group(i))
        if (octet < 0 || octet > 255) return false
    }
    
    return true
}

def uninstalled() {
    unschedule()
    log.info "Oelo Lights Zone driver uninstalled"
}

// Capability: Refresh

def refresh() {
    log.warn "refresh() command called"
    log.info "Refresh requested for zone ${zoneNumber}"
    
    // Ensure driver version is current (in case code was updated)
    setDriverVersion()
    
    // Update lastCommand to show refresh was called
    sendEvent(name: "lastCommand", value: "refresh")
    
    if (!controllerIP) {
        log.error "Cannot refresh: Controller IP not configured"
        return
    }
    
    // Call poll directly - this will update device state from controller
    poll()
}

// Custom command: Turn off lights

def off() {
    log.warn "off() command called"
    logDebug "Turning zone ${zoneNumber} OFF"
    
    def url = buildCommandUrl([
        patternType: "off",
        zones: zoneNumber,
        num_zones: 1,
        num_colors: 1,
        colors: "0,0,0",
        direction: "F",
        speed: 0,
        gap: 0,
        other: 0,
        pause: 0
    ])
    
    def success = sendCommand(url)
    if (success) {
        sendEvent(name: "switch", value: "off")
    }
}

// Internal function: Set effect (used by setCustomPattern and setStandardPattern commands)
def setEffect(String effectName) {
    logDebug "Setting effect: ${effectName}"
    
    def patternUrl = getPatternUrl(effectName)
    if (!patternUrl) {
        log.error "Unknown effect: ${effectName}. Available: ${buildEffectList()}"
        return
    }
    
    // Send command and store for future on() calls
    def success = sendCommand(patternUrl)
    if (success) {
        sendEvent(name: "effectName", value: effectName)
        sendEvent(name: "lastCommand", value: patternUrl)
        sendEvent(name: "switch", value: "on")
        logDebug "Pattern '${effectName}' set and stored"
    }
}

// Custom command: Set custom pattern from dropdown selection (Preferences)
def setCustomPattern() {
    log.warn "setCustomPattern() command called"
    def patternName = settings.selectedPattern
    if (!patternName || patternName == "") {
        log.warn "No custom pattern selected. Please select a custom pattern from Preferences → Custom Pattern Selection section first."
        return
    }
    
    log.info "Setting custom pattern from Preferences dropdown: ${patternName}"
    setEffect(patternName)
}

// Custom command: Set standard/predefined pattern from dropdown selection (Preferences)
def setStandardPattern() {
    log.warn "setStandardPattern() command called"
    def patternName = settings.selectedStandardPattern
    if (!patternName || patternName == "") {
        log.warn "No standard pattern selected. Please select a standard pattern from Preferences → Standard Pattern Selection section first."
        return
    }
    
    log.info "Setting standard pattern from Preferences dropdown: ${patternName}"
    setEffect(patternName)
}

// Custom command: Get current pattern from controller and store it
def getPattern() {
    log.warn "getPattern() command called"
    log.info "=== GET PATTERN COMMAND STARTED ==="
    log.debug "[Step 1] Checking controller IP configuration..."
    
    if (!controllerIP) {
        log.error "[FAILED] Step 1: Controller IP not configured"
        log.debug "=== GET PATTERN COMMAND FAILED ==="
        return
    }
    log.debug "[SUCCESS] Step 1: Controller IP configured: ${controllerIP}"
    
    log.debug "[Step 2] Fetching zone data from controller..."
    // Fetch current zone state
    fetchZoneData { zoneData ->
        log.debug "[Step 2] fetchZoneData callback received"
        
        if (!zoneData) {
            log.error "[FAILED] Step 2: Could not retrieve zone data from controller (IP: ${controllerIP}, Zone: ${zoneNumber})"
            log.debug "=== GET PATTERN COMMAND FAILED ==="
            return
        }
        log.debug "[SUCCESS] Step 2: Zone data retrieved"
        log.debug "Zone data keys: ${zoneData.keySet()}"
        log.debug "Zone data: ${zoneData}"
        
        log.debug "[Step 3] Extracting pattern information..."
        // Extract pattern information
        def pattern = zoneData.pattern ?: zoneData.patternType ?: "off"
        log.debug "Pattern field value: '${zoneData.pattern}'"
        log.debug "PatternType field value: '${zoneData.patternType}'"
        log.debug "Extracted pattern: '${pattern}'"
        
        log.debug "[Step 4] Determining if zone is on..."
        // Use isOn field from controller if available, otherwise infer from pattern
        def isOn = zoneData.isOn != null ? zoneData.isOn : (pattern != "off" && pattern != null && pattern.toString().trim() != "")
        log.debug "zoneData.isOn field: ${zoneData.isOn}"
        log.debug "Calculated isOn: ${isOn}"
        
        if (pattern == "off" || !isOn) {
            log.warn "[FAILED] Step 4: Zone is currently off - no pattern to capture"
            log.debug "Pattern check: pattern='${pattern}' (off=${pattern == "off"})"
            log.debug "isOn check: isOn=${isOn}"
            log.debug "=== GET PATTERN COMMAND FAILED ==="
            return
        }
        log.debug "[SUCCESS] Step 4: Zone is on with pattern '${pattern}'"
        
        log.debug "[Step 5] Building URL parameters from zone data..."
        // Build URL parameters from zone data
        def urlParams = buildPatternParamsFromZoneData(zoneData)
        if (!urlParams) {
            log.error "[FAILED] Step 5: Could not build pattern parameters from zone data"
            log.debug "buildPatternParamsFromZoneData returned null"
            log.debug "=== GET PATTERN COMMAND FAILED ==="
            return
        }
        log.debug "[SUCCESS] Step 5: URL parameters built"
        log.debug "URL parameters: ${urlParams}"
        
        log.debug "[Step 6] Generating stable pattern ID from fields..."
        // Generate stable pattern ID from pattern type and key parameters
        // Available fields: pattern, name (zone name), num, numberOfColors, direction, speed, gap, rgbOrder
        // Use pattern type as base, add descriptive suffix if parameters are non-default
        // This ID is stable and prevents duplicates - same parameters = same ID
        def patternId = pattern.toString()
        
        // Add descriptive suffix based on key parameters
        def suffixParts = []
        if (zoneData.direction && zoneData.direction != "0" && zoneData.direction != "F") {
            suffixParts.add("dir${zoneData.direction}")
        }
        if (zoneData.speed && zoneData.speed != 0) {
            suffixParts.add("spd${zoneData.speed}")
        }
        if (zoneData.numberOfColors && zoneData.numberOfColors > 1) {
            suffixParts.add("${zoneData.numberOfColors}colors")
        }
        
        // Build stable ID from pattern type and parameters (no timestamp)
        if (suffixParts.isEmpty()) {
            patternId = pattern.toString()
        } else {
            patternId = "${pattern}_${suffixParts.join('_')}"
        }
        
        log.debug "zoneData.name (zone name): '${zoneData.name}'"
        log.debug "zoneData.pattern (pattern type): '${pattern}'"
        log.debug "zoneData.direction: '${zoneData.direction}'"
        log.debug "zoneData.speed: '${zoneData.speed}'"
        log.debug "zoneData.numberOfColors: '${zoneData.numberOfColors}'"
        log.debug "Generated stable pattern ID: '${patternId}'"
        log.debug "[SUCCESS] Step 6: Stable pattern ID determined: '${patternId}'"
        
        log.debug "[Step 7] Retrieving custom patterns list from state..."
        // Get custom patterns list
        def customPatterns = state.customPatterns ?: []
        log.debug "Current custom patterns count: ${customPatterns.size()}"
        log.debug "Custom patterns: ${customPatterns}"
        log.debug "[SUCCESS] Step 7: Custom patterns list retrieved"
        
        log.debug "[Step 7a] Setting initial display name..."
        // Use the stable ID as the initial display name (user can edit this later)
        def patternName = patternId
        log.debug "Initial display name set to: '${patternName}'"
        log.debug "[SUCCESS] Step 7a: Initial display name set"
        
        log.debug "[Step 8] Checking for existing pattern with same ID..."
        // Check if a custom pattern with this ID already exists (prevents duplicates)
        def existingIndex = customPatterns.findIndexOf { it && it.id == patternId }
        log.debug "Existing pattern search by ID: index=${existingIndex >= 0 ? existingIndex : 'not found'}"
        
        if (existingIndex >= 0) {
            log.debug "[Step 9] Pattern with same ID exists - updating parameters..."
            // Pattern with same ID exists - update urlParams but keep existing name (user may have renamed it)
            def existingName = customPatterns[existingIndex].name
            customPatterns[existingIndex].urlParams = urlParams
            state.customPatterns = customPatterns
            log.info "[SUCCESS] Step 9: Updated existing pattern '${existingName}' (ID: ${patternId}) with new parameters"
            log.debug "Updated pattern: ${customPatterns[existingIndex]}"
            log.debug "=== GET PATTERN COMMAND COMPLETED SUCCESSFULLY ==="
        } else {
            log.debug "[Step 9] Finding next empty slot for new pattern..."
            // Find next empty slot for new pattern
            def nextSlot = findNextEmptyPatternSlot(customPatterns)
            log.debug "Next empty slot: ${nextSlot >= 0 ? nextSlot : 'none found'}"
            
            if (nextSlot == -1) {
                log.error "[FAILED] Step 9: No empty slots available (maximum 20 custom patterns)"
                log.debug "Current custom patterns count: ${customPatterns.size()}"
                log.debug "=== GET PATTERN COMMAND FAILED ==="
                return
            }
            log.debug "[SUCCESS] Step 9: Found empty slot ${nextSlot}"
            
            log.debug "[Step 10] Storing new pattern in slot ${nextSlot}..."
            // Store new pattern with both ID (stable) and name (display)
            if (customPatterns.size() < nextSlot) {
                log.debug "Extending custom patterns list from ${customPatterns.size()} to ${nextSlot}"
                // Extend list if needed
                while (customPatterns.size() < nextSlot) {
                    customPatterns.add(null)
                }
            }
            customPatterns[nextSlot - 1] = [
                id: patternId,
                name: patternName,
                urlParams: urlParams
            ]
            log.debug "Pattern stored at index ${nextSlot - 1}: id='${patternId}', name='${patternName}'"
            
            state.customPatterns = customPatterns
            log.debug "State updated. New custom patterns count: ${state.customPatterns.size()}"
            
            log.info "[SUCCESS] Step 10: Stored new custom pattern '${patternName}' (ID: ${patternId}) in slot ${nextSlot}"
            log.debug "Final custom patterns list: ${state.customPatterns}"
            log.debug "=== GET PATTERN COMMAND COMPLETED SUCCESSFULLY ==="
        }
    }
}


// Build pattern URL parameters from zone data returned by getController
def buildPatternParamsFromZoneData(Map zoneData) {
    log.debug "buildPatternParamsFromZoneData: Starting with zoneData keys: ${zoneData.keySet()}"
    
    def pattern = zoneData.pattern ?: zoneData.patternType ?: "off"
    log.debug "buildPatternParamsFromZoneData: Extracted pattern: '${pattern}'"
    
    if (pattern == "off") {
        log.debug "buildPatternParamsFromZoneData: Pattern is 'off', returning null"
        return null
    }
    
    // Extract parameters from zone data
    def numColors = zoneData.numberOfColors ?: 1
    def colorStr = zoneData.colorStr ?: ""
    def parsedColors = colorStr ? parseColorStr(colorStr) : "255,255,255"
    
    log.debug "buildPatternParamsFromZoneData: numberOfColors=${numColors}, colorStr length=${colorStr.length()}, parsedColors=${parsedColors}"
    
    def params = [
        patternType: pattern,
        zones: zoneNumber,
        num_zones: 1,
        num_colors: numColors,
        colors: parsedColors,
        direction: zoneData.direction ?: "F",
        speed: zoneData.speed ?: 0,
        gap: zoneData.gap ?: 0,
        other: zoneData.other ?: 0,
        pause: 0
    ]
    
    log.debug "buildPatternParamsFromZoneData: Built params: ${params}"
    return params
}

// Generate stable pattern ID from URL parameters (used to identify unique patterns)

// Parse colorStr format (e.g., "255&242&194&255&242&194...") to comma-separated RGB
def parseColorStr(String colorStr) {
    if (!colorStr || colorStr.trim() == "") return "255,255,255"
    
    // colorStr format: "R&G&B&R&G&B&..."
    def parts = colorStr.split("&")
    def colors = []
    for (int i = 0; i < parts.size(); i += 3) {
        if (i + 2 < parts.size()) {
            colors.add("${parts[i]},${parts[i+1]},${parts[i+2]}")
        }
    }
    return colors.join(",")
}

// Find next empty slot in custom patterns list
def findNextEmptyPatternSlot(List customPatterns) {
    for (int i = 0; i < 20; i++) {
        if (i >= customPatterns.size() || customPatterns[i] == null) {
            return i + 1  // Return 1-based slot number
        }
    }
    return -1  // No empty slots
}

// Migrate old settings-based custom patterns to state-based storage (one-time)
def migrateOldCustomPatterns() {
    // Check if migration is needed (old patterns in settings, none in state)
    if (state.customPatterns && state.customPatterns.size() > 0) {
        return  // Already migrated
    }
    
    def customPatterns = []
    def migrated = false
    
    // Check for old settings-based patterns
    for (int i = 1; i <= 20; i++) {
        def name = settings."customPattern${i}Name"
        if (name && name.trim()) {
            // Migrate old pattern - use pattern name as patternType (minimal params)
            customPatterns.add([
                name: name.trim(),
                urlParams: [
                    patternType: name.trim(),
                    zones: zoneNumber,
                    num_zones: 1,
                    num_colors: 1,
                    colors: "255,255,255",
                    direction: "F",
                    speed: 0,
                    gap: 0,
                    other: 0,
                    pause: 0
                ]
            ])
            migrated = true
        }
    }
    
    if (migrated) {
        state.customPatterns = customPatterns
        log.info "Migrated ${customPatterns.size()} custom patterns from settings to state"
    }
}

def getEffectList() {
    // Return sorted list for Simple Automation Rules dropdown
    return buildEffectList()
}

// Build effect list including custom patterns first, then predefined patterns
def buildEffectList() {
    def customList = []
    def predefinedList = []
    
    // Add custom patterns from state (stored patterns)
    def customPatterns = state.customPatterns ?: []
    customPatterns.each { pattern ->
        if (pattern && pattern.name) {
            customList.add(pattern.name)
        }
    }
    
    // Add predefined patterns from state (same approach as custom patterns)
    def patternsMap = state.patternsMap
    if (patternsMap) {
        predefinedList.addAll(patternsMap.keySet())
    }
    
    // Combine: custom first (sorted), then predefined (sorted)
    def result = []
    result.addAll(customList.sort())
    result.addAll(predefinedList.sort())
    
    return result
}

// Build custom pattern options for enum dropdown (only custom patterns from state)
def getCustomPatternOptions() {
    def options = [:]
    
    // Add empty option first
    options[""] = "-- Select Custom Pattern --"
    
    // Custom patterns from state (stored patterns)
    def customPatterns = []
    try {
        def storedPatterns = state.customPatterns ?: []
        storedPatterns.each { pattern ->
            if (pattern && pattern.name) {
                customPatterns.add(pattern.name)
            }
        }
    } catch (Exception e) {
        // state not available during metadata parsing - that's OK
    }
    
    // Add custom patterns (sorted)
    customPatterns.sort().each { pattern ->
        options[pattern] = pattern
    }
    
    return options
}

// Get pattern URL - handles both predefined and custom patterns
def getPatternUrl(String effectName) {
    log.debug "getPatternUrl: Looking for effect '${effectName}'"
    
    // Check predefined patterns from state (same approach as custom patterns)
    // Try to populate state.patternsMap if it's missing and PATTERNS is accessible
    if (!state.patternsMap && PATTERNS) {
        state.patternsMap = PATTERNS
        log.debug "getPatternUrl: Populated state.patternsMap from PATTERNS (${PATTERNS.size()} patterns)"
    }
    
    def patternsMap = state.patternsMap
    if (patternsMap) {
        log.debug "getPatternUrl: Checking state.patternsMap (${patternsMap.size()} patterns) for '${effectName}'"
        def pattern = patternsMap[effectName]
        if (pattern) {
            log.debug "getPatternUrl: Found predefined pattern '${effectName}'"
            // Replace {zone} placeholder
            def url = pattern.replace("{zone}", zoneNumber.toString())
            return "http://${controllerIP}/${url}"
        } else {
            log.debug "getPatternUrl: Pattern '${effectName}' not found in state.patternsMap"
        }
    } else {
        log.warn "getPatternUrl: state.patternsMap is null and PATTERNS not accessible - standard patterns unavailable"
    }
    
    // Check custom patterns from state
    def customPatterns = state.customPatterns ?: []
    def customPattern = customPatterns.find { it && it.name == effectName }
    if (customPattern && customPattern.urlParams) {
        // Use stored URL parameters to build command URL
        def urlParams = customPattern.urlParams.clone()
        urlParams.zones = zoneNumber  // Ensure zone number is current
        return buildCommandUrl(urlParams)
    }
    
    return null
}

// HTTP Communication

def sendCommand(String url) {
    logDebug "Sending command: ${url}"
    
    try {
        httpGet([
            uri: url,
            timeout: (commandTimeout ?: 10) * 1000
        ]) { response ->
            def status = response.status
            def text = response.data?.text ?: response.data?.toString() ?: ""
            
            if (status == 200 && text.contains("Command Received")) {
                logDebug "Command sent successfully"
                
                // If verification is enabled, start verification process
                if (verifyCommands) {
                    startVerification(url)
                } else {
                    // Optimistic update when verification disabled
                    def isOff = url.contains("patternType=off")
                    sendEvent(name: "switch", value: isOff ? "off" : "on")
                }
                
                return true
            } else {
                log.error "Command failed with HTTP status: ${status}"
                // Log response body/data if available (controller may return error details)
                if (response.data) {
                    try {
                        def errorData = response.data
                        if (errorData instanceof String) {
                            log.error "Controller error response: ${errorData}"
                        } else if (errorData.text) {
                            log.error "Controller error response: ${errorData.text}"
                        } else {
                            log.error "Controller error response: ${errorData.toString()}"
                        }
                    } catch (Exception e) {
                        log.debug "Could not parse error response: ${e.message}"
                    }
                }
                sendEvent(name: "verificationStatus", value: "error")
                return false
            }
        }
    } catch (Exception e) {
        log.error "Command failed: ${e.message}"
        sendEvent(name: "verificationStatus", value: "error")
        return false
    }
}

// Start verification process (async using runIn)
def startVerification(String commandUrl) {
    logDebug "Starting verification for command: ${commandUrl}"
    
    // Parse command to determine what we expect
    def expectedState = parseCommandExpectation(commandUrl)
    if (!expectedState) {
        log.warn "Could not parse command expectation, skipping verification"
        sendEvent(name: "verificationStatus", value: "skipped")
        return
    }
    
    // Store verification state
    state.verificationCommandUrl = commandUrl
    state.verificationExpectedState = expectedState
    state.verificationAttempt = 0
    state.verificationStartTime = now()
    
    // Start first verification attempt immediately
    verifyCommandState(commandUrl, expectedState.toString(), 1, now())
}

// Verify command state (called via runIn for async retries)
def verifyCommandState(data) {
    def commandUrl = data?.commandUrl ?: state.verificationCommandUrl
    def attempt = data?.attempt ?: 1
    def startTime = data?.startTime ?: state.verificationStartTime ?: now()
    
    logDebug "Verification attempt ${attempt} for command: ${commandUrl}"
    
    def maxRetries = verificationRetries ?: 3
    def delaySeconds = verificationDelay ?: 2
    def timeoutSeconds = verificationTimeout ?: 30
    
    // Check if we've exceeded timeout
    if (now() - startTime > timeoutSeconds * 1000) {
        log.warn "Verification timeout after ${timeoutSeconds} seconds"
        sendEvent(name: "verificationStatus", value: "timeout")
        state.verificationCommandUrl = null
        state.verificationExpectedState = null
        return
    }
    
    // Get current status from controller
    getCurrentZoneStateForVerification(commandUrl, attempt, maxRetries, delaySeconds, startTime)
}

// Get current zone state and compare with expected (uses poll() logic via fetchZoneData)
def getCurrentZoneStateForVerification(String commandUrl, int attempt, int maxRetries, int delaySeconds, long startTime) {
    if (!controllerIP) {
        log.error "Cannot verify: Controller IP not configured"
        sendEvent(name: "verificationStatus", value: "error")
        return
    }
    
    logDebug "Getting zone state for verification (attempt ${attempt}/${maxRetries})"
    
    // Use shared fetchZoneData function (same logic as poll())
    fetchZoneData { zoneData ->
        if (!zoneData) {
            log.warn "Zone ${zoneNumber} not found in response for verification"
            handleVerificationRetry(commandUrl, attempt, maxRetries, delaySeconds, startTime)
            return
        }
        
        def currentState = [
            pattern: zoneData.pattern ?: "off",
            isOff: (zoneData.pattern == "off")
        ]
        
        // Get expected state from stored state
        def expectedState = state.verificationExpectedState
        if (!expectedState) {
            // Try to parse from command URL
            expectedState = parseCommandExpectation(commandUrl)
        }
        
        if (matchesExpectedState(currentState, expectedState)) {
            logDebug "Command verified successfully on attempt ${attempt}"
            sendEvent(name: "verificationStatus", value: "verified")
            updateStateFromVerification(currentState)
            // Clear verification state
            state.verificationCommandUrl = null
            state.verificationExpectedState = null
        } else {
            logDebug "Verification attempt ${attempt}/${maxRetries} failed. Expected: ${expectedState}, Got: ${currentState}"
            
            // Schedule next attempt if not exceeded max retries
            if (attempt < maxRetries) {
                def nextAttempt = attempt + 1
                logDebug "Scheduling verification retry ${nextAttempt} in ${delaySeconds} seconds"
                runIn(delaySeconds, "verifyCommandState", [data: [
                    commandUrl: commandUrl,
                    attempt: nextAttempt,
                    startTime: startTime
                ]])
            } else {
                log.warn "Command verification failed after ${maxRetries} attempts"
                sendEvent(name: "verificationStatus", value: "failed")
                // Clear verification state
                state.verificationCommandUrl = null
                state.verificationExpectedState = null
            }
        }
    }
}

// Handle verification retry logic (orchestration only - uses verifyCommandState)
def handleVerificationRetry(String commandUrl, int attempt, int maxRetries, int delaySeconds, long startTime) {
    if (attempt < maxRetries) {
        def nextAttempt = attempt + 1
        def timeoutSeconds = verificationTimeout ?: 30
        
        // Check timeout
        if (now() - startTime > timeoutSeconds * 1000) {
            log.warn "Verification timeout after ${timeoutSeconds} seconds"
            sendEvent(name: "verificationStatus", value: "timeout")
            state.verificationCommandUrl = null
            state.verificationExpectedState = null
            return
        }
        
        logDebug "Scheduling verification retry ${nextAttempt} in ${delaySeconds} seconds"
        runIn(delaySeconds, "verifyCommandState", [data: [
            commandUrl: commandUrl,
            attempt: nextAttempt,
            startTime: startTime
        ]])
    } else {
        log.warn "Command verification failed after ${maxRetries} attempts"
        sendEvent(name: "verificationStatus", value: "failed")
        state.verificationCommandUrl = null
        state.verificationExpectedState = null
    }
}

// Parse command URL to determine expected state
def parseCommandExpectation(String url) {
    try {
        def params = parseUrlParams(url)
        
        def patternType = params.get("patternType")
        def zones = params.get("zones")
        def colors = params.get("colors")
        
        // Only verify if this command is for our zone
        if (zones && zones.toString() != zoneNumber.toString()) {
            return null // Not our zone, skip verification
        }
        
        def expectation = [:]
        expectation.patternType = patternType
        expectation.isOff = (patternType == "off")
        
        if (colors && patternType == "custom") {
            // Parse RGB colors
            def colorParts = colors.split(",")
            if (colorParts.length >= 3) {
                try {
                    expectation.colors = [
                        Integer.parseInt(colorParts[0].trim()),
                        Integer.parseInt(colorParts[1].trim()),
                        Integer.parseInt(colorParts[2].trim())
                    ]
                } catch (NumberFormatException e) {
                    log.warn "Could not parse color values: ${colors}"
                }
            }
        }
        
        return expectation
    } catch (Exception e) {
        log.error "Error parsing command expectation: ${e.message}"
        return null
    }
}

// Parse command URL to extract parameters (helper for URL decoding)
def parseUrlParams(String url) {
    try {
        def uri = new URI(url)
        def query = uri.query
        if (!query) return [:]
        
        def params = [:]
        query.split("&").each { param ->
            def parts = param.split("=", 2)
            if (parts.length == 2) {
                def key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                def value = java.net.URLDecoder.decode(parts[1], "UTF-8")
                params[key] = value
            }
        }
        return params
    } catch (Exception e) {
        log.error "Error parsing URL params: ${e.message}"
        return [:]
    }
}

// Check if current state matches expected state
def matchesExpectedState(Map currentState, Map expectedState) {
    if (!currentState || !expectedState) return false
    
    // Check if off state matches
    if (expectedState.isOff != null) {
        if (currentState.isOff != expectedState.isOff) {
            return false
        }
    }
    
    // If turning off, that's sufficient
    if (expectedState.isOff == true) {
        return true
    }
    
    // For on states, check pattern type matches (if we can determine it)
    if (expectedState.patternType && currentState.pattern) {
        // For custom colors, we can't easily verify exact color match from status
        // So we just verify it's not "off"
        if (expectedState.patternType == "custom") {
            return !currentState.isOff
        }
        
        // For named patterns, check pattern type matches
        // Note: controller may return pattern name, not type, so this is approximate
        if (currentState.pattern != "off" && currentState.pattern != "custom") {
            // Pattern is set, which is good enough for verification
            return true
        }
    }
    
    // If we can't determine match, assume it matches (better to be optimistic)
    return true
}

// Update device state from verified status
def updateStateFromVerification(Map zoneState) {
    if (!zoneState) return
    
    def isOn = !zoneState.isOff
    sendEvent(name: "switch", value: isOn ? "on" : "off")
    
    if (isOn && zoneState.pattern && zoneState.pattern != "custom") {
        // Try to match pattern to effect name
        def effectName = findEffectName(zoneState.pattern)
        if (effectName) {
            sendEvent(name: "effectName", value: effectName)
        }
    }
}

// Shared function to fetch zone data from controller (used by poll() and verification)
def fetchZoneData(Closure callback) {
    if (!controllerIP) {
        log.error "Cannot fetch zone data: Controller IP not configured"
        if (callback) callback(null)
        return
    }
    
    def url = "http://${controllerIP}/getController"
    logDebug "Fetching zone data: ${url}"
    log.debug "HTTP request details: IP=${controllerIP}, timeout=${commandTimeout ?: 10}s"
    
    try {
        httpGet([
            uri: url,
            timeout: (commandTimeout ?: 10) * 1000,
            requestContentType: "application/json"
        ]) { response ->
            log.debug "HTTP response received: status=${response.status}"
            if (response.status == 200) {
                def zones = response.data
                
                // Debug: Log raw response data
                log.debug "=== REFRESH/POLL RESPONSE DEBUG ==="
                log.debug "Response status: ${response.status}"
                // Note: Cannot use getClass() in Hubitat sandbox - use instanceof checks instead
                log.debug "Response data (raw): ${zones?.toString()}"
                
                // If zones is already a List, use it directly
                if (zones instanceof List) {
                    log.debug "Response is already a List with ${zones.size()} zones"
                    log.debug "Full zones array: ${zones}"
                    
                    def zoneData = zones.find { 
                        def zoneNum = it.num
                        zoneNum == zoneNumber || zoneNum.toString() == zoneNumber.toString()
                    }
                    
                    if (zoneData) {
                        log.debug "Found zone ${zoneNumber} data: ${zoneData}"
                        log.debug "Zone data keys: ${zoneData.keySet()}"
                        log.debug "Zone pattern field: '${zoneData.pattern}'"
                        log.debug "Zone patternType field: '${zoneData.patternType}'"
                        log.debug "Zone isOn field: ${zoneData.isOn}"
                        log.debug "Zone enabled field: ${zoneData.enabled}"
                        log.debug "Zone name field: '${zoneData.name}'"
                        log.debug "=== END RESPONSE DEBUG ==="
                    } else {
                        log.debug "Zone ${zoneNumber} not found in response"
                        log.debug "Available zones: ${zones.collect { it.num }}"
                        log.debug "=== END RESPONSE DEBUG ==="
                    }
                    
                    if (callback) callback(zoneData)
                    return
                }
                
                // If it's a String, parse it as JSON
                if (zones instanceof String) {
                    log.debug "Response is a String, parsing JSON..."
                    log.debug "String content: ${zones}"
                    try {
                        zones = new groovy.json.JsonSlurper().parseText(zones)
                        log.debug "Successfully parsed JSON string to List"
                        
                        if (zones instanceof List) {
                            log.debug "Parsed List with ${zones.size()} zones: ${zones}"
                            def zoneData = zones.find { 
                                def zoneNum = it.num
                                zoneNum == zoneNumber || zoneNum.toString() == zoneNumber.toString()
                            }
                            
                            if (zoneData) {
                                log.debug "Found zone ${zoneNumber} data: ${zoneData}"
                                log.debug "Zone data keys: ${zoneData.keySet()}"
                                log.debug "Zone pattern field: '${zoneData.pattern}'"
                                log.debug "Zone patternType field: '${zoneData.patternType}'"
                                log.debug "Zone isOn field: ${zoneData.isOn}"
                                log.debug "=== END RESPONSE DEBUG ==="
                            } else {
                                log.debug "Zone ${zoneNumber} not found in parsed response"
                                log.debug "=== END RESPONSE DEBUG ==="
                            }
                            
                            if (callback) callback(zoneData)
                        } else {
                            log.error "Parsed JSON string but result is not a List: ${zones}"
                            log.debug "=== END RESPONSE DEBUG ==="
                            if (callback) callback(null)
                        }
                    } catch (Exception e) {
                        log.error "Failed to parse JSON string: ${e.message}. First 200 chars: ${zones.take(200)}"
                        log.debug "=== END RESPONSE DEBUG ==="
                        if (callback) callback(null)
                    }
                    return
                }
                
                // If it's an InputStream (ByteArrayInputStream), read it as text and parse
                // Check for InputStream by trying to access .text property (safer than instanceof check)
                try {
                    def zonesText = zones?.text
                    if (zonesText) {
                        zones = new groovy.json.JsonSlurper().parseText(zonesText)
                        logDebug "Successfully parsed JSON from InputStream to List"
                        
                        if (zones instanceof List) {
                            def zoneData = zones.find { 
                                def zoneNum = it.num
                                zoneNum == zoneNumber || zoneNum.toString() == zoneNumber.toString()
                            }
                            if (callback) callback(zoneData)
                        } else {
                            log.error "Parsed JSON from InputStream but result is not a List"
                            if (callback) callback(null)
                        }
                        return
                    }
                } catch (Exception e) {
                    // If .text access fails, it's not an InputStream, continue to error logging
                    logDebug "Not an InputStream (or .text access failed): ${e.message}"
                }
                
                // If it's something else, log what we got
                log.error "Unexpected response.data type. Value: ${zones?.toString()?.take(200) ?: 'null'}"
                if (callback) callback(null)
            } else {
                log.error "Fetch zone data failed with HTTP status: ${response.status}"
                // Log response body/data if available (controller may return error details)
                if (response.data) {
                    try {
                        def errorData = response.data
                        if (errorData instanceof String) {
                            log.error "Controller error response: ${errorData}"
                        } else {
                            log.error "Controller error response: ${errorData.toString()}"
                        }
                    } catch (Exception e) {
                        log.debug "Could not parse error response: ${e.message}"
                    }
                }
                if (callback) callback(null)
            }
        }
    } catch (Exception e) {
        def errorMsg = e.message ?: e.toString()
        
        // Try to determine exception type without using getClass()
        def exceptionType = "Exception"
        def exceptionStr = e.toString()
        // Parse class name from toString() which often includes it (e.g., "java.net.ConnectException: Connection refused")
        if (exceptionStr.contains(":")) {
            def parts = exceptionStr.split(":", 2)
            if (parts.length > 0) {
                exceptionType = parts[0].trim()
                // Extract just the class name (last part after dots)
                if (exceptionType.contains(".")) {
                    exceptionType = exceptionType.substring(exceptionType.lastIndexOf(".") + 1)
                }
            }
        }
        
        // Check for common exception types using string matching (instanceof with fully qualified names may not work)
        if (exceptionStr.contains("Connection refused") || exceptionStr.contains("ConnectException")) {
            exceptionType = "ConnectException"
        } else if (exceptionStr.contains("timeout") || exceptionStr.contains("timed out") || exceptionStr.contains("SocketTimeoutException")) {
            exceptionType = "SocketTimeoutException"
        } else if (exceptionStr.contains("UnknownHostException")) {
            exceptionType = "UnknownHostException"
        } else if (exceptionStr.contains("IOException")) {
            exceptionType = "IOException"
        }
        
        log.error "Fetch zone data failed: ${errorMsg}"
        log.error "Exception type: ${exceptionType}"
        log.error "Controller IP configured: ${controllerIP}"
        log.error "URL attempted: http://${controllerIP}/getController"
        log.error "Full exception: ${exceptionStr}"
        if (e.cause) {
            log.error "Exception cause: ${e.cause.toString()}"
        }
        if (callback) callback(null)
    }
}

def poll() {
    if (!controllerIP) {
        log.error "Cannot poll: Controller IP not configured"
        return
    }
    
    logDebug "Polling: http://${controllerIP}/getController"
    
    // Use shared fetchZoneData function
    fetchZoneData { zoneData ->
        if (zoneData) {
            logDebug "Poll response for zone ${zoneNumber}: pattern='${zoneData.pattern}', isOn=${zoneData.isOn}"
            updateZoneState(zoneData)
        } else {
            log.warn "Zone ${zoneNumber} not found in response"
        }
    }
    
    // Schedule next poll if auto-polling enabled
    if (autoPoll) {
        def interval = pollInterval ?: 30
        runIn(interval, poll)
    }
}

// Helper Methods

// Safely convert a value to integer (handles null, empty string, and already-numeric values)
def toIntSafe(value, defaultValue = 0) {
    if (value == null || value == "") return defaultValue
    if (value instanceof Integer || value instanceof Long) return value.intValue()
    try {
        return value.toString().toInteger()
    } catch (Exception e) {
        return defaultValue
    }
}

def buildCommandUrl(Map params) {
    def query = params.collect { k, v -> "${k}=${java.net.URLEncoder.encode(v.toString(), "UTF-8")}" }.join("&")
    return "http://${controllerIP}/setPattern?${query}"
}

def updateZoneState(Map zoneData) {
    if (!zoneData) {
        log.debug "updateZoneState called with null zoneData"
        return
    }
    
    log.debug "=== UPDATE ZONE STATE DEBUG ==="
    log.debug "Raw zoneData: ${zoneData}"
    log.debug "zoneData keys: ${zoneData.keySet()}"
    log.debug "zoneData.pattern: '${zoneData.pattern}'"
    log.debug "zoneData.patternType: '${zoneData.patternType}'"
    log.debug "zoneData.isOn: ${zoneData.isOn}"
    
    // Extract pattern - handle different possible field names
    def pattern = zoneData.pattern ?: zoneData.patternType ?: "off"
    // Use isOn field from controller if available, otherwise infer from pattern
    def isOn = zoneData.isOn != null ? zoneData.isOn : (pattern != "off" && pattern != null && pattern.toString().trim() != "")
    
    log.debug "Extracted pattern: '${pattern}'"
    log.debug "Calculated isOn: ${isOn}"
    log.debug "Pattern comparison: pattern='${pattern}', pattern != 'off' = ${pattern != 'off'}, pattern != null = ${pattern != null}, pattern.trim() != '' = ${pattern?.toString()?.trim() != ''}"
    
    logDebug "Updating zone state - pattern: '${pattern}', isOn: ${isOn}"
    
    // Always update current pattern (raw pattern string from controller)
    sendEvent(name: "currentPattern", value: pattern.toString())
    
    // Update switch state
    sendEvent(name: "switch", value: isOn ? "on" : "off")
    
    // Try to match pattern to effect name (for predefined patterns)
    def effectName = null
    if (isOn && pattern != "off" && pattern != "custom") {
        effectName = findEffectName(pattern.toString())
        log.debug "Effect name lookup for pattern '${pattern}': ${effectName ?: 'not found'}"
    }
    
    // Always set effectName (null if not found or off)
    sendEvent(name: "effectName", value: effectName ?: "")
    
    log.debug "=== END UPDATE ZONE STATE DEBUG ==="
}

def findEffectName(String patternType) {
    // Try to find matching effect by patternType from state (same approach as custom patterns)
    def patternsMap = state.patternsMap
    if (!patternsMap) return null
    def match = patternsMap.find { name, url -> url.contains("patternType=${patternType}") }
    return match ? match.key : null
}

// Color Conversion Utilities

def rgbToHsv(int r, int g, int b) {
    r = r / 255.0
    g = g / 255.0
    b = b / 255.0
    
    def max = Math.max(Math.max(r, g), b)
    def min = Math.min(Math.min(r, g), b)
    def delta = max - min
    
    def h = 0
    def s = 0
    def v = max * 100
    
    if (delta != 0) {
        s = delta / max * 100
        
        if (max == r) {
            h = ((g - b) / delta) % 6
        } else if (max == g) {
            h = (b - r) / delta + 2
        } else {
            h = (r - g) / delta + 4
        }
        
        h = Math.round(h * 60)
        if (h < 0) h += 360
    }
    
    return [h, Math.round(s), Math.round(v)]
}

def hsvToRgb(int h, int s, int v) {
    h = h % 360
    s = s / 100.0
    v = v / 100.0
    
    def c = v * s
    def hDiv60 = h / 60.0
    // Calculate (hDiv60 % 2) manually since BigDecimal doesn't support mod()
    // (x % 2) = x - 2 * floor(x/2)
    def modPart = hDiv60 - 2 * Math.floor(hDiv60 / 2)
    def x = c * (1 - Math.abs(modPart - 1))
    def m = v - c
    
    def r = 0, g = 0, b = 0
    
    if (h < 60) {
        r = c; g = x; b = 0
    } else if (h < 120) {
        r = x; g = c; b = 0
    } else if (h < 180) {
        r = 0; g = c; b = x
    } else if (h < 240) {
        r = 0; g = x; b = c
    } else if (h < 300) {
        r = x; g = 0; b = c
    } else {
        r = c; g = 0; b = x
    }
    
    return [
        Math.round((r + m) * 255),
        Math.round((g + m) * 255),
        Math.round((b + m) * 255)
    ]
}

// Logging

def logDebug(String msg) {
    if (logEnable) {
        log.debug "[Zone ${zoneNumber}] ${msg}"
    }
}


