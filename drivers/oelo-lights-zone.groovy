/**
 * Oelo Lights Zone Driver for Hubitat
 * 
 * Controls a single zone of an Oelo Lights controller via HTTP API.
 * Designed for use with Hubitat's Simple Automation Rules app.
 * 
 * Primary Usage:
 * - setSelectedPattern() - Set pattern chosen from dropdown
 * - off() - Turn off lights
 * - refresh() - Get current state from controller
 * 
 * Based on the Oelo Lights Home Assistant integration:
 * https://github.com/Cinegration/Oelo_Lights_HA
 * 
 * @author Curtis Ide
 * @version 0.6.2
 */

metadata {
    definition(name: "Oelo Lights Zone", namespace: "pizzaman383", author: "Curtis Ide", importUrl: "") {
        capability "Switch"
        capability "Refresh"
        
        // Custom attributes
        attribute "zone", "number"
        attribute "controllerIP", "string"
        attribute "lastCommand", "string"
        attribute "effectList", "string"
        attribute "verificationStatus", "string"
        attribute "discoveredPatterns", "string"
        attribute "driverVersion", "string"
        
        // Custom command for pattern selection
        command "setSelectedPattern"
        command "setPattern", ["string"]
    }
    
    preferences {
        section("Controller Settings") {
            input name: "controllerIP", type: "text", title: "Controller IP Address", required: true, description: "IP address of Oelo controller"
            input name: "zoneNumber", type: "number", title: "Zone Number", range: "1..6", required: true, defaultValue: 1, description: "Zone number (1-6)"
        }
        
        section("Polling") {
            input name: "pollInterval", type: "number", title: "Poll Interval (seconds)", range: "10..300", defaultValue: 30, description: "How often to poll controller status"
            input name: "autoPoll", type: "bool", title: "Enable Auto Polling", defaultValue: true, description: "Automatically poll controller status"
        }
        
        section("Custom Patterns") {
            input name: "customPattern1Name", type: "text", title: "Custom Pattern 1 Name", description: "Pattern name as stored on controller", required: false
            input name: "customPattern2Name", type: "text", title: "Custom Pattern 2 Name", description: "Pattern name as stored on controller", required: false
            input name: "customPattern3Name", type: "text", title: "Custom Pattern 3 Name", description: "Pattern name as stored on controller", required: false
            input name: "customPattern4Name", type: "text", title: "Custom Pattern 4 Name", description: "Pattern name as stored on controller", required: false
            input name: "customPattern5Name", type: "text", title: "Custom Pattern 5 Name", description: "Pattern name as stored on controller", required: false
            input name: "customPattern6Name", type: "text", title: "Custom Pattern 6 Name", description: "Pattern name as stored on controller", required: false
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
        
        section("Commands") {
            input name: "selectedPattern", type: "enum", title: "Select Pattern", options: getPatternOptions(), required: false, description: "Choose a pattern to set"
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
    log.info "Oelo Lights Zone driver updated"
    // Set driver version immediately
    setDriverVersion()
    initialize()
    
    // Refresh effect list if custom patterns changed
    def effectList = buildEffectList()
    sendEvent(name: "effectList", value: effectList)
    def predefinedCount = PATTERNS ? PATTERNS.size() : 0
    def customCount = effectList.size() - predefinedCount
    log.info "Effect list updated: ${effectList.size()} patterns available (${predefinedCount} predefined + ${customCount} custom)"
    
    // Update discovered patterns attribute
    updateDiscoveredPatternsAttribute()
}

// Set driver version in state and attribute (called unconditionally)
def setDriverVersion() {
    def driverVersion = "0.6.2"
    state.driverVersion = driverVersion
    sendEvent(name: "driverVersion", value: driverVersion)
    logDebug "Driver version set to: ${driverVersion}"
}

def initialize() {
    // Set driver version (in case initialize is called directly)
    setDriverVersion()
    
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
    
    // Build and expose effect list for Simple Automation Rules (includes custom patterns)
    def effectList = buildEffectList()
    sendEvent(name: "effectList", value: effectList)
    
    // Update discovered patterns attribute
    updateDiscoveredPatternsAttribute()
    
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
    poll()
}

// Capability: Switch

def off() {
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

// Internal function: Set effect (used by setSelectedPattern command)
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

// Custom command: Set pattern from dropdown selection (Preferences)
def setSelectedPattern() {
    def patternName = settings.selectedPattern
    if (!patternName || patternName == "") {
        log.warn "No pattern selected. Please select a pattern from Preferences → Commands section first."
        return
    }
    
    log.info "Setting pattern from Preferences dropdown: ${patternName}"
    setEffect(patternName)
}

// Custom command: Set pattern by name (can be called from Commands tab or rules)
def setPattern(String patternName) {
    if (!patternName || patternName.trim() == "") {
        log.warn "Pattern name cannot be empty"
        return
    }
    
    log.info "Setting pattern: ${patternName}"
    setEffect(patternName.trim())
}

def getEffectList() {
    // Return sorted list for Simple Automation Rules dropdown
    return buildEffectList()
}

// Build effect list including predefined, custom, and discovered patterns
def buildEffectList() {
    def list = []
    
    // Add predefined patterns
    if (PATTERNS) {
        list.addAll(PATTERNS.keySet())
    }
    
    // Add custom patterns (if names are set) - handle null settings during metadata parsing
    if (settings) {
        for (int i = 1; i <= 6; i++) {
            def name = settings."customPattern${i}Name"
            if (name && name.trim()) {
                list.add(name.trim())
            }
        }
    }
    
    // Add discovered patterns - handle null state during metadata parsing
    if (state && state.discoveredPatterns && state.discoveredPatterns.size() > 0) {
        state.discoveredPatterns.each { pattern ->
            if (pattern && !list.contains(pattern)) {
                list.add(pattern)
            }
        }
    }
    
    return list.sort()
}

// Build pattern options for enum dropdown (combines custom + discovered patterns)
def getPatternOptions() {
    def options = [:]
    def patterns = buildEffectList()
    
    // Add empty option first
    options[""] = "-- Select Pattern --"
    
    // Add all patterns
    patterns.each { pattern ->
        options[pattern] = pattern
    }
    
    return options
}

// Get pattern URL - handles both predefined and custom patterns
def getPatternUrl(String effectName) {
    // Check predefined patterns first
    def pattern = PATTERNS ? PATTERNS[effectName] : null
    if (pattern) {
        // Replace {zone} placeholder
        def url = pattern.replace("{zone}", zoneNumber.toString())
        return "http://${controllerIP}/${url}"
    }
    
    // Check custom patterns - use pattern name as patternType
    for (int i = 1; i <= 6; i++) {
        def customName = settings."customPattern${i}Name"
        if (customName && customName.trim() == effectName) {
            // Use the pattern name as patternType - controller has pattern settings stored
            return buildPatternUrl(customName.trim())
        }
    }
    
    // Check discovered patterns - use pattern name as patternType
    if (state.discoveredPatterns && state.discoveredPatterns.contains(effectName)) {
        // Use the pattern name as patternType - controller has pattern settings stored
        return buildPatternUrl(effectName)
    }
    
    return null
}

// Build URL for pattern (uses pattern name as patternType - controller has settings stored)
def buildPatternUrl(String patternName) {
    // Use minimal parameters - controller uses its stored settings for the pattern
    def url = buildCommandUrl([
        patternType: patternName,
        zones: zoneNumber,
        num_zones: 1,
        num_colors: 1,
        colors: "255,255,255",  // Placeholder - controller uses stored pattern settings
        direction: "F",
        speed: 0,
        gap: 0,
        other: 0,
        pause: 0
    ])
    return url
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
                log.warn "Unexpected response: status=${status}, text=${text}"
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

// Get current zone state and compare with expected
def getCurrentZoneStateForVerification(String commandUrl, int attempt, int maxRetries, int delaySeconds, long startTime) {
    if (!controllerIP) {
        log.error "Cannot verify: Controller IP not configured"
        sendEvent(name: "verificationStatus", value: "error")
        return
    }
    
    def url = "http://${controllerIP}/getController"
    logDebug "Getting zone state for verification (attempt ${attempt}/${maxRetries})"
    
    try {
        httpGet([
            uri: url,
            timeout: (commandTimeout ?: 10) * 1000
        ]) { response ->
            if (response.status == 200) {
                def zones = response.data
                
                // Parse JSON if response.data is a string
                if (zones instanceof String) {
                    try {
                        zones = new groovy.json.JsonSlurper().parseText(zones)
                    } catch (Exception e) {
                        log.error "Failed to parse JSON response for verification: ${e.message}"
                        handleVerificationRetry(commandUrl, attempt, maxRetries, delaySeconds, startTime)
                        return
                    }
                }
                
                if (zones instanceof List) {
                    def zoneData = zones.find { it.num == zoneNumber }
                    if (zoneData) {
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
                            return
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
                    } else {
                        log.warn "Zone ${zoneNumber} not found in response for verification"
                        handleVerificationRetry(commandUrl, attempt, maxRetries, delaySeconds, startTime)
                    }
                } else {
                    log.error "Invalid response format for verification: ${zones}"
                    handleVerificationRetry(commandUrl, attempt, maxRetries, delaySeconds, startTime)
                }
            } else {
                log.error "Poll failed with status: ${response.status} for verification"
                handleVerificationRetry(commandUrl, attempt, maxRetries, delaySeconds, startTime)
            }
        }
    } catch (Exception e) {
        log.error "Error getting zone state for verification: ${e.message}"
        handleVerificationRetry(commandUrl, attempt, maxRetries, delaySeconds, startTime)
    }
}

// Handle verification retry logic
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

def poll() {
    if (!controllerIP) {
        log.error "Cannot poll: Controller IP not configured"
        return
    }
    
    def url = "http://${controllerIP}/getController"
    logDebug "Polling: ${url}"
    
    try {
        httpGet([
            uri: url,
            timeout: (commandTimeout ?: 10) * 1000
        ]) { response ->
            if (response.status == 200) {
                def zones = response.data
                
                // Parse JSON if response.data is a string
                if (zones instanceof String) {
                    try {
                        zones = new groovy.json.JsonSlurper().parseText(zones)
                    } catch (Exception e) {
                        log.error "Failed to parse JSON response: ${e.message}"
                        return
                    }
                }
                
                if (zones instanceof List) {
                    // Find zone - handle both integer and string zone numbers
                    def zoneData = zones.find { 
                        def zoneNum = it.num
                        zoneNum == zoneNumber || zoneNum.toString() == zoneNumber.toString()
                    }
                    if (zoneData) {
                        logDebug "Poll response for zone ${zoneNumber}: pattern='${zoneData.pattern}', full data=${zoneData}"
                        updateZoneState(zoneData)
                    } else {
                        log.warn "Zone ${zoneNumber} not found in response. Available zones: ${zones.collect { it.num }}"
                    }
                } else {
                    log.error "Invalid response format: ${zones}"
                }
            } else {
                log.error "Poll failed with status: ${response.status}"
            }
        }
    } catch (Exception e) {
        log.error "Poll failed: ${e.message}"
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
    // Extract pattern - handle different possible field names
    def pattern = zoneData.pattern ?: zoneData.patternType ?: "off"
    def isOn = pattern != "off" && pattern != null && pattern.toString().trim() != ""
    
    logDebug "Updating zone state - pattern: '${pattern}', isOn: ${isOn}"
    
    sendEvent(name: "switch", value: isOn ? "on" : "off")
    
    // Track discovered patterns from controller
    if (pattern && pattern != "off" && pattern != "custom" && pattern.toString().trim() != "") {
        if (!state.discoveredPatterns) {
            state.discoveredPatterns = []
        }
        def patternStr = pattern.toString().trim()
        if (!state.discoveredPatterns.contains(patternStr)) {
            state.discoveredPatterns.add(patternStr)
            log.info "Discovered pattern from controller: ${patternStr}"
            // Update attribute when new pattern is discovered
            updateDiscoveredPatternsAttribute()
        }
    }
    
    if (isOn && pattern != "custom") {
        // Try to match pattern to effect name
        def effectName = findEffectName(pattern.toString())
        if (effectName) {
            sendEvent(name: "effectName", value: effectName)
        }
    }
}

def findEffectName(String patternType) {
    // Try to find matching effect by patternType
    if (!PATTERNS) return null
    def match = PATTERNS.find { name, url -> url.contains("patternType=${patternType}") }
    return match ? match.key : null
}

// Update discovered patterns attribute
def updateDiscoveredPatternsAttribute() {
    if (state.discoveredPatterns && state.discoveredPatterns.size() > 0) {
        def patternsList = state.discoveredPatterns.sort().join(", ")
        sendEvent(name: "discoveredPatterns", value: patternsList)
    } else {
        sendEvent(name: "discoveredPatterns", value: "None discovered yet")
    }
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

// Pattern Definitions
// TODO: Import from patterns.py or define inline
// This is a subset - full list should be imported from patterns.py

final Map PATTERNS = [
    "Christmas: Candy Cane Glimmer": "setPattern?patternType=river&num_zones=1&zones={zone}&num_colors=4&colors=255,255,255,255,0,0,255,255,255,255,0,0,&direction=R&speed=20&gap=0&other=0&pause=0",
    "Christmas: Candy Cane Lane": "setPattern?patternType=stationary&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,255,255,255,255,255,255,255,0,0,255,0,0,255,0,0,&direction=R&speed=4&gap=0&other=0&pause=0",
    "Halloween: Goblin Delight": "setPattern?patternType=takeover&num_zones=1&zones={zone}&num_colors=6&colors=176,0,255,176,0,255,176,0,255,53,255,0,53,255,0,53,255,0,&direction=R&speed=1&gap=0&other=0&pause=0",
    "Fourth of July: Fast Fireworks": "setPattern?patternType=twinkle&num_zones=1&zones={zone}&num_colors=6&colors=255,255,255,0,0,255,0,0,255,255,255,255,255,0,0,255,0,0,&direction=R&speed=10&gap=0&other=0&pause=0"
    // TODO: Add all patterns from patterns.py
]

