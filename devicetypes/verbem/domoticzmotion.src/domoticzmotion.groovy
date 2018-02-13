/**
 *  domoticzMotion
 *
 *  Copyright 2016 Martin Verbeek
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
	definition (name: "domoticzMotion", namespace: "verbem", author: "SmartThings") {
		capability "Motion Sensor"
		capability "Temperature Measurement"
		capability "Sensor"
		capability "Battery"
		capability "Actuator"
		capability "Refresh"
        capability "Signal Strength"
		capability "Health Check"
        capability "Illuminance Measurement"
        capability "Power"
        
        attribute "powerToday", "string"
        }

	tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "on", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "On", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "ON", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "off", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "Off", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "OFF", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "Error", label:"Install Error", backgroundColor: "#bc2323"
			}
            tileAttribute("device.powerToday", key: "SECONDARY_CONTROL") {
        		attributeState "powerToday",label:'${currentValue}', icon:"st.switches.switch.on", defaultState: true
            }
		}

		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		} 
        
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/battery.png"
		}        
		valueTile("temperature", "device.temperature", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "temparature", label:'${currentValue}', unit:"", icon:"st.Weather.weather2"
		}
		valueTile("illuminance", "device.illuminance", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "illuminance", label:'${currentValue} Lux', unit:"", icon:""
		}
		//valueTile("power", "device.powerToday", decoration: "flat", inactiveLabel: false, width: 4, height: 2) {
		//	state "powerToday", label:'${currentValue}', unit:"", icon:""
		//}
		valueTile("rssi", "device.rssi", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "rssi", label:'Signal ${currentValue}', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/network-signal.png"
        }

		main "motion"
		details(["motion", "temperature", "battery", "illuminance", "power", "rssi", "refresh"])
	}
}

def refresh() {
	log.debug "Executing 'refresh'"

    if (parent) {
        parent.domoticz_poll(getIDXAddress())
        if (device.currentValue("powerToday") == null) sendEvent(name:"powerToday", value:"Usage not reported")
    }
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

    //log.debug "Using IDX: $idx for device: ${device.id}"
    return idx
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {

	if (parent) {
        sendEvent(name: "DeviceWatch-Enroll", value: groovy.json.JsonOutput.toJson([protocol: "LAN", scheme:"untracked"]), displayed: false)
        if (device.currentValue("powerToday") == null) sendEvent(name:"powerToday", value:"Power not reported")
    }
    else {
    	log.error "You cannot use this DTH without the related SmartAPP Domoticz Server, the device needs to be a child of this App"
        sendEvent(name: "motion", value: "Error", descriptionText: "$device.displayName You cannot use this DTH without the related SmartAPP Domoticz Server", isStateChange: true)
    }
}