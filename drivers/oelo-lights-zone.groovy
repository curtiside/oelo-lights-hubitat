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
 * Hubitat Groovy Sandbox Restrictions:
 * The following functions/methods are NOT available in Hubitat's sandboxed Groovy environment:
 * 
 * - getClass() - Cannot use .getClass() or ?.getClass() on any object
 *   Error: "Expression [MethodCallExpression] is not allowed"
 *   Workaround: Use instanceof checks instead of getClass().name
 * 
 * - response.json - HttpResponseDecorator does not have a 'json' property
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
 * @author Curtis Ide
 * @version 0.6.14
 */

metadata {
    definition(name: "Oelo Lights Zone", namespace: "pizzaman383", author: "Curtis Ide", importUrl: "") {
        capability "Refresh"
        
        // Custom attributes
        attribute "zone", "number"
        attribute "controllerIP", "string"
        attribute "lastCommand", "string"
        attribute "effectList", "string"
        attribute "verificationStatus", "string"
        attribute "discoveredPatterns", "string"
        attribute "driverVersion", "string"
        attribute "switch", "string"
        
        // Custom commands
        command "setSelectedPattern"
        command "off"
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
    def driverVersion = "0.6.14"
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

def getEffectList() {
    // Return sorted list for Simple Automation Rules dropdown
    return buildEffectList()
}

// Build effect list including custom patterns first, then predefined, then discovered
def buildEffectList() {
    def customList = []
    def predefinedList = []
    def discoveredList = []
    
    // Add custom patterns FIRST (if names are set) - handle null settings during metadata parsing
    if (settings) {
        for (int i = 1; i <= 6; i++) {
            def name = settings."customPattern${i}Name"
            if (name && name.trim()) {
                customList.add(name.trim())
            }
        }
    }
    
    // Add predefined patterns from PATTERNS map
    if (PATTERNS) {
        predefinedList.addAll(PATTERNS.keySet())
    }
    
    // Add discovered patterns - handle null state during metadata parsing
    if (state && state.discoveredPatterns && state.discoveredPatterns.size() > 0) {
        state.discoveredPatterns.each { pattern ->
            if (pattern && !customList.contains(pattern) && !predefinedList.contains(pattern)) {
                discoveredList.add(pattern)
            }
        }
    }
    
    // Combine: custom first (sorted), then predefined (sorted), then discovered (sorted)
    def result = []
    result.addAll(customList.sort())
    result.addAll(predefinedList.sort())
    result.addAll(discoveredList.sort())
    
    return result
}

// Build pattern options for enum dropdown (custom patterns first, then predefined, then discovered)
def getPatternOptions() {
    def options = [:]
    
    // Add empty option first
    options[""] = "-- Select Pattern --"
    
    // Build list (custom first, then predefined, then discovered)
    def patterns = buildEffectList()
    
    // Add all patterns in order
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
    
    try {
        httpGet([
            uri: url,
            timeout: (commandTimeout ?: 10) * 1000
        ]) { response ->
            if (response.status == 200) {
                def zones = response.data
                
                // If zones is already a List, use it directly
                if (zones instanceof List) {
                    def zoneData = zones.find { 
                        def zoneNum = it.num
                        zoneNum == zoneNumber || zoneNum.toString() == zoneNumber.toString()
                    }
                    if (callback) callback(zoneData)
                    return
                }
                
                // If it's a String, parse it as JSON
                if (zones instanceof String) {
                    try {
                        zones = new groovy.json.JsonSlurper().parseText(zones)
                        logDebug "Successfully parsed JSON string to List"
                        
                        if (zones instanceof List) {
                            def zoneData = zones.find { 
                                def zoneNum = it.num
                                zoneNum == zoneNumber || zoneNum.toString() == zoneNumber.toString()
                            }
                            if (callback) callback(zoneData)
                        } else {
                            log.error "Parsed JSON string but result is not a List"
                            if (callback) callback(null)
                        }
                    } catch (Exception e) {
                        log.error "Failed to parse JSON string: ${e.message}. First 200 chars: ${zones.take(200)}"
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
                log.error "Fetch zone data failed with status: ${response.status}"
                if (callback) callback(null)
            }
        }
    } catch (Exception e) {
        log.error "Fetch zone data failed: ${e.message}"
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
// Imported from patterns.groovy - all predefined patterns from Home Assistant integration

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

