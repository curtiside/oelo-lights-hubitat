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
 * - Patterns: Up to 200 user-defined patterns with custom names
 * - Spotlight Plan Lights: Comma-delimited list of LED indices for spotlight plans
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
 * - `setPattern()` - Set pattern chosen from Pattern Selection dropdown
 * - `off()` - Turn off lights
 * - `refresh()` - Get current state from controller
 * - `getPattern()` - Capture current pattern from controller and save it
 * 
 * **Pattern Selection:**
 * - Patterns: Configured in device preferences (up to 200 patterns)
 * - Patterns are selected via dropdown menu in device preferences
 * - Commands use the selected pattern from the dropdown
 * 
 * ## Pattern Support
 * 
 * **Patterns:**
 * - Up to 200 patterns can be configured per device
 * - Patterns support two types: spotlight plans and non-spotlight plans
 * - Spotlight plans: Specific LEDs on, all others off (uses spotlightPlanLights setting)
 * - Non-spotlight plans: Standard pattern types (march, stationary, river, etc.)
 * - Pattern names are user-defined
 * - Patterns appear in Pattern Selection dropdown with plan type indicator
 * 
 * ## Attributes
 * 
 * - `zone`: Zone number (1-6)
 * - `controllerIP`: Controller IP address
 * - `lastCommand`: Last command URL sent
 * - `currentPattern`: Current pattern string from controller (e.g., "march", "off", "custom")
 * - `effectName`: Current pattern name if it matches a saved pattern (empty if not found or off)
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
 */

// Constants
final String DRIVER_VERSION = "0.8.3"  // Driver version
final int MAX_LEDS = 500  // Maximum number of LEDs per zone
final String DEFAULT_SPOTLIGHT_PLAN_LIGHTS = "1,2,3,4,8,9,10,11,21,22,23,24,25,35,36,37,38,59,60,61,62,67,68,69,70,93,94,95,112,113,114,115,132,133,134,135,153,154,155,156"

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
        capability "Switch"
        
        // Custom attributes
        attribute "zone", "number"
        attribute "controllerIP", "string"
        attribute "lastCommand", "string"
        attribute "currentPattern", "string"
        attribute "effectName", "string"
        attribute "verificationStatus", "string"
        attribute "driverVersion", "string"
        attribute "switch", "string"
        attribute "availablePatterns", "string"
        
        // Custom commands
        command "setPattern"
        command "getPattern"
        command "loadPredefinedPatterns"
        command "on"
        command "off"
    }
    
    preferences {
        section("Controller Settings") {
            input name: "controllerIP", type: "text", title: "Controller IP Address", required: true, description: "IP address of Oelo controller"
            input name: "zoneNumber", type: "number", title: "Zone Number", range: "1..6", required: true, defaultValue: 1, description: "Zone number (1-6)"
        }
        
        section("Pattern Selection") {
            input name: "selectedPattern", type: "enum", title: "Select Pattern", 
                options: getPatternOptions(), 
                required: false, description: "Choose a pattern to set"
        }
        
        section("Pattern Management") {
            input name: "renamePattern", type: "enum", title: "Select Pattern to Rename", 
                options: getPatternOptions(), 
                required: false, description: "Select a pattern to rename${state.lastCapturedPatternName ? " (Most recent: ${state.lastCapturedPatternName})" : ""}"
            input name: "newPatternName", type: "text", title: "New Pattern Name", 
                required: false, description: "Enter new name for the selected pattern (pattern will be renamed when preferences are saved)"
            input name: "deletePattern", type: "enum", title: "Delete Pattern", 
                options: getPatternOptions(), 
                required: false, description: "Select a pattern to delete (pattern will be deleted when preferences are saved)"
        }
        
        section("Spotlight Plan Settings") {
            input name: "maxLeds", type: "number", title: "Maximum LEDs per Zone", 
                required: false, defaultValue: MAX_LEDS, range: "1..500",
                description: "Maximum number of LEDs in this zone (default: ${MAX_LEDS})"
            input name: "spotlightPlanLights", type: "text", title: "Spotlight Plan Lights", 
                required: false, defaultValue: DEFAULT_SPOTLIGHT_PLAN_LIGHTS,
                description: "Comma-delimited list of LED indices to turn on for spotlight plans (e.g., '1,3,5,7,9').\n\nCurrent value:\n${getSpotlightPlanLightsDisplay()}"
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
    debugLog "updated() called - checking all preferences"
    
    // Set driver version immediately
    setDriverVersion()
    
    // Pre-select most recently captured pattern in renamePattern if not set
    if ((!settings.renamePattern || settings.renamePattern == "") && state.lastCapturedPatternName) {
        def lastCaptured = state.lastCapturedPatternName
        def patterns = state.patterns ?: []
        def patternExists = patterns.find { it && it.name == lastCaptured }
        if (patternExists) {
            // Try to set renamePattern to last captured pattern
            // Note: app.updateSetting() may not work in drivers, but we'll try
            try {
                app.updateSetting("renamePattern", lastCaptured)
                debugLog "Pre-selected most recently captured pattern '${lastCaptured}' in renamePattern"
            } catch (Exception e) {
                debugLog "Could not pre-select renamePattern (may not be supported in drivers): ${e.message}"
            }
        }
    }
    
    // Handle pattern renaming if renamePattern and newPatternName preferences were set
    if (settings.renamePattern && settings.renamePattern != "" && settings.newPatternName && settings.newPatternName.trim() != "") {
        def oldPatternName = settings.renamePattern
        def newPatternName = settings.newPatternName.trim()
        
        def patterns = state.patterns ?: []
        
        // Check if pattern with old name exists - if not, it was already renamed or doesn't exist
        def patternToRename = patterns.find { it && it.name == oldPatternName }
        if (!patternToRename) {
            // Pattern with old name doesn't exist - already renamed or never existed, skip
            debugLog "Pattern '${oldPatternName}' not found - already renamed or doesn't exist, skipping rename action"
        } else if (patternToRename.name == newPatternName) {
            // Check if pattern already has the new name (no change needed)
            debugLog "Pattern '${oldPatternName}' already has name '${newPatternName}', skipping rename action"
        } else {
            log.warn "Preference action: Renaming pattern '${oldPatternName}' to '${newPatternName}'"
        log.info "Renaming pattern: '${oldPatternName}' to '${newPatternName}'"
        
        // Check if new name already exists (and it's not the same pattern)
        def nameExists = patterns.find { it && it.name == newPatternName && it != patternToRename }
        if (nameExists) {
            log.warn "Cannot rename: Pattern name '${newPatternName}' already exists"
        } else {
            patternToRename.name = newPatternName
            state.patterns = patterns
            log.info "Renamed pattern '${oldPatternName}' to '${newPatternName}'"
            updateAvailablePatternsAttribute()
        }
        }
    }
    
    // Handle pattern deletion if deletePattern preference was set
    if (settings.deletePattern && settings.deletePattern != "") {
        def patternName = settings.deletePattern
        log.warn "Preference action: Deleting pattern '${patternName}'"
        log.info "Deleting pattern: ${patternName}"
        
        def patterns = state.patterns ?: []
        def indexToDelete = -1
        
        // Find pattern to delete
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns[i] && patterns[i].name == patternName) {
                indexToDelete = i
                break
            }
        }
        
        if (indexToDelete != -1) {
            // Remove pattern and compact list
            patterns.remove(indexToDelete)
            // Remove trailing nulls
            while (patterns.size() > 0 && patterns[patterns.size() - 1] == null) {
                patterns.remove(patterns.size() - 1)
            }
            
            state.patterns = patterns
            log.info "Deleted pattern '${patternName}' and compacted list"
            updateAvailablePatternsAttribute()
            
            // Clear the deletePattern setting (can't use app.updateSetting in driver, will clear on next save)
            // User will need to save preferences again to clear the selection
        } else {
            log.warn "Pattern '${patternName}' not found for deletion"
        }
    }
    
    // Handle spotlightPlanLights setting changes
    def currentSpotlightLights = settings.spotlightPlanLights ?: ""
    def previousSpotlightLights = state.previousSpotlightPlanLights ?: ""
    
    if (currentSpotlightLights != previousSpotlightLights) {
        log.info "spotlightPlanLights setting changed, re-modifying all saved spotlight plans..."
        debugLog "Previous value: '${previousSpotlightLights}'"
        debugLog "New value: '${currentSpotlightLights}'"
        
        // Normalize new value
        def normalized = currentSpotlightLights.trim()
        if (normalized) {
            // Use preference if set, otherwise use constant (convert to integer)
            def maxLeds = (settings.maxLeds ?: MAX_LEDS).toInteger()
            normalized = normalizeLedIndices(normalized, maxLeds)
            
            // Save normalized value back to preference if it changed
            // Note: Can't update settings in drivers, but normalized value will be used
            if (normalized != currentSpotlightLights) {
                debugLog "Normalized spotlightPlanLights differs from setting, using normalized value"
                currentSpotlightLights = normalized
            }
        }
        
        // Re-modify all saved spotlight plans
        def patterns = state.patterns ?: []
        def modifiedCount = 0
        patterns.eachWithIndex { pattern, index ->
            if (pattern && pattern.planType == "spotlight") {
                def urlParams = pattern.urlParams
                def numColors = urlParams.num_colors ? urlParams.num_colors.toInteger() : 1
                // Use original colors if available, otherwise use current colors
                def colors = pattern.originalColors ?: urlParams.colors ?: ""
                
                if (normalized && normalized.trim() != "") {
                    def maxLeds = (settings.maxLeds ?: MAX_LEDS).toInteger()
                    def modifiedColors = modifySpotlightPlan(colors, normalized, numColors.toInteger(), maxLeds)
                    urlParams.colors = modifiedColors
                    patterns[index] = pattern
                    modifiedCount++
                    debugLog "Re-modified spotlight plan '${pattern.name}' with new LED indices: ${normalized}"
                }
            }
        }
        
        if (modifiedCount > 0) {
            state.patterns = patterns
            updateAvailablePatternsAttribute()
            log.info "Re-modified ${modifiedCount} spotlight plan(s) with new spotlightPlanLights setting"
        }
        
        // Store current value for next comparison
        state.previousSpotlightPlanLights = currentSpotlightLights
    } else if (currentSpotlightLights && currentSpotlightLights.trim() != "") {
        // Normalize even if value didn't change (in case user entered invalid format)
        // Use actual LED count (500) for normalization, not num_colors (which is RGB triplets)
        def maxLeds = 500  // Default zone LED count
        def normalized = normalizeLedIndices(currentSpotlightLights, maxLeds)
        if (normalized != currentSpotlightLights) {
            debugLog "Normalized spotlightPlanLights differs from setting, using normalized value"
            state.previousSpotlightPlanLights = normalized
        }
    }
    
    initialize()
}

// Set driver version in state and attribute (called unconditionally)
def setDriverVersion() {
    def driverVersion = DRIVER_VERSION
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
    
    // Initialize patterns storage if not exists
    if (!state.patterns) {
        state.patterns = []
    }
    
    // Migrate old settings-based patterns to state (one-time migration)
    migrateOldPatterns()
    
    // Load predefined patterns from PATTERNS map (one-time)
    doLoadPredefinedPatterns()
    
    // Update available patterns attribute
    updateAvailablePatternsAttribute()
    
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

// Custom command: Turn on lights (Switch capability)
def on() {
    log.warn "on() command called"
    // Use last used pattern, or selected pattern from preferences, or first available pattern
    def patternName = state.lastUsedPattern ?: settings.selectedPattern
    if (patternName && patternName != "") {
        logDebug "Turning zone ${zoneNumber} ON with pattern: ${patternName}"
        setEffect(patternName)
    } else {
        // Try to use first available pattern if no last used or selected
        def patterns = state.patterns ?: []
        def firstPattern = patterns.find { it && it.name }
        if (firstPattern) {
            logDebug "Turning zone ${zoneNumber} ON with first available pattern: ${firstPattern.name}"
            setEffect(firstPattern.name)
        } else {
            log.warn "No pattern available. Please capture a pattern using getPattern() command or select one in Preferences → Pattern Selection."
        }
    }
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

// Internal function: Set effect (used by setPattern command)
def setEffect(String effectName) {
    logDebug "Setting effect: ${effectName}"
    
    def patternUrl = getPatternUrl(effectName)
    if (!patternUrl) {
        log.error "Unknown effect: ${effectName}. Available: ${buildEffectList()}"
        return
    }
    
    // Validate the pattern URL before sending
    def urlParams = parseUrlParams(patternUrl)
    if (!validatePatternUrl(patternUrl, urlParams)) {
        log.error "Invalid pattern URL for '${effectName}': validation failed"
        log.error "Invalid URL (full): ${patternUrl}"
        return
    }
    
    // Check if this would be a no-op (same as what's currently active)
    // Skip no-op detection for spotlight plans since they're modified and won't match controller state
    def patterns = state.patterns ?: []
    def pattern = patterns.find { it && it.name == effectName }
    if (pattern && pattern.urlParams && pattern.urlParams.originalUrlString) {
        // Ensure planType exists (lazy evaluation)
        pattern = ensurePlanType(pattern)
        
        // Skip spotlight plans - they're modified so URL comparison won't work
        if (pattern.planType != "spotlight") {
            // Compare with stored original URL (normalize for comparison)
            def storedUrl = pattern.urlParams.originalUrlString
            if (normalizeUrlForComparison(patternUrl) == normalizeUrlForComparison(storedUrl)) {
                debugLog "Pattern '${effectName}' URL matches stored URL - checking if controller state matches..."
                // Could fetch current state and compare, but for now just log
                debugLog "No-op detected: URL matches stored pattern"
            }
        }
    }
    
    // Send command and store for future on() calls
    def success = sendCommand(patternUrl)
    if (success) {
        sendEvent(name: "effectName", value: effectName)
        sendEvent(name: "lastCommand", value: patternUrl)
        sendEvent(name: "switch", value: "on")
        state.lastUsedPattern = effectName  // Store for on() command
        logDebug "Pattern '${effectName}' set and stored"
    }
}

// Custom command: Set pattern (supports both parameterized and preference-based)
def setPattern(String patternName = null) {
    log.warn "setPattern() command called${patternName ? " with pattern: ${patternName}" : ""}"
    
    // If patternName parameter provided, use it; otherwise use preference selection
    def selectedPattern = patternName ?: settings.selectedPattern
    
    if (!selectedPattern || selectedPattern == "") {
        log.warn "No pattern specified. Please provide a pattern name as parameter or select a pattern from Preferences → Pattern Selection section first."
        log.warn "Available patterns: ${buildEffectList()}"
        return
    }
    
    log.info "Setting pattern: ${selectedPattern}"
    setEffect(selectedPattern)
}

// Custom command: Load predefined patterns from PATTERNS map
def loadPredefinedPatterns() {
    log.info "loadPredefinedPatterns() command called"
    // Reset the marker to allow reloading
    state.predefinedPatternsLoaded = false
    doLoadPredefinedPatterns()
    log.info "Predefined patterns loading completed"
}

// Custom command: Get current pattern from controller and store it
def getPattern() {
    log.warn "getPattern() command called"
    log.info "=== GET PATTERN COMMAND STARTED ==="
    debugLog "[Step 1] Checking controller IP configuration..."
    
    if (!controllerIP) {
        log.error "[FAILED] Step 1: Controller IP not configured"
        debugLog "=== GET PATTERN COMMAND FAILED ==="
        return
    }
    debugLog "[SUCCESS] Step 1: Controller IP configured: ${controllerIP}"
    
    debugLog "[Step 2] Fetching zone data from controller..."
    // Fetch current zone state
    fetchZoneData { zoneData ->
        debugLog "[Step 2] fetchZoneData callback received"
        
        if (!zoneData) {
            log.error "[FAILED] Step 2: Could not retrieve zone data from controller (IP: ${controllerIP}, Zone: ${zoneNumber})"
            debugLog "=== GET PATTERN COMMAND FAILED ==="
            return
        }
        debugLog "[SUCCESS] Step 2: Zone data retrieved"
        debugLog "Zone data keys: ${zoneData.keySet()}"
        debugLog "Zone data: ${zoneData}"
        
        debugLog "[Step 3] Extracting pattern information..."
        // Extract pattern information
        def pattern = zoneData.pattern ?: zoneData.patternType ?: "off"
        debugLog "Pattern field value: '${zoneData.pattern}'"
        debugLog "PatternType field value: '${zoneData.patternType}'"
        debugLog "Extracted pattern: '${pattern}'"
        
        debugLog "[Step 4] Determining if zone is on..."
        // Use isOn field from controller if available, otherwise infer from pattern
        def isOn = zoneData.isOn != null ? zoneData.isOn : (pattern != "off" && pattern != null && pattern.toString().trim() != "")
        debugLog "zoneData.isOn field: ${zoneData.isOn}"
        debugLog "Calculated isOn: ${isOn}"
        
        if (pattern == "off" || !isOn) {
            log.warn "[FAILED] Step 4: Zone is currently off - no pattern to capture"
            debugLog "Pattern check: pattern='${pattern}' (off=${pattern == "off"})"
            debugLog "isOn check: isOn=${isOn}"
            debugLog "=== GET PATTERN COMMAND FAILED ==="
            return
        }
        debugLog "[SUCCESS] Step 4: Zone is on with pattern '${pattern}'"
        
        debugLog "[Step 5] Building URL parameters from zone data..."
        // Build URL parameters from zone data
        def urlParams = buildPatternParamsFromZoneData(zoneData)
        if (!urlParams) {
            log.error "[FAILED] Step 5: Could not build pattern parameters from zone data"
            debugLog "buildPatternParamsFromZoneData returned null"
            debugLog "=== GET PATTERN COMMAND FAILED ==="
            return
        }
        debugLog "[SUCCESS] Step 5: URL parameters built"
        debugLog "URL parameters: ${urlParams}"
        
        // Detect plan type
        def planType = identifyPlanType(urlParams.patternType ?: pattern)
        debugLog "[Step 5a] Plan type detected: '${planType}'"
        
        // Store original colors for spotlight plans BEFORE modification
        def originalColors = null
        if (planType == "spotlight" && urlParams.colors) {
            originalColors = urlParams.colors
        }
        
        // Handle spotlight plan modification
        if (planType == "spotlight") {
            debugLog "[Step 5b] Processing spotlight plan modification..."
            def numColors = urlParams.num_colors ? urlParams.num_colors.toInteger() : 1
            def colors = urlParams.colors ?: ""
            
            // Get actual LED count: prefer preference, then zone data, then constant
            // Convert to integer to ensure proper type
            def maxLeds = (settings.maxLeds ?: zoneData.ledCnt ?: zoneData.numberOfColors ?: MAX_LEDS).toInteger()
            debugLog "Using maxLeds=${maxLeds} for normalization (preference=${settings.maxLeds}, ledCnt=${zoneData.ledCnt}, numberOfColors=${zoneData.numberOfColors}, num_colors=${numColors})"
            
            // Use default spotlight plan lights constant
            def defaultSpotlightLights = DEFAULT_SPOTLIGHT_PLAN_LIGHTS
            
            // Check if spotlightPlanLights is null/empty - use default, otherwise use setting
            def spotlightLights = settings.spotlightPlanLights
            if (!spotlightLights || spotlightLights.trim() == "") {
                debugLog "spotlightPlanLights is empty, using default value..."
                spotlightLights = defaultSpotlightLights
            }
            
            // Normalize the value (whether from setting or default) using actual LED count
            spotlightLights = normalizeLedIndices(spotlightLights, maxLeds)
            // Note: Can't update settings in drivers, but normalized value will be used
            if (spotlightLights != settings.spotlightPlanLights && spotlightLights != defaultSpotlightLights) {
                debugLog "Normalized spotlightPlanLights differs from setting, using normalized value"
            }
            
            // Modify colors array based on spotlightPlanLights
            if (spotlightLights && spotlightLights.trim() != "") {
                def modifiedColors = modifySpotlightPlan(colors, spotlightLights, numColors.toInteger(), maxLeds)
                urlParams.colors = modifiedColors
                // Update num_colors to match the new array size
                urlParams.num_colors = maxLeds.toString()
                debugLog "Modified spotlight plan colors using LED indices: ${spotlightLights} (created ${maxLeds} RGB triplets)"
            }
        }
        
        debugLog "[Step 6] Generating stable pattern ID from fields..."
        // Generate stable pattern ID from pattern type and key parameters
        // Available fields: pattern, name (zone name), num, numberOfColors, direction, speed, gap, rgbOrder
        // Use pattern type as base, add descriptive suffix if parameters are non-default
        // Note: Zone number is not included since each device instance is zone-specific
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
        // Don't include numberOfColors for spotlight patterns since they can be modified to different LED counts
        // For non-spotlight patterns, numberOfColors represents the actual pattern size
        if (planType != "spotlight" && zoneData.numberOfColors && zoneData.numberOfColors > 1) {
            suffixParts.add("${zoneData.numberOfColors}colors")
        }
        
        // Build stable ID from pattern type and parameters (no timestamp, no zone - device is zone-specific)
        if (suffixParts.isEmpty()) {
            patternId = pattern.toString()
        } else {
            patternId = "${pattern}_${suffixParts.join('_')}"
        }
        
        debugLog "zoneData.name (zone name): '${zoneData.name}'"
        debugLog "zoneData.pattern (pattern type): '${pattern}'"
        debugLog "zoneData.direction: '${zoneData.direction}'"
        debugLog "zoneData.speed: '${zoneData.speed}'"
        debugLog "zoneData.numberOfColors: '${zoneData.numberOfColors}'"
        debugLog "Generated stable pattern ID: '${patternId}'"
        debugLog "[SUCCESS] Step 6: Stable pattern ID determined: '${patternId}'"
        
        // Build and validate the complete pattern URL string
        def patternString = buildCommandUrl(urlParams)
        checkPlanStringLength(patternString, patternId)
        
        // Validate the complete pattern URL string
        if (!validatePatternUrl(patternString, urlParams)) {
            log.error "[FAILED] Pattern URL validation failed for pattern '${patternId}'"
            log.error "Invalid pattern URL (full): ${patternString}"
            debugLog "=== GET PATTERN COMMAND FAILED ==="
            return
        }
        
        // Store the original URL string for no-op detection
        urlParams.originalUrlString = patternString
        
        debugLog "[Step 7] Retrieving patterns list from state..."
        // Get patterns list
        def patterns = state.patterns ?: []
        debugLog "Current patterns count: ${patterns.size()}"
        debugLog "Patterns: ${patterns}"
        debugLog "[SUCCESS] Step 7: Patterns list retrieved"
        
        debugLog "[Step 7a] Setting initial display name..."
        // Use the stable ID as the initial display name (user can edit this later)
        def patternName = patternId
        debugLog "Initial display name set to: '${patternName}'"
        debugLog "[SUCCESS] Step 7a: Initial display name set"
        
        debugLog "[Step 8] Checking for existing pattern with same ID..."
        // Check if a pattern with this ID already exists (prevents duplicates)
        def existingIndex = patterns.findIndexOf { it && it.id == patternId }
        debugLog "Existing pattern search by ID: index=${existingIndex >= 0 ? existingIndex : 'not found'}"
        
        if (existingIndex >= 0) {
            debugLog "[Step 9] Pattern with same ID exists - updating parameters..."
            // Pattern with same ID exists - update urlParams but keep existing name (user may have renamed it)
            def existingName = patterns[existingIndex].name
            patterns[existingIndex].urlParams = urlParams
            patterns[existingIndex].planType = planType
            // Store original colors for spotlight plans
            if (planType == "spotlight" && urlParams.colors) {
                patterns[existingIndex].originalColors = urlParams.colors
            }
            state.patterns = patterns
            updateAvailablePatternsAttribute()
            
            // Track most recently captured pattern for rename pre-selection
            state.lastCapturedPatternName = existingName
            
            log.info "[SUCCESS] Step 9: Updated existing pattern '${existingName}' (ID: ${patternId}) with new parameters (planType: ${planType})"
            debugLog "Updated pattern: ${patterns[existingIndex]}"
            debugLog "=== GET PATTERN COMMAND COMPLETED SUCCESSFULLY ==="
        } else {
            debugLog "[Step 9] Finding next empty slot for new pattern..."
            // Find next empty slot for new pattern
            def nextSlot = findNextEmptyPatternSlot(patterns)
            debugLog "Next empty slot: ${nextSlot >= 0 ? nextSlot : 'none found'}"
            
            if (nextSlot == -1) {
                log.error "[FAILED] Step 9: No empty slots available (maximum 200 patterns)"
                debugLog "Current patterns count: ${patterns.size()}"
                debugLog "=== GET PATTERN COMMAND FAILED ==="
        return
    }
            debugLog "[SUCCESS] Step 9: Found empty slot ${nextSlot}"
            
            debugLog "[Step 10] Storing new pattern in slot ${nextSlot}..."
            // Store new pattern with both ID (stable) and name (display)
            if (patterns.size() < nextSlot) {
                debugLog "Extending patterns list from ${patterns.size()} to ${nextSlot}"
                // Extend list if needed
                while (patterns.size() < nextSlot) {
                    patterns.add(null)
                }
            }
            patterns[nextSlot - 1] = [
                id: patternId,
                name: patternName,
                urlParams: urlParams,
                planType: planType,
                originalColors: originalColors  // Store original for spotlight plans (set earlier)
            ]
            debugLog "Pattern stored at index ${nextSlot - 1}: id='${patternId}', name='${patternName}', planType='${planType}'"
            
            state.patterns = patterns
            debugLog "State updated. New patterns count: ${state.patterns.size()}"
            
            log.info "[SUCCESS] Step 10: Stored new pattern '${patternName}' (ID: ${patternId}) in slot ${nextSlot}"
            debugLog "Final patterns list: ${state.patterns}"
            
            // Track most recently captured pattern for rename pre-selection
            state.lastCapturedPatternName = patternName
            
            debugLog "=== GET PATTERN COMMAND COMPLETED SUCCESSFULLY ==="
        }
    }
}

// Build pattern URL parameters from zone data returned by getController
def buildPatternParamsFromZoneData(Map zoneData) {
    debugLog "buildPatternParamsFromZoneData: Starting with zoneData keys: ${zoneData.keySet()}"
    
    def pattern = zoneData.pattern ?: zoneData.patternType ?: "off"
    debugLog "buildPatternParamsFromZoneData: Extracted pattern: '${pattern}'"
    
    if (pattern == "off") {
        debugLog "buildPatternParamsFromZoneData: Pattern is 'off', returning null"
    return null
}

    // Extract parameters from zone data
    def colorStr = zoneData.colorStr ?: ""
    
    // Log the raw colorStr to see what we're receiving
    debugLog "buildPatternParamsFromZoneData: Raw colorStr length=${colorStr.length()}"
    debugLog "buildPatternParamsFromZoneData: Full colorStr: ${colorStr}"
    
    // Count expected parts (should be ledCnt * 3 for RGB values)
    def expectedParts = zoneData.ledCnt ? (zoneData.ledCnt * 3) : 0
    def actualParts = colorStr ? colorStr.split("&").size() : 0
    debugLog "buildPatternParamsFromZoneData: Expected ${expectedParts} color parts (${zoneData.ledCnt} LEDs × 3), actual parts in colorStr: ${actualParts}"
    
    def parsedColors = colorStr ? parseColorStr(colorStr) : "255,255,255"
    
    // Count how many RGB triplets were parsed (this is the actual number of LEDs with color data)
    def parsedColorCount = parsedColors.split(",").size() / 3
    
    // num_colors should match the number of RGB triplets in the colors parameter
    // Use parsedColorCount to ensure we send the correct number
    // Fall back to ledCnt or numberOfColors if parsing failed
    def numColors = parsedColorCount > 0 ? parsedColorCount : (zoneData.ledCnt ?: zoneData.numberOfColors ?: 1)
    
    debugLog "buildPatternParamsFromZoneData: ledCnt=${zoneData.ledCnt}, numberOfColors=${zoneData.numberOfColors}"
    debugLog "buildPatternParamsFromZoneData: parsed ${parsedColorCount} RGB triplets"
    debugLog "buildPatternParamsFromZoneData: using num_colors=${numColors} (should match number of RGB triplets)"
    
    // Verify we have enough colors for all LEDs
    if (zoneData.ledCnt && parsedColorCount < zoneData.ledCnt) {
        log.warn "buildPatternParamsFromZoneData: Warning - parsed ${parsedColorCount} RGB triplets but ledCnt=${zoneData.ledCnt}. Some LED data may be missing."
        log.warn "buildPatternParamsFromZoneData: Expected ${expectedParts} parts in colorStr, got ${actualParts} parts"
    }
    
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
    
    debugLog "buildPatternParamsFromZoneData: Built params: ${params}"
    return params
}

// Generate stable pattern ID from URL parameters (used to identify unique patterns)

// Parse colorStr format (e.g., "255&242&194&255&242&194...") to comma-separated RGB
// colorStr contains RGB values for ALL LEDs separated by &
// Format: "R&G&B&R&G&B&..." where each R,G,B triplet represents one LED
def parseColorStr(String colorStr) {
    if (!colorStr || colorStr.trim() == "") {
        debugLog "parseColorStr: Empty colorStr, returning default white"
        return "255,255,255"
    }
    
    // colorStr format: "R&G&B&R&G&B&..."
    def parts = colorStr.split("&")
    def totalParts = parts.size()
    def expectedTriplets = totalParts / 3
    debugLog "parseColorStr: Input has ${totalParts} parts, expecting ${expectedTriplets} RGB triplets"
    
    def colors = []
    for (int i = 0; i < parts.size(); i += 3) {
        if (i + 2 < parts.size()) {
            colors.add("${parts[i]},${parts[i+1]},${parts[i+2]}")
        } else {
            // Handle incomplete triplet at the end
            debugLog "parseColorStr: Warning - incomplete RGB triplet at position ${i}, skipping"
        }
    }
    
    def result = colors.join(",")
    debugLog "parseColorStr: Parsed ${colors.size()} RGB triplets (${colors.size() * 3} color values total)"
    debugLog "parseColorStr: Result length: ${result.length()} characters"
    
    return result
}

// Plan Type Detection Functions

// Identify plan type from patternType string
def identifyPlanType(String patternType) {
    if (!patternType) return "non-spotlight"
    return (patternType == "spotlight") ? "spotlight" : "non-spotlight"
}

// Ensure planType exists in pattern (lazy evaluation for backward compatibility)
def ensurePlanType(Map pattern) {
    // If planType already exists, return pattern as-is
    if (pattern.planType) {
        return pattern
    }
    
    // Lazy evaluation: determine planType from urlParams
    try {
        def patternType = pattern.urlParams?.patternType ?: "unknown"
        def planType = identifyPlanType(patternType.toString())
        
        // Update pattern object with planType
        pattern.planType = planType
        
        // Save updated pattern back to state
        def patterns = state.patterns ?: []
        def index = patterns.findIndexOf { it && it.id == pattern.id }
        if (index >= 0) {
            patterns[index] = pattern
            state.patterns = patterns
            debugLog "Lazy evaluation: Populated planType='${planType}' for pattern '${pattern.name}'"
        }
    } catch (Exception e) {
        // Never fail - default to non-spotlight
        log.warn "Could not evaluate planType for pattern '${pattern.name}', defaulting to 'non-spotlight': ${e.message}"
        pattern.planType = "non-spotlight"
    }
    
    return pattern
}

// Check plan string length and log warning if at full length
def checkPlanStringLength(String patternString, String patternName) {
    if (!patternString || !patternName) return
    
    def length = patternString.length()
    def stateThreshold = 62000  // 95% of 65536
    def preferenceThreshold = 157  // 95% of 166
    
    if (length >= stateThreshold) {
        log.warn "WARNING: Plan '${patternName}' received at full length (${length} chars). This may indicate truncation or maximum capacity reached (state limit: ~64KB)."
    } else if (length >= preferenceThreshold) {
        log.warn "WARNING: Plan '${patternName}' received at full length (${length} chars). This may indicate truncation (preference field limit: ~166 chars)."
    }
}

// Spotlight Plan Functions

// Get display value for spotlightPlanLights preference (with word wrapping)
def getSpotlightPlanLightsDisplay() {
    // Handle null settings during metadata parsing
    if (!settings) {
        return "Not set (will auto-extract from plan)"
    }
    def value = settings.spotlightPlanLights
    if (!value || value.trim() == "") {
        return "Not set (will auto-extract from plan)"
    }
    
    // Word wrap the value: break into chunks of ~15 items per line for better display
    def trimmed = value.trim()
    def parts = trimmed.split(",")
    def wrapped = []
    def currentLine = []
    def lineLength = 0
    def maxLineLength = 50  // Approximate characters per line
    
    parts.each { part ->
        def trimmedPart = part.trim()
        if (trimmedPart) {
            def partLength = trimmedPart.length() + 1  // +1 for comma
            if (lineLength + partLength > maxLineLength && currentLine.size() > 0) {
                // Start new line
                wrapped.add(currentLine.join(","))
                currentLine = [trimmedPart]
                lineLength = partLength
            } else {
                currentLine.add(trimmedPart)
                lineLength += partLength
            }
        }
    }
    
    if (currentLine.size() > 0) {
        wrapped.add(currentLine.join(","))
    }
    
    return wrapped.join(",\n")
}

// Normalize LED indices: eliminate duplicates, sort, skip invalid
def normalizeLedIndices(String ledIndices, int numColors) {
    if (!ledIndices || ledIndices.trim() == "") {
        return ""
    }
    
    try {
        def indices = []
        def parts = ledIndices.split(",")
        
        parts.each { part ->
            def trimmed = part.trim()
            if (trimmed) {
                try {
                    def index = trimmed.toInteger()
                    // Validate: 1-based indexing, must be between 1 and numColors
                    if (index >= 1 && index <= numColors) {
                        if (!indices.contains(index)) {
                            indices.add(index)
                        }
                    } else {
                        log.warn "Skipping invalid LED index ${index} (must be between 1 and ${numColors})"
                    }
                } catch (NumberFormatException e) {
                    log.warn "Skipping invalid LED index format: '${trimmed}'"
                }
            }
        }
        
        // Sort numerically
        indices.sort()
        
        return indices.join(",")
    } catch (Exception e) {
        log.error "Error normalizing LED indices: ${e.message}"
        return ledIndices.trim()  // Return original on error
    }
}

// Extract list of ON LEDs from plan colors array
def extractOnLedsFromPlan(String colors, int numColors) {
    if (!colors || colors.trim() == "") {
        return ""
    }
    
    try {
        def onLeds = []
        def parts = colors.split(",")
        
        // Colors array is comma-separated RGB triplets: R,G,B,R,G,B,...
        // Each LED has 3 values, so LED index = (position / 3) + 1 (1-based)
        for (int i = 0; i < parts.size(); i += 3) {
            if (i + 2 < parts.size()) {
                try {
                    def r = parts[i].trim().toInteger()
                    def g = parts[i + 1].trim().toInteger()
                    def b = parts[i + 2].trim().toInteger()
                    
                    // If RGB values are not all zero, LED is ON
                    if (r != 0 || g != 0 || b != 0) {
                        def ledIndex = (i / 3) + 1  // 1-based indexing
                        // Use preference if set, otherwise use constant for validation
                        // numColors is RGB triplets, not total LEDs
                        def maxLeds = settings.maxLeds ?: MAX_LEDS
                        if (ledIndex >= 1 && ledIndex <= maxLeds) {
                            onLeds.add(ledIndex)
                        }
                    }
                } catch (NumberFormatException e) {
                    debugLog "Skipping invalid RGB values at position ${i}"
                }
            }
        }
        
        // Normalize the extracted list using preference if set, otherwise use constant
        def maxLeds = settings.maxLeds ?: MAX_LEDS
        return normalizeLedIndices(onLeds.join(","), maxLeds)
    } catch (Exception e) {
        log.error "Error extracting ON LEDs from plan: ${e.message}"
        return ""
    }
}

// Modify spotlight plan colors array based on spotlightPlanLights setting
// numColors is the number of RGB triplets in the original colors array
// maxLeds is the actual number of LEDs in the zone (used to create full array)
def modifySpotlightPlan(String colors, String spotlightLights, int numColors, int maxLeds) {
    if (!colors || colors.trim() == "") {
        return colors
    }
    
    try {
        // Parse original colors array
        def parts = colors.split(",")
        def originalColors = []
        for (int i = 0; i < parts.size(); i += 3) {
            if (i + 2 < parts.size()) {
                originalColors.add([
                    r: parts[i].trim().toInteger(),
                    g: parts[i + 1].trim().toInteger(),
                    b: parts[i + 2].trim().toInteger()
                ])
            }
        }
        
        // Initialize new colors array: all LEDs OFF (use maxLeds for full zone size)
        def newColors = []
        for (int i = 0; i < maxLeds; i++) {
            newColors.add([r: 0, g: 0, b: 0])
        }
        
        // Parse spotlight LED indices
        // maxLeds is passed as parameter (actual LED count in zone)
        def ledIndices = []
        if (spotlightLights && spotlightLights.trim() != "") {
            def normalized = normalizeLedIndices(spotlightLights, maxLeds)
            if (normalized) {
                normalized.split(",").each { part ->
                    try {
                        ledIndices.add(part.trim().toInteger())
                    } catch (NumberFormatException e) {
                        // Skip invalid entries (already normalized)
                    }
                }
            }
        }
        
        // Set specified LEDs to ON using RGB values from original plan
        ledIndices.each { ledIndex ->
            // Convert 1-based index to 0-based array index
            def arrayIndex = ledIndex - 1
            if (arrayIndex >= 0 && arrayIndex < newColors.size()) {
                // Use RGB values from original plan if available, otherwise use first color or default
                if (arrayIndex < originalColors.size()) {
                    // LED index is within original colors array - use that color
                    newColors[arrayIndex] = originalColors[arrayIndex]
                } else if (originalColors.size() > 0) {
                    // LED index is beyond original colors array - use first color from original
                    newColors[arrayIndex] = originalColors[0]
                }
                // If originalColors is empty, LED remains OFF (already initialized)
            }
        }
        
        // Rebuild colors string
        def colorStrings = []
        newColors.each { color ->
            colorStrings.add("${color.r},${color.g},${color.b}")
        }
        
        def result = colorStrings.join(",")
        
        // Validate the result (use maxLeds for validation since that's the actual array size)
        if (!validateColorsString(result, maxLeds)) {
            log.error "Invalid colors string generated: expected ${maxLeds} RGB triplets, got ${colorStrings.size()}"
            log.error "Generated colors string (full): ${result}"
            // Return original colors if validation fails
            return colors
        }
        
        return result
    } catch (Exception e) {
        log.error "Error modifying spotlight plan: ${e.message}"
        return colors  // Return original on error
    }
}

// Validate complete pattern URL string
def validatePatternUrl(String url, Map urlParams) {
    if (!url || url.trim() == "") {
        return false
    }
    
    try {
        // Check URL format
        if (!url.startsWith("http://") || !url.contains("/setPattern?")) {
            log.warn "Pattern URL validation failed: Invalid URL format"
            return false
        }
        
        // Validate required parameters exist
        def requiredParams = ["patternType", "zones", "num_zones", "num_colors", "colors", "direction", "speed", "gap", "other", "pause"]
        requiredParams.each { param ->
            if (!urlParams || !urlParams.containsKey(param)) {
                log.warn "Pattern URL validation failed: Missing required parameter '${param}'"
                return false
            }
        }
        
        // Validate colors string
        def numColors = urlParams.num_colors ? urlParams.num_colors.toInteger() : 0
        if (numColors > 0 && urlParams.colors) {
            if (!validateColorsString(urlParams.colors, numColors)) {
                log.warn "Pattern URL validation failed: Colors string validation failed"
                return false
            }
        }
        
        // Validate numeric parameters are in valid ranges
        def zones = urlParams.zones ? urlParams.zones.toInteger() : 0
        if (zones < 1 || zones > 6) {
            log.warn "Pattern URL validation failed: Invalid zones value: ${zones}"
            return false
        }
        
        def speed = urlParams.speed ? urlParams.speed.toInteger() : 0
        if (speed < 0 || speed > 255) {
            log.warn "Pattern URL validation failed: Invalid speed value: ${speed}"
            return false
        }
        
        debugLog "Pattern URL validation passed: all parameters valid"
        return true
    } catch (Exception e) {
        log.error "Pattern URL validation error: ${e.message}"
        return false
    }
}

// Normalize URL for comparison (remove IP, sort params, etc.)
def normalizeUrlForComparison(String url) {
    if (!url) return ""
    try {
        // Extract just the query string part
        def queryPart = url.contains("?") ? url.substring(url.indexOf("?") + 1) : url
        // Parse and rebuild with sorted params for consistent comparison
        def params = parseUrlParams(url)
        if (!params) return queryPart
        
        // Rebuild with sorted keys for comparison
        def sortedKeys = params.keySet().sort()
        def normalized = sortedKeys.collect { k -> "${k}=${params[k]}" }.join("&")
        return normalized
    } catch (Exception e) {
        debugLog "Error normalizing URL for comparison: ${e.message}"
        return url
    }
}

// Parse URL parameters from a URL string
def parseUrlParams(String url) {
    try {
        def params = [:]
        def queryPart = url.contains("?") ? url.substring(url.indexOf("?") + 1) : url
        def pairs = queryPart.split("&")
        pairs.each { pair ->
            def parts = pair.split("=", 2)
            if (parts.size() == 2) {
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

// Validate colors string format and content
def validateColorsString(String colors, int expectedTriplets) {
    if (!colors || colors.trim() == "") {
        return false
    }
    
    try {
        def parts = colors.split(",")
        def tripletCount = parts.size() / 3
        
        // Check we have the right number of RGB triplets
        if (tripletCount != expectedTriplets) {
            log.warn "Colors string validation failed: expected ${expectedTriplets} triplets, got ${tripletCount} (${parts.size()} values)"
            return false
        }
        
        // Validate all RGB values are in valid range (0-255)
        for (int i = 0; i < parts.size(); i += 3) {
            if (i + 2 < parts.size()) {
                try {
                    def r = parts[i].trim().toInteger()
                    def g = parts[i + 1].trim().toInteger()
                    def b = parts[i + 2].trim().toInteger()
                    
                    if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
                        log.warn "Colors string validation failed: RGB values out of range at triplet ${i/3 + 1}: R=${r}, G=${g}, B=${b}"
                        return false
                    }
                } catch (NumberFormatException e) {
                    log.warn "Colors string validation failed: Invalid number format at position ${i}: ${e.message}"
                    return false
                }
            }
        }
        
        debugLog "Colors string validation passed: ${tripletCount} RGB triplets, all values in valid range"
        return true
    } catch (Exception e) {
        log.error "Colors string validation error: ${e.message}"
        return false
    }
}

// Find next empty slot in custom patterns list (supports up to 200 patterns)
def findNextEmptyPatternSlot(List patterns) {
    for (int i = 0; i < 200; i++) {
        if (i >= patterns.size() || patterns[i] == null) {
            return i + 1  // Return 1-based slot number
        }
    }
    return -1  // No empty slots
}

// Migrate old settings-based custom patterns to state-based storage (one-time)
def migrateOldPatterns() {
    // Check if migration is needed (old patterns in settings, none in state)
    if (state.patterns && state.patterns.size() > 0) {
        return  // Already migrated
    }
    
    def patterns = []
    def migrated = false
    
    // Check for old settings-based patterns
    for (int i = 1; i <= 20; i++) {
        def name = settings."pattern${i}Name"
        if (name && name.trim()) {
            // Migrate old pattern - use pattern name as patternType (minimal params)
            patterns.add([
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
        state.patterns = patterns
        updateAvailablePatternsAttribute()
        log.info "Migrated ${patterns.size()} patterns from settings to state"
    }
}

// Load predefined patterns from PATTERNS map into state.patterns
def doLoadPredefinedPatterns() {
    // Check if already loaded (check for a marker in state)
    if (state.predefinedPatternsLoaded) {
        debugLog "Predefined patterns already loaded, skipping"
        return
    }
    
    def patterns = state.patterns ?: []
    def loadedCount = 0
    
    PATTERNS.each { patternName, patternUrl ->
        // Check if pattern already exists (by name)
        def existingPattern = patterns.find { it && it.name == patternName }
        if (existingPattern) {
            debugLog "Pattern '${patternName}' already exists, skipping"
            return
        }
        
        // Parse pattern URL string to extract parameters
        // Format: "setPattern?patternType=march&zones={zone}&num_zones=1&num_colors=6&colors=...&direction=R&speed=1&gap=0&other=0&pause=0"
        def urlParams = parseUrlParams(patternUrl.replace("{zone}", zoneNumber.toString()))
        
        if (!urlParams || urlParams.isEmpty()) {
            log.warn "Could not parse pattern URL for '${patternName}', skipping"
            return
        }
        
        // Determine plan type
        def patternType = urlParams.patternType ?: "unknown"
        def planType = identifyPlanType(patternType)
        
        // Generate stable pattern ID
        def patternId = generatePatternId(patternName, urlParams)
        
        // Find next empty slot
        def nextSlot = findNextEmptyPatternSlot(patterns)
        if (nextSlot < 0) {
            log.warn "No empty slots available for predefined pattern '${patternName}' (max 200 patterns)"
            return
        }
        
        // Extend patterns list if needed
        if (patterns.size() < nextSlot) {
            while (patterns.size() < nextSlot) {
                patterns.add(null)
            }
        }
        
        // Create pattern object
        def patternObj = [
            id: patternId,
            name: patternName,
            urlParams: urlParams,
            planType: planType
        ]
        
        // Store pattern
        patterns[nextSlot - 1] = patternObj
        loadedCount++
        debugLog "Loaded predefined pattern '${patternName}' (ID: ${patternId}, planType: ${planType})"
    }
    
    if (loadedCount > 0) {
        state.patterns = patterns
        state.predefinedPatternsLoaded = true
        updateAvailablePatternsAttribute()
        log.info "Loaded ${loadedCount} predefined patterns from PATTERNS map"
    } else {
        state.predefinedPatternsLoaded = true  // Mark as loaded even if none were added
        debugLog "No new predefined patterns to load (all already exist or no slots available)"
    }
}

// Generate stable pattern ID from pattern name and URL parameters
def generatePatternId(String patternName, Map urlParams) {
    // Use pattern name as base, but make it URL-safe
    def baseId = patternName.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase()
    
    // Add key parameters to make it unique
    def suffixParts = []
    if (urlParams.direction && urlParams.direction != "0" && urlParams.direction != "F") {
        suffixParts.add("dir${urlParams.direction}")
    }
    if (urlParams.speed && urlParams.speed != "0") {
        suffixParts.add("spd${urlParams.speed}")
    }
    if (urlParams.num_colors && urlParams.num_colors.toInteger() > 1) {
        suffixParts.add("${urlParams.num_colors}colors")
    }
    
    if (suffixParts.isEmpty()) {
        return baseId
    } else {
        return "${baseId}_${suffixParts.join('_')}"
    }
}

def getEffectList() {
    // Return sorted list for Simple Automation Rules dropdown
    return buildEffectList()
}

// Build effect list including patterns only
def buildEffectList() {
    def patternList = []
    
    // Add patterns from state (stored patterns)
    def patterns = state.patterns ?: []
    patterns.each { pattern ->
        if (pattern && pattern.name) {
            patternList.add(pattern.name)
        }
    }
    
    // Return sorted patterns
    return patternList.sort()
}

// Build pattern options for enum dropdown (patterns from state, includes planType)
def getPatternOptions() {
    def options = [:]
    
    // Add empty option first
    options[""] = "-- Select Pattern --"
    
    // Patterns from state (stored patterns)
    def patterns = []
    try {
        def storedPatterns = state.patterns ?: []
        storedPatterns.each { pattern ->
            if (pattern && pattern.name) {
                // Ensure planType exists (lazy evaluation)
                def plan = ensurePlanType(pattern)
                def planTypeDisplay = plan.planType ? " [${plan.planType}]" : ""
                patterns.add([
                    name: pattern.name,
                    display: "${pattern.name}${planTypeDisplay}",
                    planType: plan.planType ?: "non-spotlight"
                ])
            }
        }
    } catch (Exception e) {
        // state not available during metadata parsing - that's OK
    }
    
    // Add patterns (sorted by name, but display includes planType)
    patterns.sort { it.name }.each { pattern ->
        options[pattern.name] = pattern.display  // Key is name, value is display with planType
    }
    
    return options
}

// Build pattern options for command parameters (no empty option)
def getPatternOptionsForCommand() {
    def options = [:]
    
    // Patterns from state (stored patterns)
    def patterns = []
    try {
        def storedPatterns = state.patterns ?: []
        storedPatterns.each { pattern ->
            if (pattern && pattern.name) {
                patterns.add(pattern.name)
            }
        }
    } catch (Exception e) {
        // state not available during metadata parsing - that's OK
    }
    
    // Add patterns (sorted) - no empty option for commands
    patterns.sort().each { pattern ->
        options[pattern] = pattern
    }
    
    return options
}

// Update availablePatterns attribute with current pattern list (includes planType)
def updateAvailablePatternsAttribute() {
    def patterns = state.patterns ?: []
    def patternNames = []
    patterns.each { pattern ->
        if (pattern && pattern.name) {
            // Ensure planType exists (lazy evaluation)
            def plan = ensurePlanType(pattern)
            def planTypeDisplay = plan.planType ? " [${plan.planType}]" : ""
            patternNames.add("${pattern.name}${planTypeDisplay}")
        }
    }
    def patternsList = patternNames.sort().join(", ")
    sendEvent(name: "availablePatterns", value: patternsList ?: "No patterns available")
}

// Get pattern URL - handles patterns from state
def getPatternUrl(String effectName) {
    debugLog "getPatternUrl: Looking for effect '${effectName}'"
    
    // Check patterns from state
    def patterns = state.patterns ?: []
    def pattern = patterns.find { it && it.name == effectName }
    if (pattern && pattern.urlParams) {
        debugLog "getPatternUrl: Found pattern '${effectName}'"
        
        // Lazy evaluation: ensure planType exists
        pattern = ensurePlanType(pattern)
        
        // Use stored URL parameters to build command URL
        def urlParams = pattern.urlParams.clone()
        urlParams.zones = zoneNumber  // Ensure zone number is current
        
        // Handle spotlight plan modification on use
        if (pattern.planType == "spotlight") {
            def numColors = urlParams.num_colors ? urlParams.num_colors.toInteger() : 1
            // Use original colors if available, otherwise use current colors
            def colors = pattern.originalColors ?: urlParams.colors ?: ""
            
            // Use default spotlight plan lights constant
            def defaultSpotlightLights = DEFAULT_SPOTLIGHT_PLAN_LIGHTS
            
            // Use setting if available, otherwise use default
            def spotlightLights = settings.spotlightPlanLights
            if (!spotlightLights || spotlightLights.trim() == "") {
                spotlightLights = defaultSpotlightLights
            }
            
            // Use preference if set, otherwise use constant for normalization
            // numColors is RGB triplets, not total LEDs
            def maxLeds = settings.maxLeds ?: MAX_LEDS
            debugLog "Using maxLeds=${maxLeds} for normalization in getPatternUrl (num_colors=${numColors} is RGB triplets)"
            
            // Normalize and modify
            def normalized = normalizeLedIndices(spotlightLights, maxLeds.toInteger())
            if (normalized && normalized.trim() != "") {
                def modifiedColors = modifySpotlightPlan(colors, normalized, numColors, maxLeds.toInteger())
                urlParams.colors = modifiedColors
                // Update num_colors to match the new array size
                urlParams.num_colors = maxLeds.toString()
                debugLog "Modified spotlight plan on use with LED indices: ${normalized} (created ${maxLeds} RGB triplets)"
            }
        }
        
        return buildCommandUrl(urlParams)
    }
    
    log.warn "getPatternUrl: Pattern '${effectName}' not found in patterns"
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
                        debugLog "Could not parse error response: ${e.message}"
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
    debugLog "HTTP request details: IP=${controllerIP}, timeout=${commandTimeout ?: 10}s"
    
    try {
        httpGet([
            uri: url,
            timeout: (commandTimeout ?: 10) * 1000,
            requestContentType: "application/json"
        ]) { response ->
            debugLog "HTTP response received: status=${response.status}"
            if (response.status == 200) {
                def zones = response.data
                
                // Debug: Log raw response data
                debugLog "=== REFRESH/POLL RESPONSE DEBUG ==="
                debugLog "Response status: ${response.status}"
                // Note: Cannot use getClass() in Hubitat sandbox - use instanceof checks instead
                debugLog "Response data (raw): ${zones?.toString()}"
                
                // If zones is already a List, use it directly
                if (zones instanceof List) {
                    debugLog "Response is already a List with ${zones.size()} zones"
                    debugLog "Full zones array: ${zones}"
                    
                    def zoneData = zones.find { 
                        def zoneNum = it.num
                        zoneNum == zoneNumber || zoneNum.toString() == zoneNumber.toString()
                    }
                    
                    if (zoneData) {
                        debugLog "Found zone ${zoneNumber} data: ${zoneData}"
                        debugLog "Zone data keys: ${zoneData.keySet()}"
                        debugLog "Zone pattern field: '${zoneData.pattern}'"
                        debugLog "Zone patternType field: '${zoneData.patternType}'"
                        debugLog "Zone isOn field: ${zoneData.isOn}"
                        debugLog "Zone enabled field: ${zoneData.enabled}"
                        debugLog "Zone name field: '${zoneData.name}'"
                        debugLog "=== END RESPONSE DEBUG ==="
                    } else {
                        debugLog "Zone ${zoneNumber} not found in response"
                        debugLog "Available zones: ${zones.collect { it.num }}"
                        debugLog "=== END RESPONSE DEBUG ==="
                    }
                    
                    if (callback) callback(zoneData)
                    return
                }
                
                // If it's a String, parse it as JSON
                if (zones instanceof String) {
                    debugLog "Response is a String, parsing JSON..."
                    debugLog "String content: ${zones}"
                    try {
                        zones = new groovy.json.JsonSlurper().parseText(zones)
                        debugLog "Successfully parsed JSON string to List"
                        
                        if (zones instanceof List) {
                            debugLog "Parsed List with ${zones.size()} zones: ${zones}"
                            def zoneData = zones.find { 
                                def zoneNum = it.num
                                zoneNum == zoneNumber || zoneNum.toString() == zoneNumber.toString()
                            }
                            
                            if (zoneData) {
                                debugLog "Found zone ${zoneNumber} data: ${zoneData}"
                                debugLog "Zone data keys: ${zoneData.keySet()}"
                                debugLog "Zone pattern field: '${zoneData.pattern}'"
                                debugLog "Zone patternType field: '${zoneData.patternType}'"
                                debugLog "Zone isOn field: ${zoneData.isOn}"
                                debugLog "=== END RESPONSE DEBUG ==="
                } else {
                                debugLog "Zone ${zoneNumber} not found in parsed response"
                                debugLog "=== END RESPONSE DEBUG ==="
                }
                            
                            if (callback) callback(zoneData)
            } else {
                            log.error "Parsed JSON string but result is not a List: ${zones}"
                            debugLog "=== END RESPONSE DEBUG ==="
                            if (callback) callback(null)
                        }
                    } catch (Exception e) {
                        def zonesStr = zones?.toString() ?: ""
                        def preview = zonesStr.length() > 200 ? zonesStr.substring(0, 200) : zonesStr
                        log.error "Failed to parse JSON string: ${e.message}. First 200 chars: ${preview}"
                        debugLog "=== END RESPONSE DEBUG ==="
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
                def zonesStr = zones?.toString() ?: "null"
                def preview = zonesStr.length() > 200 ? zonesStr.substring(0, 200) : zonesStr
                log.error "Unexpected response.data type. Value: ${preview}"
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
                        debugLog "Could not parse error response: ${e.message}"
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
        debugLog "updateZoneState called with null zoneData"
        return
    }
    
    debugLog "=== UPDATE ZONE STATE DEBUG ==="
    debugLog "Raw zoneData: ${zoneData}"
    debugLog "zoneData keys: ${zoneData.keySet()}"
    debugLog "zoneData.pattern: '${zoneData.pattern}'"
    debugLog "zoneData.patternType: '${zoneData.patternType}'"
    debugLog "zoneData.isOn: ${zoneData.isOn}"
    
    // Extract pattern - handle different possible field names
    def pattern = zoneData.pattern ?: zoneData.patternType ?: "off"
    // If pattern is 'off', force isOn to false regardless of controller's isOn field
    // Otherwise, use isOn field from controller if available, or infer from pattern
    def isOn = false
    if (pattern == "off" || pattern == null || pattern.toString().trim() == "") {
        isOn = false
    } else {
        isOn = zoneData.isOn != null ? zoneData.isOn : true
    }
    
    debugLog "Extracted pattern: '${pattern}'"
    debugLog "zoneData.isOn field: ${zoneData.isOn}"
    debugLog "Calculated isOn: ${isOn}"
    debugLog "Pattern comparison: pattern='${pattern}', pattern == 'off' = ${pattern == 'off'}, pattern != null = ${pattern != null}, pattern.trim() != '' = ${pattern?.toString()?.trim() != ''}"
    
    logDebug "Updating zone state - pattern: '${pattern}', isOn: ${isOn}"
    
    // Always update current pattern (raw pattern string from controller)
    sendEvent(name: "currentPattern", value: pattern.toString())
    
    // Update switch state
    sendEvent(name: "switch", value: isOn ? "on" : "off")
    
    // Try to match pattern to effect name (for saved patterns)
    def effectName = null
    if (isOn && pattern != "off" && pattern != "custom") {
        effectName = findEffectName(pattern.toString())
        debugLog "Effect name lookup for pattern '${pattern}': ${effectName ?: 'not found'}"
    }
    
    // Always set effectName (null if not found or off)
    sendEvent(name: "effectName", value: effectName ?: "")
    
    debugLog "=== END UPDATE ZONE STATE DEBUG ==="
}

def findEffectName(String patternType) {
    // Try to find matching custom pattern by patternType
    def patterns = state.patterns ?: []
    def match = patterns.find { it && it.urlParams && it.urlParams.patternType == patternType }
    return match ? match.name : null
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
    if (settings.logEnable) {
        debugLog "[Zone ${zoneNumber}] ${msg}"
    }
}

// Simple debug logging wrapper that checks logEnable preference
def debugLog(String msg) {
    if (settings.logEnable) {
        log.debug msg
    }
}



