import groovy.transform.Field

/**
 * Powerley Thermostat
 * SKU: PWLY-7828-A1
 * ZWave Plus
 * v1.0
 * 
 * Since there is no Hubitat-provided example thermostat driver to base anything off of
 * and their documentation is sorely lacking, we turn to community-developed drivers as
 * a basis.
 * 
 * This driver is based off of the excellent work of Bryan Copeland, specifically his
 * 'Advanced Honeywell T6 Pro' thermostat driver:
 * 
 * 	https://github.com/djdizzyd/hubitat/tree/master/Drivers/Honeywell
 * 
 * Many thanks to Brian for all of his hard work.
 * 
 */

metadata {
	definition (
		name: "Powerley Thermostat",
		namespace: "bchubitat",
		author: "Ben Castellucci",
		description: "Driver for the Powerley Thermostat",
		importUrl: "https://raw.githubusercontent.com/bcastellucci/Hubitat/master/Drivers/Powerley-Thermostat.groovy"
	) {

		capability "Actuator"
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "Sensor"
		capability "TemperatureMeasurement"
		capability "Thermostat"
		capability "ThermostatMode"
		capability "ThermostatFanMode"
		capability "ThermostatSetpoint"
		capability "ThermostatCoolingSetpoint"
		capability "ThermostatHeatingSetpoint"
		capability "ThermostatOperatingState"
		capability "RelativeHumidityMeasurement"

		//
		//these two are unique to this thermostat and are probably not of much use to anyone
		//but it may be fun to turn the indicators on & off!
		//
		attribute "currEcoMode", "number"
		attribute "currDemandResponseMode", "number"

		command "ECOMode", [[
					name:"ecoMode",
					type:"ENUM",
					description:"Controls the LED indicator light that signals ECO mode operation",
					constraints:["0","1"]
				]]
		command "DemandResponseMode", [[
					name:"demandResponseMode",
					type:"ENUM",
					description:"Controls the LED indicator light that signals a utility company Demand Response Event",
					constraints:["0","1"]
				]]
		
		fingerprint	mfr:"028C", //Powerley
					prod:"A55A", //(their only thermostat model)
					deviceId:"0001", //(their only thermostat model)
					inClusters:"0x5E,0x85,0x59,0x5A,0x73,0x80,0x70,0x72,0x31,0x44,0x45,0x40,0x42,0x43,0x86",
					deviceJoinName: "Powerley Thermostat (PWLY-7828-A1)"
	}
	preferences {
		//run through all the commands & turn them into preferences
		configParams.each {
			//skip 3 & 7 - those are the ECO Mode and Demand-Response Mode indicators,
			//respectively, and we don't need actual preferences for those.
			if (![3,7].contains(it.key)) {
				input it.value.input
			}
		}
		//add the preferences for debug and trace logging
		input "debugEnable", "bool", title: "Enable debug logging", defaultValue: false
		input "traceEnable", "bool", title: "Enable trace logging", defaultValue: false
	}

}

//
//the specific versions we need to declare/use come from:
//
//	https://powerley.com/thermostat/command-classes/
//
//	and
//
//	https://opensmarthouse.org/zwavedatabase/1149/PowerleyThermostat-Assoc-Parameter.pdf
//
@Field static Map CMD_CLASS_VERS=[
	0x85:2,	//Association (version 2)
	0x59:1,	//Association Group Info (version 1)
	0x20:1,	//Basic (version 1)
	0x80:1, //Battery (version 1)
	0x70:1,	//Configuration (version 1)
	0x5A:1,	//Device Reset Locally (version 1)
	0x7A:2,	//Firmware Update Meta Data (version 2)
	0x72:1,	//Manufacturer Specific (version 1)
	0x31:5,	//Multilevel Sensor (version 5)
	0x73:1,	//Power Level (version 1)
	0x44:1,	//Thermostat Fan Mode (version 1)
	0x45:1,	//Thermostat Fan State (version 1)
	0x40:2,	//Thermostat Mode (version 2)
	0x42:2,	//Thermostat Operating State (version 2)
	0x43:1,	//Thermostat Setpoint (version 1)
	0x86:2,	//Version (version 2)
	0x5E:2	//Z-Wave Plus Info (version 2)
]

//
//these values all come from:
//
//	https://docs.hubitat.com/index.php?title=ZWave_Classes#Thermostat_Operating_State_Report
//
//HINT - we are missing a few that this thermostat doesn't support
//
@Field static Map THERMOSTAT_OPERATING_STATEv2=[0x00:"idle",0x01:"heating",0x02:"cooling",0x03:"fan only",0x04:"pending heat",0x05:"pending cool"]
@Field static Map THERMOSTAT_MODEv2=[0x00:"off",0x01:"heat",0x02:"cool"]
@Field static Map SET_THERMOSTAT_MODEv2=["off":0x00,"heat":0x01,"cool":0x02]
@Field static Map THERMOSTAT_FAN_MODEv1=[0x00:"auto",0x01:"on",0x02:"auto",0x03:"on",0x04:"auto",0x05:"on"]
@Field static Map SET_THERMOSTAT_FAN_MODEv1=["auto":0x00,"on":0x01]
@Field static Map THERMOSTAT_FAN_STATEv1=[0x00:"idle", 0x01:"running"]

//
//this thermostat is super simple - it only supports the following modes & fan modes
//
@Field static List<String> supportedThermostatFanModes=["on", "auto"]
@Field static List<String> supportedThermostatModes=["off", "heat", "cool"]

//
//nice idea from Bryan to combine config commands & preferences, then collect them all up into a map, makes things much easier later on.
//map key is the parameter number (to send to the device), the rest is self-explanatory.
//
//names & descriptions were closely matched to those in the Powerley app
//
@Field static Map configParams = [
		1:  [
				input: [
					name: "temperatureUnits",
					type: "enum",
					title: "Temperature Units",
					description: "Temperature units on thermostat",
					defaultValue: 1,
					options: [0:"Celsius", 1:"Fahrenheit"]
				],
				parameterSize: 1
			],
		11: [
				input: [
					name: "alwaysOnDisplay",
					type: "enum",
					title: "Always On Display",
					description: "When enabled, display will remain illuminated (only takes effect if powered by constant 24vac)",
					defaultValue: 1,
					options: [0:"Disabled", 1:"Enabled"]
				],
				parameterSize: 1
			],
		6:  [
				input: [
					name: "displayBrightness",
					type: "enum",
					title: "Display Brightness",
					description: "Display illumination level",
					defaultValue: 0,
					options: [0:"Low", 1:"Medium", 2:"High", 3:"Auto"]
				],
				parameterSize: 1
			],
		2:  [
				input: [
					name: "hvacEquipmentType",
					type: "enum",
					title: "HVAC Equipment Type",
					description: "Type of HVAC system",
					defaultValue: 0,
					options: [
						0: "1 Stage Conventional",
						1: "2 Stage Conventional",
						2: "Heat Pump w/O-Wire",
						3: "Heat Pump w/B-Wire",
						4: "Heat Pump w/O-Wire & aux heating",
						5: "Heat Pump w/B-Wire & aux heating"
					]
				],
				parameterSize: 1
			],
		8:  [
				input: [
					name: "swingRange",
					type: "enum",
					title: "Swing Range",
					description: "Difference (+/-) from setpoint before HVAC kicks on",
					defaultValue: 1,
					options: [
						 0: "+/- 0.0°",
						 1: "+/- 1.0°",
						 2: "+/- 2.0°",
						 3: "+/- 3.0°",
						 4: "+/- 4.0°",
						 5: "+/- 5.0°",
						 6: "+/- 6.0°",
						 7: "+/- 7.0°",
						 8: "+/- 8.0°",
						 9: "+/- 9.0°",
						10: "+/- 10.0°"
					]
				],
				parameterSize:1
			],
		5:  [
				input: [
					name: "temperatureCalibration",
					type: "enum",
					title: "Tempurature Calibration",
					description: "Offset",
					defaultValue: 0,
					options: [
						(-5): "-5.0°",
						(-4): "-4.0°",
						(-3): "-3.0°",
						(-2): "-2.0°",
						(-1): "-1.0°",
						  0 : "0.0°",
						  1 : "+1.0°",
						  2 : "+2.0°",
						  3 : "+3.0°",
						  4 : "+4.0°",
						  5 : "+5.0°"
					]
				],
				parameterSize:1
			],
		9:  [
				input: [
					name: "setpointDifferential",
					type: "enum",
					title: "Setpoint Differential",
					description: "Difference between ambient tempurature and setpoint before 2nd stage or auxiliary heating/cooling kicks on",
					defaultValue: 3,
					options: [
						 0: "+/- 0.0°",
						 1: "+/- 1.0°",
						 2: "+/- 2.0°",
						 3: "+/- 3.0°",
						 4: "+/- 4.0°",
						 5: "+/- 5.0°",
						 6: "+/- 6.0°",
						 7: "+/- 7.0°",
						 8: "+/- 8.0°",
						 9: "+/- 9.0°",
						10: "+/- 10.0°"
					]
				],
				parameterSize:1
			],
		3:  [
				input: [
					name: "ecoMode",
					type: "enum",
					title: "ECO Mode",
					description: "Illuminate (or turn off) the ECO mode indicator on the thermostat",
					defaultValue: 0,
					options: [0:"Off", 1:"On"]
				],
				parameterSize: 1
			],
		7:  [
				input: [
					name: "demandResponseMode",
					type: "enum",
					title: "Demand Response Mode",
					description: "Illuminate (or turn off) the Demand Response mode indicator on the thermostat",
					defaultValue: 0,
					options: [0:"Off", 1:"On"]
				],
				parameterSize: 1
			]
]

// ========================================================
// EXPECTED/DECLARED DEVICE COMMANDS
// ========================================================

//this method is called when the device is installed, via pair or virtual
//	https://community.hubitat.com/t/built-in-driver-methods-behavior/1995
void installed() {
	if (traceEnable) log.trace "trace entry: installed()"
	try {
		if (traceEnable) log.trace "calling initializeVars()..."
		initializeVars()
	} finally {
		if (traceEnable) log.trace "trace exit: installed()"
	}
}

//this method is called after installed() runs, or from the 'Configure' command button on the device page
//	https://community.hubitat.com/t/built-in-driver-methods-behavior/1995
void configure() {
	if (traceEnable) log.trace "trace entry: configure()"
	try {
		if (!state.initialized) {
			if (traceEnable) log.trace "state not initialized, calling initializeVars()..."
			initializeVars()
		}
		if (traceEnable) log.trace "setting pollDeviceData() to be called in 5 seconds..."
		runIn(5, "pollDeviceData")
	} finally {
		if (traceEnable) log.trace "trace exit: configure()"
	}
}

//this method is called when the 'Save' button is clicked for preferences on the device page
//	https://community.hubitat.com/t/built-in-driver-methods-behavior/1995
void updated() {
	if (traceEnable) log.trace "trace entry: updated()"
	try {
		log.warn "debug logging is: ${debugEnable == true}"
		log.warn "trace logging is: ${traceEnable == true}"
		if (traceEnable) log.trace "calling unschedule()..."
		unschedule()
		if (debugEnable || traceEnable) {
			if (traceEnable) log.trace "setting logsOff() to be called in 1800 seconds..."
			runIn(1800,logsOff)
		}
		if (traceEnable) log.trace "calling runConfigs()..."
		runConfigs()
	} finally {
		if (traceEnable) log.trace "trace exit: updated()"
	}
}

//this method is called from the 'Refresh' on the device page
void refresh() {
	if (traceEnable) log.trace "trace entry: refresh()"
	try {
		sendToDevice([
			zwave.batteryV1.batteryGet(),
			zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1, scale: temperatureUnits),
			zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 5, scale: 0),
			zwave.thermostatFanModeV3.thermostatFanModeGet(),
			zwave.thermostatFanStateV1.thermostatFanStateGet(),
			zwave.thermostatModeV2.thermostatModeGet(),
			zwave.thermostatOperatingStateV1.thermostatOperatingStateGet(),
			zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1),
			zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 2)
		])
	} finally {
		if (traceEnable) log.trace "trace exit: refresh()"
	}
}

void ECOMode(value) {
	if (traceEnable) log.trace "trace entry: ECOMode()"
	try {
		if (debugEnable) log.debug "ECOMode($value)"
		sendToDevice(configCmd(3,1,value))
	} finally {
		if (traceEnable) log.trace "trace exit: ECOMode()"
	}
}

void DemandResponseMode(value) {
	if (traceEnable) log.trace "trace entry: DemandResponseMode()"
	try {
		if (debugEnable) log.debug "DemandResponseMode($value)"
		sendToDevice(configCmd(7,1,value))
	} finally {
		if (traceEnable) log.trace "trace exit: DemandResponseMode()"
	}
}

//this method is called when a Z-Wave message is received from the physical device and will call an appropriate
//zwave event 'listener' (defined further below)
void parse(String description) {
	if (traceEnable) log.trace "trace entry: parse()"
	try {
		if (debugEnable) log.debug "parse: ${description}"
		hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
		if (cmd) {
			if (debugEnable) log.debug "cmd: ${cmd.class}"
			zwaveEvent(cmd)
		}
	} finally {
		if (traceEnable) log.trace "trace exit: parse()"
	}
}

// ========================================================
// Z-WAVE EVENT 'LISTENERS'
// ========================================================

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport)"
	try {
		int scaledValue
		cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
		if(configParams[cmd.parameterNumber.toInteger()]) {
			Map configParam=configParams[cmd.parameterNumber.toInteger()]
			if (scaledValue > 127) scaledValue = scaledValue - 256
			device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
			//go ahead and make a data value that appears in the 'Data' row in the 'Device Details' section of the device page for each of
			//the config parameters - that way we can see feedback & be sure our preference setting changes have taken effect
			//HINT - be sure to 'translate' to the display name & value, as they appear in the preferences section
			device.updateDataValue(configParam.input.name, configParam.input.options[scaledValue])
			//now update our two attributes that are unique to this device
			if (cmd.parameterNumber==3) {
				eventProcess(name: "currEcoMode", value: scaledValue)
			}
			if (cmd.parameterNumber==7) {
				eventProcess(name: "currDemandResponseMode", value: scaledValue)
			}
		}
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation)"
	try {
		hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
		if (encapsulatedCommand) {
			zwaveEvent(encapsulatedCommand)
		}
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation)"
	}
}

void zwaveEvent(hubitat.zwave.commands.multicmdv1.MultiCmdEncap cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.multicmdv1.MultiCmdEncap)"
	try {
		if (debugEnable) log.debug "Got multicmd: ${cmd}"
		cmd.encapsulatedCommands(CMD_CLASS_VERS).each { encapsulatedCommand ->
			zwaveEvent(encapsulatedCommand)
		}
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.multicmdv1.MultiCmdEncap)"
	}
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet)"
	try {
		if (debugEnable) log.debug "Supervision get: ${cmd}"
		hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
		if (encapsulatedCommand) {
			zwaveEvent(encapsulatedCommand)
		}
		sendToDevice(new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet)"
	}
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport)"
	try {
		if (debugEnable) log.debug "version2 report: ${cmd}"
		device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
		device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
		device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport)"
	}
}

void zwaveEvent(hubitat.zwave.Command cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.Command)"
	try {
		if (debugEnable) log.debug "skip:${cmd}"
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.Command)"
	}
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport)"
	try {
		if (debugEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
		List<String> temp = []
		if (cmd.nodeId != []) {
			cmd.nodeId.each {
				temp.add(it.toString().format( '%02x', it.toInteger() ).toUpperCase())
			}
		}
		updateDataValue("zwaveAssociationG${cmd.groupingIdentifier}", "$temp")
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport)"
	try {
		if (debugEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
		log.info "${device.label?device.label:device.name}: Supported association groups: ${cmd.supportedGroupings}"
		state.associationGroups = cmd.supportedGroupings
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport)"
	try {
		if (debugEnable) log.debug "got battery report: ${cmd.batteryLevel}"
		Map evt = [name: "battery", unit: "%"]
		if (cmd.batteryLevel == 0xFF) {
			evt.descriptionText = "${device.displayName} has a low battery"
			evt.value = "1"
		} else {
			evt.descriptionText = "${device.displayName} battery is ${cmd.batteryLevel}%"
			evt.value = "${cmd.batteryLevel}"
		}
		if (txtEnable) log.info evt.descriptionText
		eventProcess(evt)
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport)"
	try {
		if (cmd.sensorType.toInteger() == 1) {
			if (debugEnable) log.debug "got temp: ${cmd.scaledSensorValue} (scale ${cmd.scale})"
			eventProcess(name: "temperature", value: cmd.scaledSensorValue, unit: cmd.scale == 0 ? "C" : "F")
		} else if (cmd.sensorType.toInteger() == 5) {
			if (debugEnable) log.debug "got humidity: ${cmd.scaledSensorValue} (scale ${cmd.scale})"
			eventProcess(name: "humidity", value: Math.round(cmd.scaledSensorValue), unit: cmd.scale == 0 ? "%": "g/m³")
		}
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.thermostatsetpointv1.ThermostatSetpointReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.thermostatsetpointv1.ThermostatSetpointReport)"
	try {
		if (debugEnable) log.debug "Got thermostat setpoint report: ${cmd}"
		def mode = [state.lastMode, device.currentValue("thermostatMode")]
		def wholeValue = Math.round(cmd.scaledValue)
		def unit = (cmd.scale == 0 ? "C" : "F")
		def physicalOrDigital = (state.isDigital?"digital":"physical")
		switch (cmd.setpointType) {
			case 1: //heat
				eventProcess(name: "heatingSetpoint", value: wholeValue, unit: unit, type: physicalOrDigital)
				if (mode.contains("heat")) { //if we are currently in a heating mode then send the overall setpoint event
					eventProcess(name: "thermostatSetpoint", value: wholeValue, unit: unit, type: physicalOrDigital)
				}
				break
			case 2: //cool
				eventProcess(name: "coolingSetpoint", value: wholeValue, unit: unit, type: physicalOrDigital)
				if (mode.contains("cool")) { //if we are currently in a cooling mode then send the overall setpoint event
					eventProcess(name: "thermostatSetpoint", value: wholeValue, unit: unit, type: physicalOrDigital)
				}
				break
		}
		//NOTE - not in any kind of heating or cooling mode? then last one to report wins meaning, on a refresh where we solicit separate
		//       setpoint reports for heat and cool, this will rapidly fire twice as each report comes in and the attribute will be left at
		//       the value of whichever report was last. This can't really be helped and is actually probably the correct behavior for
		//		 the situation.
		//NOTE - if we're in heat mode, for example, and setCoolingSetpoint is called then this event wouldn't fire, which is exactly what we want
		if (mode.intersect(["heat", "cool"]).isEmpty()) {
			eventProcess(name: "thermostatSetpoint", value: wholeValue, unit: unit, type: physicalOrDigital)
		}
		//this would only be true if we had called one of our own setpoint methods (i.e., digital), so be sure to set it to false here at the end
		//(so it will remain false, for example, if the setpoint is changed on the thermostat itself)
		state.isDigital=false
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.thermostatsetpointv1.ThermostatSetpointReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport)"
	try {
		if (debugEnable) log.debug "Got thermostat operating state report: ${cmd}"
		String newstate=THERMOSTAT_OPERATING_STATEv2[cmd.operatingState.toInteger()]
		if (debugEnable) log.debug "Translated state: " + newstate
		eventProcess(name: "thermostatOperatingState", value: newstate)
		state.lastMode = newstate
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport)"
	try {
		if (debugEnable) log.debug "Got thermostat fan state report: ${cmd}"
		String newstate=THERMOSTAT_FAN_STATEv1[cmd.fanOperatingState.toInteger()]
		if (debugEnable) log.debug "Translated fan state: " + newstate
		sendToDevice(zwave.configurationV1.configurationGet(parameterNumber: 52))
		if (newstate=="idle" && (device.currentValue("thermostatOperatingState")=="heating" || device.currentValue=="cooling")) sendToDevice(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet())
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport)"
	try {
		if (debugEnable) log.debug "Got thermostat fan mode report: ${cmd}"
		String newmode=THERMOSTAT_FAN_MODEv1[cmd.fanMode.toInteger()]
		if (debugEnable) log.debug "Translated fan mode: " + newmode
		eventProcess(name: "thermostatFanMode", value: newmode, type: state.isDigital?"digital":"physical")
		state.isDigital=false
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport)"
	try {
		if (debugEnable) log.debug "Got thermostat mode report: ${cmd}"
		String newmode=THERMOSTAT_MODEv2[cmd.mode.toInteger()]
		if (debugEnable) log.debug "Translated thermostat mode: " + newmode
		eventProcess(name: "thermostatMode", value: newmode, type: state.isDigital?"digital":"physical")
		state.isDigital=false
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport)"
	}
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	if (traceEnable) log.trace "trace entry: zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet)"
	try {
		if (debugEnable) log.debug "basic set: ${cmd}"
		// setup basic reports for missed operating state changes
		if (cmd.value.toInteger()==0xFF) {
			if (device.currentValue("thermostatOperatingState")!="heating" || device.currentValue!="cooling") sendToDevice(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet())
		} else {
			if (device.currentValue("thermostatOperatingState")=="heating" || device.currentValue=="cooling") sendToDevice(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet())
		}
	} finally {
		if (traceEnable) log.trace "trace exit: zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet)"
	}
}

// ========================================================
// SUPPORTED THERMOSTAT COMMANDS
// ========================================================

//called from either arrow up/down on dashboard tile or heating setpoint command on device page
void setHeatingSetpoint(degrees) {
	if (traceEnable) log.trace "trace entry: setHeatingSetpoint()"
	try {
		setSetpoint(1,degrees)
	} finally {
		if (traceEnable) log.trace "trace exit: setHeatingSetpoint()"
	}
}

//called from either arrow up/down on dashboard tile or cooling setpoint command on device page
void setCoolingSetpoint(degrees) {
	if (traceEnable) log.trace "trace entry: setCoolingSetpoint()"
	try {
		setSetpoint(2,degrees)
	} finally {
		if (traceEnable) log.trace "trace exit: setCoolingSetpoint()"
	}
}

void setThermostatMode(mode) {
	if (traceEnable) log.trace "trace entry: setThermostatMode()"
	try {
		mode = mode.trim()
		if (!supportedThermostatModes.contains(mode)) {
			log.warn "thermostatMode ${mode} is not supported by this driver"
			return
		}
		if (debugEnable) log.debug "setting zwave thermostat mode ${mode} - ${SET_THERMOSTAT_MODEv2[mode]}"
		state.isDigital=true
		sendToDevice([
			zwave.thermostatModeV2.thermostatModeSet(mode: SET_THERMOSTAT_MODEv2[mode]),
			zwave.thermostatModeV2.thermostatModeGet()
		])
	} finally {
		if (traceEnable) log.trace "trace exit: setThermostatMode()"
	}
}

void setThermostatFanMode(mode) {
	if (traceEnable) log.trace "trace entry: setThermostatFanMode()"
	try {
		mode = mode.trim()
		if (!supportedThermostatFanModes.contains(mode)) {
			log.warn "thermostatFanMode ${mode} is not supported by this driver"
			return
		}
		if (debugEnable) log.debug "setting zwave thermostat fan mode ${mode} - ${SET_THERMOSTAT_FAN_MODEv1[mode]}"
		state.isDigital=true
		sendToDevice([
			zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: SET_THERMOSTAT_FAN_MODEv1[mode]),
			zwave.thermostatFanModeV3.thermostatFanModeGet()
		])
	} finally {
		if (traceEnable) log.trace "trace exit: setThermostatFanMode()"
	}
}

void off() {
	if (traceEnable) log.trace "trace entry: off()"
	try {
		state.isDigital=true
		setThermostatMode("off")
	} finally {
		if (traceEnable) log.trace "trace exit: off()"
	}
}

void heat() {
	if (traceEnable) log.trace "trace entry: heat()"
	try {
		state.isDigital=true
		setThermostatMode("heat")
	} finally {
		if (traceEnable) log.trace "trace exit: heat()"
	}
}

void cool() {
	if (traceEnable) log.trace "trace entry: cool()"
	try {
		state.isDigital=true
		setThermostatMode("cool")
	} finally {
		if (traceEnable) log.trace "trace exit: cool()"
	}
}

void fanOn() {
	if (traceEnable) log.trace "trace entry: fanOn()"
	try {
		state.isDigital=true
		setThermostatFanMode("on")
	} finally {
		if (traceEnable) log.trace "trace exit: fanOn()"
	}
}

void fanAuto() {
	if (traceEnable) log.trace "trace entry: fanAuto()"
	try {
		state.isDigital=true
		setThermostatFanMode("auto")
	} finally {
		if (traceEnable) log.trace "trace exit: fanAuto()"
	}
}

// ========================================================
// HELPERS
// ========================================================

void sendToDevice(List<hubitat.zwave.Command> cmds) {
	if (traceEnable) log.trace "trace entry: sendToDevice(List<hubitat.zwave.Command>)"
	try {
		sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE))
	} finally {
		if (traceEnable) log.trace "trace exit: sendToDevice(List<hubitat.zwave.Command>)"
	}
}

void sendToDevice(hubitat.zwave.Command cmd) {
	if (traceEnable) log.trace "trace entry: sendToDevice(hubitat.zwave.Command)"
	try {
		sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
	} finally {
		if (traceEnable) log.trace "trace exit: sendToDevice(hubitat.zwave.Command)"
	}
}

void sendToDevice(String cmd) {
	if (traceEnable) log.trace "trace entry: sendToDevice(String)"
	try {
		sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
	} finally {
		if (traceEnable) log.trace "trace exit: sendToDevice(String)"
	}
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) {
	if (traceEnable) log.trace "trace entry: commands()"
	try {
		return delayBetween(cmds.collect{ secureCommand(it) }, delay)
	} finally {
		if (traceEnable) log.trace "trace exit: commands()"
	}
}

String secureCommand(hubitat.zwave.Command cmd) {
	if (traceEnable) log.trace "trace entry: secureCommand(hubitat.zwave.Command)"
	try {
		secureCommand(cmd.format())
	} finally {
		if (traceEnable) log.trace "trace exit: secureCommand(hubitat.zwave.Command)"
	}
}

String secureCommand(String cmd) {
	if (traceEnable) log.trace "trace entry: secureCommand(String)"
	try {
		String encap=""
		if (getDataValue("zwaveSecurePairingComplete") != "true") {
			return cmd
		} else {
			encap = "988100"
		}
		return "${encap}${cmd}"
	} finally {
		if (traceEnable) log.trace "trace exit: secureCommand(String)"
	}
}

void initializeVars() {
	if (traceEnable) log.trace "trace entry: initializeVars()"
	try {
		// first run only
		String tStatModes = supportedThermostatModes.toString().replaceAll(/"/,"")
		String tStatFanModes = supportedThermostatFanModes.toString().replaceAll(/"/,"")
		if (traceEnable) {
			log.trace "sending events for supportedThermostatModes [${tStatModes}] and supportedThermostatFanModes [${tStatFanModes}]..."
		}
		sendEvent(name:"supportedThermostatModes", value: tStatModes, isStateChange:true)
		sendEvent(name:"supportedThermostatFanModes", value: tStatFanModes, isStateChange:true)
		if (traceEnable) log.trace "setting state initialized to true..."
		state.initialized=true
		if (traceEnable) log.trace "setting refresh() to be called in 15 seconds..."
		runIn(15, refresh)
	} finally {
		if (traceEnable) log.trace "trace exit: initializeVars()"
	}
}

//this method is called further below, by the updated() method.
//basically the purpose is to turn the debug logging off (if enabled) 30 minutes after an update
void logsOff(){
	if (traceEnable) log.trace "trace entry: logsOff()"
	try {
		log.warn "debug logging disabled..."
		device.updateSetting("debugEnable",[value:"false",type:"bool"])
		log.warn "trace logging disabled..."
		device.updateSetting("traceEnable",[value:"false",type:"bool"])
	} finally {
		if (traceEnable) log.trace "trace exit: logsOff()"
	}
}

void runConfigs() {
	if (traceEnable) log.trace "trace entry: runConfigs()"
	try {
		List<hubitat.zwave.Command> cmds=[]
		configParams.each { param, data ->
			if (settings[data.input.name]) {
				cmds.addAll(configCmd(param, data.parameterSize, settings[data.input.name]))
			}
		}
		if (traceEnable) log.trace "sending ${cmds.size} commands to the device"
		sendToDevice(cmds)
	} finally {
		if (traceEnable) log.trace "trace exit: runConfigs()"
	}
}

List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
	if (traceEnable) log.trace "trace entry: configCmd()"
	try {
		if (debugEnable) {
			if(configParams[parameterNumber.toInteger()]) {
				Map configParam=configParams[parameterNumber.toInteger()]
				log.debug "ParameterNumber: ${parameterNumber}, Size: ${size}, Value: ${scaledConfigurationValue} (${configParam.input.name} - ${configParam.input.options[scaledConfigurationValue.toInteger()]})"
			} else {
				log.debug "ParameterNumber: ${parameterNumber}, Size: ${size}, Value: ${scaledConfigurationValue}"
			}
		}
		List<hubitat.zwave.Command> cmds = []
		int intval=scaledConfigurationValue.toInteger()
		if (intval<0) intval=256 + intval
		cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), configurationValue: [(intval & 0xFF)]))
		cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()))
		return cmds
	} finally {
		if (traceEnable) log.trace "trace exit: configCmd()"
	}
}

void eventProcess(Map evt) {
	if (traceEnable) log.trace "trace entry: eventProcess()"
	try {
		if (device.currentValue(evt.name).toString() != evt.value.toString()) {
			evt.isStateChange=true
			sendEvent(evt)
		}
	} finally {
		if (traceEnable) log.trace "trace exit: eventProcess()"
	}
}

void pollDeviceData() {
	if (traceEnable) log.trace "trace entry: pollDeviceData()"
	try {
		sendToDevice([
			zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId),
			zwave.associationV2.associationGet(groupingIdentifier: 1),
			zwave.versionV2.versionGet(),
			pollConfigs()
		])
	} finally {
		if (traceEnable) log.trace "trace exit: pollDeviceData()"
	}
}

List<hubitat.zwave.Command> pollConfigs() {
	if (traceEnable) log.trace "trace entry: pollConfigs()"
	try {
		List<hubitat.zwave.Command> cmds=[]
		configParams.each { param, data ->
			if (settings[data.input.name]) {
				cmds.add(zwave.configurationV1.configurationGet(parameterNumber: param.toInteger()))
			}
		}
		return cmds
	} finally {
		if (traceEnable) log.trace "trace exit: pollConfigs()"
	}
}

private void setSetpoint(setPointType, value) {
	if (traceEnable) log.trace "trace entry: setSetpoint()"
	try {
		if (debugEnable) log.debug "setSetpoint ${setPointType} ${value}"
		state.isDigital=true
		sendToDevice([
			zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: setPointType, scale: getTemperatureScale()=="C" ? 0:1, precision: 0, scaledValue: value),
			zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: setPointType)
		])
	} finally {
		if (traceEnable) log.trace "trace exit: setSetpoint()"
	}
}

// ========================================================
// UNSUPPORTED THERMOSTAT COMMANDS
// ========================================================

void on() {
	if (traceEnable) log.trace "trace entry: on()"
	try {
		log.warn "Ambiguous use of on()"
	} finally {
		if (traceEnable) log.trace "trace exit: on()"
	}
}

void auto() {
	if (traceEnable) log.trace "trace entry: auto()"
	try {
		log.warn "auto is not supported by this driver"
	} finally {
		if (traceEnable) log.trace "trace exit: auto()"
	}
}

void emergencyHeat() {
	if (traceEnable) log.trace "trace entry: emergencyHeat()"
	try {
		log.warn "emergencyHeat is not supported by this driver"
	} finally {
		if (traceEnable) log.trace "trace exit: emergencyHeat()"
	}
}

void fanCirculate() {
	if (traceEnable) log.trace "trace entry: fanCirculate()"
	try {
		log.warn "fanCirculate is not supported by this driver"
	} finally {
		if (traceEnable) log.trace "trace exit: fanCirculate()"
	}
}

void setSchedule() {
	if (traceEnable) log.trace "trace entry: setSchedule()"
	try {
		log.warn "setSchedule is not supported by this driver"
	} finally {
		if (traceEnable) log.trace "trace exit: setSchedule()"
	}
}
