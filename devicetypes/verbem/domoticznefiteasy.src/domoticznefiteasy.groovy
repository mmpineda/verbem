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
 *	Ecobee Thermostat
 *
 *	Author: SmartThings
 *	Date: 2013-06-13
 */
metadata {
	definition (name: "domoticzNefitEasy", namespace: "verbem", author: "Martin Verbeek") {
		capability "Actuator"
		capability "Thermostat"
		capability "Temperature Measurement"
		capability "Sensor"
        capability "Switch"
		capability "Refresh"
		capability "Health Check"

		command "generateEvent"
		command "raiseSetpoint"
		command "lowerSetpoint"
		command "resumeProgram"
		command "switchMode"

		attribute "thermostatSetpoint", "number"
		attribute "thermostatStatus", "string"
		attribute "maxHeatingSetpoint", "number"
		attribute "minHeatingSetpoint", "number"
		attribute "deviceTemperatureUnit", "string"
		attribute "deviceAlive", "enum", ["true", "false"]
	}
//**************************

multiAttributeTile(name:"thermostatFull", type:"thermostat", width:6, height:4) {
    tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
        attributeState("temperature", label:'${currentValue}', unit:"C", defaultState: true,
					backgroundColors:[
							[value: 0, color: "#153591"],
							[value: 7, color: "#1e9cbb"],
							[value: 15, color: "#90d2a7"],
							[value: 23, color: "#44b621"],
							[value: 28, color: "#f1d801"],
							[value: 35, color: "#d04e00"],
							[value: 37, color: "#bc2323"],
					]        
        )
    }
    tileAttribute("device.temperature", key: "VALUE_CONTROL") {
        attributeState("VALUE_UP", action:"raiseSetpoint", icon:"st.thermostat.thermostat-up")
        attributeState("VALUE_DOWN", action:"lowerSetpoint", icon:"st.thermostat.thermostat-down")
    }
    tileAttribute("device.switch", key: "SECONDARY_CONTROL") {
        attributeState("On", label:'On', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzblinds.src/Nefit Warm Water.png")
        attributeState("Off", label:'Off', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzblinds.src/Nefit No Warm Water.png")
    }
    tileAttribute("device.thermostatOperatingState", key: "OPERATING_STATE") {
        attributeState("idle", backgroundColor:"#00A0DC")
        attributeState("heating", backgroundColor:"#e86d13")
        attributeState("cooling", backgroundColor:"#00A0DC")
    }
    tileAttribute("device.thermostatMode", key: "THERMOSTAT_MODE") {
        attributeState("eco", label:'${name}', action:"switchMode", nextState: "updating", icon: "st.thermostat.heating-cooling-off")
        attributeState("manual", label:'${name}', action:"switchMode", nextState: "updating", icon: "st.thermostat.heat")
        attributeState("auto", label:'${name}', action:"switchMode",  nextState: "updating", icon: "st.thermostat.auto")
        attributeState("holiday", label:'${name}', action:"switchMode",  nextState: "updating", icon: "st.thermostat.heating-cooling-off")
        attributeState("updating", label:"Working", icon: "st.secondary.secondary")

    }
    tileAttribute("device.heatingSetpoint", key: "HEATING_SETPOINT") {
        attributeState("heatingSetpoint", label:'${currentValue}', unit:"", defaultState: true)
    }
}

//**************************
		valueTile("currentStatus", "device.thermostatStatus", height: 1, width: 2, decoration: "flat") {
			state "thermostatStatus", label:'${currentValue}', backgroundColor:"#ffffff"
		}
		standardTile("refresh", "device.thermostatMode", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		standardTile("resumeProgram", "device.resumeProgram", inactiveLabel: false, decoration: "flat") {
			state "resume", action:"resumeProgram", nextState: "updating", label:'Resume', icon:"st.samsung.da.oven_ic_send"
			state "updating", label:"Working", icon: "st.secondary.secondary"
		}
		main "thermostatFull"
		details(["thermostatFull", "resumeProgram", "currentStatus", "refresh"])
	

	preferences {
		input "holdType", "enum", title: "Hold Type", description: "When changing temperature, use Temporary (Until next transition) or Permanent hold (default)", required: false, options:["Temporary", "Permanent"]
	}
}

void installed() {
    // The device refreshes every 5 minutes by default so if we miss 2 refreshes we can consider it offline
    // Using 12 minutes because in testing, device health team found that there could be "jitter"
    sendEvent(name: "checkInterval", value: 60 * 12, data: [protocol: "cloud"], displayed: false)
}

// Device Watch will ping the device to proactively determine if the device has gone offline
// If the device was online the last time we refreshed, trigger another refresh as part of the ping.
def ping() {
    def isAlive = device.currentValue("deviceAlive") == "true" ? true : false
    if (isAlive) {
        refresh()
    }
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def refresh() {
	poll()
}

void poll() {
	parent.nefitEasy_poll()
}

def generateEvent(Map results) {
	log.debug "parsing data $results"
	if(results) {
		results.each { name, value ->

			def linkText = getLinkText(device)
			def isChange = false
			def isDisplayed = true
			def event = [name: name, linkText: linkText, descriptionText: getThermostatDescriptionText(name, value, linkText),
						 handlerName: name]

			if (name=="temperature" || name=="heatingSetpoint" ) {
				def sendValue =  value.toInteger()
				isChange = isStateChange(device, name, value.toString())
				isDisplayed = isChange
				event << [value: sendValue, unit: temperatureScale, isStateChange: isChange, displayed: isDisplayed]
			}  	else if (name=="maxHeatingSetpoint" || name=="minHeatingSetpoint") {
                    def sendValue =  value.toInteger()
                    event << [value: sendValue, unit: temperatureScale, displayed: false]
			}  	else if (name=="manual" || name=="auto" ){
                    isChange = isStateChange(device, name, value.toString())
                    event << [value: value.toString(), isStateChange: isChange, displayed: false]
			} 	else if (name == "deviceAlive") {
                    isChange = isStateChange(device, name, value.toString())
                    event['isStateChange'] = isChange
                    event['displayed'] = false
			} 	else {
                    isChange = isStateChange(device, name, value.toString())
                    isDisplayed = isChange
                    event << [value: value.toString(), isStateChange: isChange, displayed: isDisplayed]
			}
			sendEvent(event)
		}
		generateSetpointEvent ()
		generateStatusEvent ()
	}
}

//return descriptionText to be shown on mobile activity feed
private getThermostatDescriptionText(name, value, linkText) {
	if(name == "temperature") {
		def sendValue =  value.toDouble()
		return "$linkText temperature is ${sendValue} ${location.temperatureScale}"

	} else if(name == "heatingSetpoint") {
		def sendValue =  value.toDouble()
		return "heating setpoint is ${sendValue} ${location.temperatureScale}"

	} else if (name == "thermostatMode") {
		return "thermostat mode is ${value}"

	} else {
		return "${name} = ${value}"
	}
}

void setHeatingSetpoint(setpoint) {
	log.debug "***heating setpoint $setpoint"
	def heatingSetpoint = setpoint
	def deviceId = device.deviceNetworkId
	def maxHeatingSetpoint = device.currentValue("maxHeatingSetpoint")
	def minHeatingSetpoint = device.currentValue("minHeatingSetpoint")

	//enforce limits of heatingSetpoint
	if (heatingSetpoint > maxHeatingSetpoint) {
		heatingSetpoint = maxHeatingSetpoint
	} else if (heatingSetpoint < minHeatingSetpoint) {
		heatingSetpoint = minHeatingSetpoint
	}


	log.debug "Sending setHeatingSetpoint> heatingSetpoint: ${heatingSetpoint}"

	def heatingValue = location.temperatureScale == "C"? convertCtoF(heatingSetpoint) : heatingSetpoint

	def sendHoldType = holdType ? (holdType=="Temporary")? "nextTransition" : (holdType=="Permanent")? "indefinite" : "indefinite" : "indefinite"
	if (parent.nefitEasy_setHold(heatingValue, deviceId, sendHoldType)) {
		sendEvent("name":"heatingSetpoint", "value":heatingSetpoint, "unit":location.temperatureScale)
		log.debug "Done setHeatingSetpoint> coolingSetpoint: ${coolingSetpoint}, heatingSetpoint: ${heatingSetpoint}"
		generateSetpointEvent()
		generateStatusEvent()
	} else {
		log.error "Error setHeatingSetpoint(setpoint)"
	}
}

void resumeProgram() {
	log.debug "resumeProgram() is called"
	sendEvent("name":"thermostatStatus", "value":"resuming schedule", "description":statusText, displayed: false)
	def deviceId = device.deviceNetworkId
	if (parent.nefitEasy_resumeProgram(deviceId)) {
		sendEvent("name":"thermostatStatus", "value":"setpoint is updating", "description":statusText, displayed: false)
		runIn(5, "poll")
		log.debug "resumeProgram() is done"
		sendEvent("name":"resumeProgram", "value":"resume", descriptionText: "resumeProgram is done", displayed: false, isStateChange: true)
	} else {
		sendEvent("name":"thermostatStatus", "value":"failed resume click refresh", "description":statusText, displayed: false)
		log.error "Error resumeProgram() check parent.resumeProgram(deviceId)"
	}

}

def modes() {
	if (state.modes) {
		log.debug "Modes = ${state.modes}"
		return state.modes
	}
	else {
		state.modes = parent.nefitEasy_availableModes(this)
		log.debug "Modes = ${state.modes}"
		return state.modes
	}
}

def switchMode() {
	log.debug "in switchMode"
	def currentMode = device.currentState("thermostatMode")?.value
	def lastTriedMode = state.lastTriedMode ?: currentMode ?: "off"
	def modeOrder = modes()
	def next = { modeOrder[modeOrder.indexOf(it) + 1] ?: modeOrder[0] }
	def nextMode = next(lastTriedMode)
	switchToMode(nextMode)
}

def switchToMode(nextMode) {
	log.debug "In switchToMode = ${nextMode}"
	if (nextMode in modes()) {
		state.lastTriedMode = nextMode
		"$nextMode"()
	} else {
		log.debug("no mode method '$nextMode'")
	}
}


def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}

def setThermostatMode(String mode) {
	log.debug "setThermostatMode($mode)"
	mode = mode.toLowerCase()
	switchToMode(mode)
}

def generateModeEvent(mode) {
	sendEvent(name: "thermostatMode", value: mode, descriptionText: "$device.displayName is in ${mode} mode", displayed: true)
}

def generateOperatingStateEvent(operatingState) {
	sendEvent(name: "thermostatOperatingState", value: operatingState, descriptionText: "$device.displayName is ${operatingState}", displayed: true)
}

def eco() {
	log.debug "eco"
	def deviceId = device.deviceNetworkId
	if (parent.nefitEasy_setMode("eco", deviceId))
		generateModeEvent("eco")
	else {
		log.debug "Error setting new mode."
		def currentMode = device.currentState("thermostatMode")?.value
		generateModeEvent(currentMode) // reset the tile back
	}
	generateSetpointEvent()
	generateStatusEvent()
}

def manual() {
	log.debug "manual"
	def deviceId = device.deviceNetworkId
	if (parent.nefitEasy_setMode("manual", deviceId))
		generateModeEvent("manual")
	else {
		log.debug "Error setting new mode."
		def currentMode = device.currentState("thermostatMode")?.value
		generateModeEvent(currentMode) // reset the tile back
	}
	generateSetpointEvent()
	generateStatusEvent()
}


def holiday() {
	log.debug "holiday"
	def deviceId = device.deviceNetworkId
	if (parent.nefitEasy_setMode("holiday", deviceId))
		generateModeEvent("holiday")
	else {
		log.debug "Error setting new mode."
		def currentMode = device.currentState("thermostatMode")?.value
		generateModeEvent(currentMode) // reset the tile back
	}
	generateSetpointEvent()
	generateStatusEvent()
}

def emergencyHeat() {
	auxHeatOnly()
}

def auto() {
	log.debug "auto"
	def deviceId = device.deviceNetworkId
	if (parent.nefitEasy_setMode("auto", deviceId))
		generateModeEvent("auto")
	else {
		log.debug "Error setting new mode."
		def currentMode = device.currentState("thermostatMode")?.value
		generateModeEvent(currentMode) // reset the tile back
	}
	generateSetpointEvent()
	generateStatusEvent()
}

def generateSetpointEvent() {
	log.debug "Generate SetPoint Event"

	def mode = device.currentValue("thermostatMode")

	def heatingSetpoint = device.currentValue("heatingSetpoint")
	def maxHeatingSetpoint = device.currentValue("maxHeatingSetpoint")
	def minHeatingSetpoint = device.currentValue("minHeatingSetpoint")

	log.debug "Current Mode = ${mode}"
	log.debug "Heating Setpoint = ${heatingSetpoint}"

	sendEvent("name":"maxHeatingSetpoint", "value":maxHeatingSetpoint, "unit":location.temperatureScale)
	sendEvent("name":"minHeatingSetpoint", "value":minHeatingSetpoint, "unit":location.temperatureScale)
	sendEvent("name":"heatingSetpoint", "value":heatingSetpoint, "unit":location.temperatureScale)

	if (mode == "manual") {
		sendEvent("name":"thermostatSetpoint", "value":heatingSetpoint, "unit":location.temperatureScale)
	} 	
    else if (mode == "auto") {
        sendEvent("name":"thermostatSetpoint", "value":"Auto")
    } 	
    else if (mode == "holiday") {
        sendEvent("name":"thermostatSetpoint", "value":"Holiday")
    }
    else if (mode == "eco") {
        sendEvent("name":"thermostatSetpoint", "value":"Eco")
    } 
}

void raiseSetpoint() {
	def mode = device.currentValue("thermostatMode")
	def targetvalue
	def maxHeatingSetpoint = device.currentValue("maxHeatingSetpoint")

	if (mode != "manual") {
		log.warn "this mode: $mode does not allow raiseSetpoint"
	} else {
		log.trace device.currentValue("heatingSetpoint") + device.currentValue("thermostatSetpoint")
		def heatingSetpoint = device.currentValue("heatingSetpoint")
		def thermostatSetpoint = device.currentValue("thermostatSetpoint")

		if (location.temperatureScale == "C") {
			maxHeatingSetpoint = maxHeatingSetpoint > 40 ? convertFtoC(maxHeatingSetpoint) : maxHeatingSetpoint
			heatingSetpoint = heatingSetpoint > 40 ? convertFtoC(heatingSetpoint) : heatingSetpoint
			thermostatSetpoint = thermostatSetpoint > 40 ? convertFtoC(thermostatSetpoint) : thermostatSetpoint
		}

		log.debug "raiseSetpoint() mode = ${mode}, heatingSetpoint: ${heatingSetpoint}, thermostatSetpoint:${thermostatSetpoint}"

		targetvalue = thermostatSetpoint ? thermostatSetpoint : 0
		targetvalue = location.temperatureScale == "F"? targetvalue + 1 : targetvalue + 0.5

		if ((mode == "manual") && targetvalue > maxHeatingSetpoint) {
			targetvalue = maxHeatingSetpoint
		} 

		sendEvent("name":"thermostatSetpoint", "value":targetvalue, "unit":location.temperatureScale, displayed: false)
		log.info "In mode $mode raiseSetpoint() to $targetvalue"

		runIn(3, "alterSetpoint", [data: [value:targetvalue], overwrite: true]) //when user click button this runIn will be overwrite
	}
}

//called by tile when user hit raise temperature button on UI
void lowerSetpoint() {
	def mode = device.currentValue("thermostatMode")
	def targetvalue
	def minHeatingSetpoint = device.currentValue("minHeatingSetpoint")

	if (mode != "manual") {
		log.warn "this mode: $mode does not allow lowerSetpoint"
	} else {
		def heatingSetpoint = device.currentValue("heatingSetpoint")
		def thermostatSetpoint = device.currentValue("thermostatSetpoint")

		if (location.temperatureScale == "C") {
			minHeatingSetpoint = minHeatingSetpoint > 40 ? convertFtoC(minHeatingSetpoint) : minHeatingSetpoint
			heatingSetpoint = heatingSetpoint > 40 ? convertFtoC(heatingSetpoint) : heatingSetpoint
			thermostatSetpoint = thermostatSetpoint > 40 ? convertFtoC(thermostatSetpoint) : thermostatSetpoint
		} 
		log.debug "lowerSetpoint() mode = ${mode}, heatingSetpoint: ${heatingSetpoint}, thermostatSetpoint:${thermostatSetpoint}"

		targetvalue = thermostatSetpoint ? thermostatSetpoint : 0
		targetvalue = location.temperatureScale == "F"? targetvalue - 1 : targetvalue - 0.5

		if ((mode == "manual") && targetvalue < minHeatingSetpoint) {
			targetvalue = minHeatingSetpoint
		}

		sendEvent("name":"thermostatSetpoint", "value":targetvalue, "unit":location.temperatureScale, displayed: false)
		log.info "In mode $mode lowerSetpoint() to $targetvalue"

		runIn(3, "alterSetpoint", [data: [value:targetvalue], overwrite: true]) //when user click button this runIn will be overwrite
	}
}

//called by raiseSetpoint() and lowerSetpoint()
void alterSetpoint(temp) {
	def mode = device.currentValue("thermostatMode")

	if (mode == "off" || mode == "auto") {
		log.warn "this mode: $mode does not allow alterSetpoint"
	} else {
		def heatingSetpoint = device.currentValue("heatingSetpoint")
		def deviceId = device.deviceNetworkId.split(/\./).last()

		def targetHeatingSetpoint

		def temperatureScaleHasChanged = false

		if (location.temperatureScale == "C") {
			if ( heatingSetpoint > 40.0 ) {
				temperatureScaleHasChanged = true
			}
		} else {
			if ( heatingSetpoint < 40.0) {
				temperatureScaleHasChanged = true
			}
		}

		//step1: check thermostatMode, enforce limits before sending request to cloud
		if (mode == "manual"){
			if (temp.value > coolingSetpoint){
				targetHeatingSetpoint = temp.value
			} else {
				targetHeatingSetpoint = temp.value
			}
		}

		log.debug "alterSetpoint >> in mode ${mode} trying to change heatingSetpoint to $targetHeatingSetpoint " +
				"coolingSetpoint to $targetCoolingSetpoint with holdType : ${holdType}"

		def sendHoldType = holdType ? (holdType=="Temporary")? "nextTransition" : (holdType=="Permanent")? "indefinite" : "indefinite" : "indefinite"

		def heatingValue = location.temperatureScale == "C"? convertCtoF(targetHeatingSetpoint) : targetHeatingSetpoint

		if (parent.nefitEasy_setHold(heatingValue, deviceId, sendHoldType)) {
			sendEvent("name": "thermostatSetpoint", "value": temp.value, displayed: false)
			sendEvent("name": "heatingSetpoint", "value": targetHeatingSetpoint, "unit": location.temperatureScale)
			log.debug "alterSetpoint in mode $mode succeed change setpoint to= ${temp.value}"
		} else {
			log.error "Error alterSetpoint()"
			if (mode == "manual"){
				sendEvent("name": "thermostatSetpoint", "value": heatingSetpoint.toString(), displayed: false)
			} 
		}

		if ( temperatureScaleHasChanged )
			generateSetpointEvent()
		generateStatusEvent()
	}
}

def generateStatusEvent() {
	def mode = device.currentValue("thermostatMode")
	def heatingSetpoint = device.currentValue("heatingSetpoint")
	def temperature = device.currentValue("temperature")
	def statusText

	log.debug "Generate Status Event for Mode = ${mode}"
	log.debug "Temperature = ${temperature}"
	log.debug "Heating set point = ${heatingSetpoint}"
	log.debug "HVAC Mode = ${mode}"

	if (mode == "manual") {
		if (temperature >= heatingSetpoint)
			statusText = "Right Now: Manual"
		else
			statusText = "Heating to ${heatingSetpoint} ${location.temperatureScale}"
	} else if (mode == "auto") {
		statusText = "Right Now: Auto"
	} else if (mode == "eco") {
		statusText = "Right Now: Eco"
	} else if (mode == "holiday") {
		statusText = "Right Now: Holiday"
	} else {
		statusText = "?"
	}

	log.debug "Generate Status Event = ${statusText}"
	sendEvent("name":"thermostatStatus", "value":statusText, "description":statusText, displayed: true)
}

def generateActivityFeedsEvent(notificationMessage) {
	sendEvent(name: "notificationMessage", value: "$device.displayName $notificationMessage", descriptionText: "$device.displayName $notificationMessage", displayed: true)
}

def roundC (tempC) {
	return (Math.round(tempC.toDouble() * 2))/2
}

def convertFtoC (tempF) {
	return ((Math.round(((tempF - 32)*(5/9)) * 2))/2).toDouble()
}

def convertCtoF (tempC) {
	return (Math.round(tempC * (9/5)) + 32).toInteger()
}