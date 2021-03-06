/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Aeon Multisensor", namespace: "smartthings", author: "SmartThings") {
		capability "Motion Sensor"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		capability "Configuration"
		capability "Illuminance Measurement"
		capability "Sensor"
		capability "Battery"
		capability "Health Check"

		fingerprint deviceId: "0x2001", inClusters: "0x30,0x31,0x80,0x84,0x70,0x85,0x72,0x86"
	}

	simulator {
		// messages the device returns in response to commands it receives
		status "motion (basic)"     : "command: 2001, payload: FF"
		status "no motion (basic)"  : "command: 2001, payload: 00"
		status "motion (binary)"    : "command: 3003, payload: FF"
		status "no motion (binary)" : "command: 3003, payload: 00"
		status "wakeup" 			: "command: 8407, payload: "

		for (int i = 0; i <= 100; i += 20) {
			status "temperature ${i}F": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 1, sensorType: 1, scale: 1).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "humidity ${i}%": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 0, sensorType: 5).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "luminance ${i} lux": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 0, sensorType: 3).incomingMessage()
		}
		for (int i = 200; i <= 1000; i += 200) {
			status "luminance ${i} lux": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 0, sensorType: 3).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "battery ${i}%": new physicalgraph.zwave.Zwave().batteryV1.batteryReport(
				batteryLevel: i).incomingMessage()
		}
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#cccccc"
			}
		}
		valueTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2) {
			state "temperature", label:'${currentValue}°',
			backgroundColors:[
				[value: 31, color: "#153591"],
				[value: 44, color: "#1e9cbb"],
				[value: 59, color: "#90d2a7"],
				[value: 74, color: "#44b621"],
				[value: 84, color: "#f1d801"],
				[value: 95, color: "#d04e00"],
				[value: 96, color: "#bc2323"]
			]
		}
		valueTile("humidity", "device.humidity", inactiveLabel: false, width: 2, height: 2) {
			state "humidity", label:'${currentValue}% humidity', unit:""
		}
		valueTile("illuminance", "device.illuminance", inactiveLabel: false, width: 2, height: 2) {
			state "luminosity", label:'${currentValue} lux', unit:""
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}

		main(["motion", "temperature", "humidity", "illuminance"])
		details(["motion", "temperature", "humidity", "illuminance", "battery"])
	}

	preferences {
		input description: "Please consult the operating manual for advanced setting options. You can skip this configuration to use default settings",
				title: "Advanced Configuration", displayDuringSetup: true, type: "paragraph", element: "paragraph"
		// these are cribbed from the newer aeon-multisensor-6
		input "motionDelayTime", "enum", title: "Motion Sensor Delay Time",
				options: ["20 seconds", "40 seconds", "1 minute", "2 minutes", "3 minutes", "4 minutes"], defaultValue: "${motionDelayTime}", displayDuringSetup: true

		input "reportInterval", "enum", title: "Sensors Report Interval",
				options: ["5 minutes", "8 minutes", "15 minutes", "30 minutes", "1 hour", "6 hours", "12 hours", "18 hours", "24 hours"], defaultValue: "${reportInterval}", displayDuringSetup: true
	}
}

def installed(){
// Device-Watch simply pings if no device events received for 32min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def updated(){
// Device-Watch simply pings if no device events received for 32min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])

	log.debug "Updated with settings: ${settings}"

	//preferences changed, so we should reconfigure on next wakeup
	setConfigured("false")
}

// Parse incoming device messages to generate events
def parse(String description)
{
	def results = []
	log.debug("parsing")
	if (description.startsWith("Err")) {
		results = createEvent(descriptionText:description, displayed:true)
	} else {
		def cmd = zwave.parse(description, [0x31: 2, 0x30: 1, 0x84: 1])
		if(cmd) results = zwaveEvent(cmd)
		if(!results) results = [ descriptionText: cmd, displayed: false ]
	}
	log.debug("Parsed '$description' to $results")
	return results
}

// Event Generation
def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	def results = []
	def cmds = []
	results << createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)
	// If we haven't configured yet, then do so now
	if (!isConfigured()) {
		cmds = configure()
	}
	cmds += zwave.wakeUpV1.wakeUpNoMoreInformation()
	results << response(cmds)
	return results
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd)
{
	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			// temperature
			def cmdScale = cmd.scale == 1 ? "F" : "C"
			map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.unit = getTemperatureScale()
			map.name = "temperature"
			break;
		case 3:
			// luminance
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "lux"
			map.name = "illuminance"
			break;
		case 5:
			// humidity
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "%"
			map.name = "humidity"
			break;
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	map.name = "battery"
	map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
	map.unit = "%"
	map.displayed = false
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
	def map = [name : "motion"]
	map.value = cmd.sensorValue ? "active" : "inactive"
	if (map.value == "active") {
		map.descriptionText = "$device.displayName detected motion"
	}
	else {
		map.descriptionText = "$device.displayName motion has stopped"
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	def map = [:]
	map.value = cmd.value ? "active" : "inactive"
	map.name = "motion"
	if (map.value == "active") {
		map.descriptionText = "$device.displayName detected motion"
	}
	else {
		map.descriptionText = "$device.displayName motion has stopped"
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Catchall reached for cmd: ${cmd.toString()}}"
	createEvent([:])
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	secure(zwave.batteryV1.batteryGet())
}

def configure() {
	def defaultTimeout = motionDelayTime ? timeOptionValueMap[motionDelayTime] : 20
	def defaultPeriod = reportInterval ? timeOptionValueMap[reportInterval]: 5*60

	setConfigured("true")

	delayBetween([
		// send remove association to avoid double reports
		zwave.associationV1.associationRemove(groupingIdentifier: 1, nodeId: zwaveHubNodeId).format(),

		// send binary sensor report instead of basic set for motion
		zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: 2).format(),

		// send no-motion report after a user-specified period (default 20 seconds) after motion stops
		zwave.configurationV1.configurationSet(parameterNumber: 3, size: 2, scaledConfigurationValue: defaultTimeout).format(),

		// send all data (temperature, humidity, & illuminance) periodically
		zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 224).format(),

		// set data reporting period for above to a user-specified period (default 5 minutes)
		zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: defaultPeriod).format(),

		// send a battery report less frequently
		zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 1).format(),

		// like once every 12 hours
		zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 12*60*60).format()
	]) + ["delay 5000"]
}

private def getTimeOptionValueMap() { [
		"20 seconds" : 20,
		"40 seconds" : 40,
		"1 minute"   : 60,
		"2 minutes"  : 2*60,
		"3 minutes"  : 3*60,
		"4 minutes"  : 4*60,
		"5 minutes"  : 5*60,
		"8 minutes"  : 8*60,
		"15 minutes" : 15*60,
		"30 minutes" : 30*60,
		"1 hours"    : 1*60*60,
		"6 hours"    : 6*60*60,
		"12 hours"   : 12*60*60,
		"18 hours"   : 6*60*60,
		"24 hours"   : 24*60*60,
]}

private setConfigured(configure) {
	updateDataValue("configured", configure)
}

private isConfigured() {
	getDataValue("configured") == "true"
}