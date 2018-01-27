/**
 *  Copyright 2017 Martin Verbeek
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
 *	Generic Domoticz Thermostat
 *
 *	Author: Martin Verbeek
 *	
 	V1.00	Initial release
 
 */
metadata {
	definition (name: "domoticzThermostat", namespace: "verbem", author: "Martin Verbeek") {
		capability "Actuator"
		capability "Thermostat"
		capability "Temperature Measurement"
		capability "Sensor"
        capability "Switch"
		capability "Refresh"
		capability "Health Check"

		command "raiseSetpoint"
		command "lowerSetpoint"
		command "resumeProgram"

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
        tileAttribute("device.thermostatSetpoint", key: "VALUE_CONTROL") {
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
            attributeState("thermostatMode", 	label:'${currentValue}')
        }
        tileAttribute("device.coolingSetpoint", key: "COOLING_SETPOINT") {
            attributeState("coolingSetpoint", label:'${currentValue}', unit:"", defaultState: true)
        }
        tileAttribute("device.heatingSetpoint", key: "HEATING_SETPOINT") {
            attributeState("heatingSetpoint", label:'${currentValue}', unit:"", defaultState: true)
        }
    }

		valueTile("currentStatus", "device.thermostatStatus", height: 1, width: 2, decoration: "flat") {
			state "thermostatStatus", label:'${currentValue}', backgroundColor:"#ffffff"
		}
        
		main "thermostatFull"
		details(["thermostatFull", "currentStatus"])
	

	preferences {
		input "holdType", "enum", title: "Hold Type", description: "When changing temperature, use Temporary (Until next transition) or Permanent hold (default)", required: false, options:["Temporary", "Permanent"]
	}
}
void setThermostatMode(setMode) {
	sendEvent(name: "thermostatMode", value: setMode)
    log.info "Mode ${setMode} has been set"
    def thermostatOperatingState
    
    switch (setMode) {
    case "cool":
		thermostatOperatingState = "cooling"
		break
    case "heat":
		thermostatOperatingState = "heating"
    	break
    case "auto":
		thermostatOperatingState = "idle"
    	break
     case "off":
		thermostatOperatingState = "idle"
    	break
   default:
		thermostatOperatingState = "idle"
    }
    sendEvent(name: "thermostatOperatingState", value: thermostatOperatingState)    	
}
void setThermostatFanMode(setMode) {
	sendEvent(name: "thermostatFanMode", value: setMode)
    log.info "Fan Mode ${setMode} has been set"
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

void setCoolingSetpoint(setpoint) {
	def parts = device.deviceNetworkId.tokenize(":")
    def last = parts[parts.size()-1]
	log.debug "***cooling setpoint $setpoint for $last"
	parent.domoticz_setpoint(last, setpoint)  
    sendEvent(name: "coolingSetpoint", value: setpoint)
	sendEvent(name: "thermostatStatus", value:"Cooling to ${setpoint}", description:"Heating to ${setpoint}", displayed: true)  
    sendEvent(name: "thermostatOperatingState", value: "cooling")


	return 
}

void setHeatingSetpoint(setpoint) {
	def parts = device.deviceNetworkId.tokenize(":")
    def last = parts[parts.size()-1]
	log.debug "***heating setpoint $setpoint for $last"
    
	parent.domoticz_setpoint(last, setpoint)  
    sendEvent(name: "heatingSetpoint", value: setpoint)
	sendEvent(name: "thermostatStatus", value:"Heating to ${setpoint}", description:"Heating to ${setpoint}", displayed: true)    
    sendEvent(name: "thermostatOperatingState", value: "heating")
    
    return 
}

void resumeProgram() {
	log.debug "resumeProgram() is called"
}

def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}


void raiseSetpoint() {
	def currentSetpoint = device.currentValue("thermostatSetpoint") + 0.5  
    log.info currentSetpoint
    parent.domoticz_setpoint(getIDXAddress(), currentSetpoint)
}

//called by tile when user hit raise temperature button on UI
void lowerSetpoint() {
	def currentSetpoint = device.currentValue("thermostatSetpoint") - 0.5  
    log.info currentSetpoint
    parent.domoticz_setpoint(getIDXAddress(), currentSetpoint)
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

// gets the IDX address of the device
private getIDXAddress() {
	
    def idx = getDataValue("idx")
        
    if (!idx) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 3) {
            idx = parts[2]
        } else {
            log.warn "Can't figure out idx for device: ${device.id}"
        }
    }

    return idx
}