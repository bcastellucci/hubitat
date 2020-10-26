import groovy.json.JsonSlurper

/**
 * Powerley Energy Monitor
 * v1.0
 *
 * This driver is based off of the excellent work of Chris Lawson, specifically his
 * 'MQTT Link Driver' driver:
 *
 * 	https://github.com/mydevbox/hubitat-mqtt-link/tree/master/drivers
 * 
 * It is also based on the great sleuthing work found in this thread:
 * 
 * 	https://github.com/home-assistant/core/issues/20170
 * 
 * as well as this thread (which is really a succinct re-cap of the previous thread):
 * 
 * 	https://www.reddit.com/r/homeassistant/comments/j7ykh6/for_any_aep_ohio_customers_with_the_powerley/
 *
 * Many thanks to Chris and to the posters in the threads for all of their hard work.
 *
 */

private static final String mqttTopicIsAppOpen() { return "request/is_app_open" }
private static final String mqttTopicMeteringMiniuteSummation() { return "event/metering/summation/minute" }
private static final String mqttTopicMeteringInstantaneousDemand() { return "event/metering/instantaneous_demand" }

metadata {
	
	definition(
		name: "Powerley Energy Monitor",
		namespace: "bchubitat",
		author: "Ben Castellucci",
		description: "Driver to consume energy usage from the Powerley Energy Bridge & smart meter over MQTT & present to Hubitat as an energy monitor device",
		importUrl: "https://raw.githubusercontent.com/bcastellucci/Hubitat/master/Drivers/Powerley-Energy-Monitor.groovy"
	) {
		
		capability "EnergyMeter"
		capability "PowerMeter"
		
		attribute "energyState", "string"
		attribute "lastEnergyTime", "number"
		attribute "lastEnergyValue", "number"
		attribute "powerState", "string"
		attribute "lastPowerTime", "number"
		attribute "lastPowerValue", "number"
		attribute "connectionState", "string"

		preferences {
			
			input(
				name: "energyBridgeIP",
				type: "string",
				title: "IP Address of the Powerley Energy Bridge",
				description: "e.g. 192.168.1.200",
				required: true,
				displayDuringSetup: true
			)
			
			input "debugEnabled", "bool", title: "Enable debug logging", defaultValue: false
			input "traceEnabled", "bool", title: "Enable trace logging", defaultValue: false
			
		}
		
		command "connect", [[
					name:"connect",
					description:"Connects to the Powerley Energy Bridge on the local LAN, if not already connected"
				]]
		command "disconnect", [[
					name:"disconnect",
					description:"Disconnects from the Powerley Energy Bridge on the local LAN, if currently connected"
				]]
		command "energyStart", [[
					name:"energyStart",
					description:"Starts receiving summation energy reporting (Wmin) from the Powerley Energy Bridge on the local LAN (every minute), then publishes events to Hubitat with the corresponding times and values"
				]]
		command "energyStop", [[
					name:"energyStop",
					description:"Stops energy reporting"
				]]
		command "powerStart", [[
					name:"powerStart",
					description:"Starts receiving instantaneous demand power reporting (WATTS) from the Powerley Energy Bridge on the local LAN (every three seconds), then publishes events to Hubitat with the corresponding times and values"
				]]
		command "powerStop", [[
					name:"powerStop",
					description:"Stops power reporting"
				]]
		
	}
}

// ========================================================
// EXPECTED DEVICE METHODS
// ========================================================

void installed() {
	if (traceEnabled) log.trace "trace entry: installed()"
	if (traceEnabled) log.trace "trace exit: installed()"
}

void updated() {
	if (traceEnabled) log.trace "trace entry: updated()"
	try {
		def lastEnergyState = device.currentValue("energyState")
		def lastPowerState = device.currentValue("powerState")
		disconnect()
		connect()
		if (lastEnergyState == 'started') energyStart()
		if (lastPowerState == 'started') powerStart()
	} finally {
		if (traceEnabled) log.trace "trace exit: updated()"
	}
}

void uninstalled() {
	if (traceEnabled) log.trace "trace entry: uninstalled()"
	try {
		disconnect()
		unschedule()
	} finally {
		if (traceEnabled) log.trace "trace exit: uninstalled()"
	}
}

// ========================================================
// COMMANDS
// ========================================================

void connect() {
	if (traceEnabled) log.trace "trace entry: connect()"
	try {
		if (!interfaces.mqtt.isConnected()) {
			
			// connect to the mqtt broker on the Powerley Energy Bridge
			interfaces.mqtt.connect(
				"tcp://${settings?.energyBridgeIP}:2883",
				"hubitat_${getHubId()}",
				null, null
			)
			
			log.info("connect() - Connected to Powerley Energy Bridge")
			
			sendEvent (name: "connectionState", value: "connected", isStateChange: true)
			
			schedule("0 */5 * ? * *", "testConnected")
			
		} else {
			if (debugEnabled) log.debug "connect() - already connected"
		}
	} catch(e) {
		log.error("connect() - ${e}")
	} finally {
		if (traceEnabled) log.trace "trace exit: connect()"
	}
}

void disconnect() {
	if (traceEnabled) log.trace "trace entry: disconnect()"
	try {
		if (interfaces.mqtt.isConnected()) {
			
			// unsubscribe from topics
			energyStop()
			powerStop()
			
			interfaces.mqtt.disconnect()
			
			log.info("disconnect() - Disconnected from Powerley Energy Bridge")
			
			sendEvent (name: "connectionState", value: "disconnected", isStateChange: true)
			
			unschedule("testConnected")
			
		} else {
			if (debugEnabled) log.debug "disconnect() - not connected"
		}
	} catch(e) {
		log.error("disconnect() - ${e}")
	} finally {
		if (traceEnabled) log.trace "trace exit: dicsonnect()"
	}
}

void energyStart() {
	if (traceEnabled) log.trace "trace entry: energyStart)"
	try {
		if (interfaces.mqtt.isConnected()) {
			
			// subscribe to the minute summation metering topic
			interfaces.mqtt.subscribe(mqttTopicMeteringMiniuteSummation())
			
			log.info("energyStart() - Subscribed to ${mqttTopicMeteringMiniuteSummation()}")
			
			sendEvent (name: "energyState", value: "started", isStateChange: true)
			
		} else {
			if (debugEnabled) log.debug "energyStart() - not connected, skipping"
		}
	} catch(Exception e) {
		log.error("energyStart() - ${e}")
	} finally {
		if (traceEnabled) log.trace "trace exit: energyStart()"
	}
}

void energyStop() {
	if (traceEnabled) log.trace "trace entry: energyStop)"
	try {
		if (interfaces.mqtt.isConnected()) {
			
			// un-subscribe from the minute summation event metering topic
			interfaces.mqtt.unsubscribe(mqttTopicMeteringMiniuteSummation())
			
			log.info("energyStop() - Un-subscribed from ${mqttTopicMeteringMiniuteSummation()}")
			
			sendEvent (name: "energyState", value: "stopped", isStateChange: true)
			
		} else {
			if (debugEnabled) log.debug "energyStop() - not connected, skipping"
		}
	} catch(Exception e) {
		log.error("energyStop() - ${e}")
	} finally {
		if (traceEnabled) log.trace "trace exit: energyStop()"
	}
}

void powerStart() {
	if (traceEnabled) log.trace "trace entry: powerStart()"
	try {
		if (interfaces.mqtt.isConnected()) {
			
			// publish to is_app_open
			interfaces.mqtt.publish(mqttTopicIsAppOpen(), '{"requestId": "ha-monitor"}', 0, false)
			
			if (debugEnabled) log.debug "powerStart() - Published to ${mqttTopicIsAppOpen()}"
			
			// subscribe to the instantaneous demand event metering topic
			interfaces.mqtt.subscribe(mqttTopicMeteringInstantaneousDemand())
			
			log.info("powerStart() - Subscribed to ${mqttTopicMeteringInstantaneousDemand()}")
			
			sendEvent (name: "powerState", value: "started", isStateChange: true)
			
		} else {
			if (debugEnabled) log.debug "powerStart() - not connected, skipping"
		}
	} catch(Exception e) {
		log.error("powerStart() - ${e}")
	} finally {
		if (traceEnabled) log.trace "trace exit: powerStart()"
	}
}

void powerStop() {
	if (traceEnabled) log.trace "trace entry: powerStop)"
	try {
		if (interfaces.mqtt.isConnected()) {
			
			// un-subscribe from the instantaneous demand event metering topic
			interfaces.mqtt.unsubscribe(mqttTopicMeteringInstantaneousDemand())
			
			log.info("powerStop() - Un-subscribed from ${mqttTopicMeteringInstantaneousDemand()}")
			
			sendEvent (name: "powerState", value: "stopped", isStateChange: true)
			
		} else {
			if (debugEnabled) log.debug "powerStop() - not connected, skipping"
		}
	} catch(Exception e) {
		log.error("powerStop() - ${e}")
	} finally {
		if (traceEnabled) log.trace "trace exit: powerStop()"
	}
}

// ========================================================
// MQTT METHODS
// ========================================================

// Parse incoming message from the MQTT broker
def parse(String event) {
	if (traceEnabled) log.trace "trace entry: parse()"
	try {
		
		def message = interfaces.mqtt.parseMessage(event)
		if (debugEnabled) log.debug("parse() - Received MQTT message: ${message}")
			
		def parsedMessage = new JsonSlurper().parseText("${message.payload}")
		if (traceEnabled) log.trace("parse() - parsed message: ${parsedMessage}")
		
		switch (message.topic) {
			case mqttTopicMeteringInstantaneousDemand():
				if (traceEnabled) log.trace "parse() - received instantaneous demand message with payload ${message.payload} (${new Date(Long.valueOf(parsedMessage.time)).toString()})"
				sendEvent (name: "power", value: parsedMessage.demand, isStateChange: true)
				sendEvent (name: "lastPowerTime", value: parsedMessage.time, isStateChange: true)
				sendEvent (name: "lastPowerValue", value: parsedMessage.demand, isStateChange: true)
				break;
			case mqttTopicMeteringMiniuteSummation():
				if (traceEnabled) log.trace "parse() - received minute-level summation message with payload ${message.payload} (${new Date(Long.valueOf(parsedMessage.time)).toString()})"
				sendEvent (name: "energy", value: parsedMessage.value, isStateChange: true)
				sendEvent (name: "lastEnergyTime", value: parsedMessage.time, isStateChange: true)
				sendEvent (name: "lastEnergyValue", value: parsedMessage.value, isStateChange: true)
				if (
					device.currentValue("lastPowerTime") + 10000 < parsedMessage.time
					&& device.currentValue("powerState") != "stopped"
				) {
					if (debugEnabled) log.debug "parse() - instantaneous demand seems to have stopped"
					sendEvent (name: "powerState", value: "stopped", isStateChange: true)
				}
				break;
			default:
				log.warn "parse() - received unknown message with payload ${message.payload}"
				break;
		}
		
	} finally {
		if (traceEnabled) log.trace "trace exit: parse()"
	}
}

def mqttClientStatus(status) {
	if (traceEnabled) log.trace "trace entry: mqttClientStatus()"
	try {
		if (debugEnabled) log.debug "mqttClientStatus() - status: ${status}"
	} finally {
		if (traceEnabled) log.trace "trace exit: mqttClientStatus()"
	}
}

// ========================================================
// HELPERS
// ========================================================

def getHubId() {
	if (traceEnabled) log.trace "trace entry: getHubId()"
	try {
		def hub = location.hubs[0]
		def hubNameNormalized = "${hub.name}-${hub.hardwareID}".replaceAll("[^a-zA-Z0-9]+","-").toLowerCase()
		if (debugEnabled) log.debug "getHubId() - hub name (normalized) - ${hubNameNormalized}"
		return hubNameNormalized
	} finally {
		if (traceEnabled) log.trace "trace exit: getHubId()"
	}
}

def testConnected() {
	if (traceEnabled) log.trace "trace entry: testConnected()"
	try {
		
		if (!interfaces.mqtt.isConnected()) {
			
			log.info("testConnected() - Not connected to Powerley Energy Bridge")
			
			if (device.currentValue("energyState") != 'stopped') {
				sendEvent (name: "energyState", value: "stopped", isStateChange: true)
			}
			
			if (device.currentValue("powerState") != 'stopped') {
				sendEvent (name: "powerState", value: "stopped", isStateChange: true)
			}
			
			if (device.currentValue("connectionState") != 'disconnected') {
				sendEvent (name: "connectionState", value: "disconnected", isStateChange: true)
			}
			
		} else if (debugEnabled) {
			log.debug "testConnected() - Still connected to Powerley Energy Bridge"
		}
		
	} finally {
		if (traceEnabled) log.trace "trace exit: testConnected()"
	}
}
