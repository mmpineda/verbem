/**
 *  Hue Motion
 *
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
 *  Revision History
 *  ----------------
 *  2017-07-10 1.00 Initial Release
 *	1.01 Initialize with motion is inactive
 *	1.02 add switch capability to set HUE CONFIG On or OFF!!
 *	1.24 add custom attribute tempScale
 */
metadata {
	definition (name: "Hue Motion", namespace: "verbem", author: "Martin Verbeek") {
		capability "Motion Sensor"
        capability "Switch"
		capability "Sensor"
		capability "Battery"
		capability "Actuator"
        capability "Refresh"
        capability "Illuminance Measurement"
        capability "Temperature Measurement"
		capability "Health Check"
        
        attribute "tempScale", "string"
        }

	tiles(scale: 2) {
		multiAttributeTile(name:"hueMotion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "on", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "On", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "ON", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "off", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "Off", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "OFF", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
                attributeState "Error", label:'Install Error', icon:"st.motion.motion.inactive", backgroundColor:"#bc2323"
			}
		}
        
		standardTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/battery.png"
		}
         
		standardTile("illiminance", "device.illuminance", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "illuminance", label:'${currentValue} Lux', unit:"Lux"
		}
        
		standardTile("temperature", "device.tempScale", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "tempScale", label: '${currentValue}' , unit: ""
		}

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width:2, height:2) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        
        standardTile("switch", "device.switch", inactiveLabel: false, decoration: "flat", width:2, height:2) {
            state "on", label:'Hue Config True', action: "off"
            state "off", label: 'Hue Config False', action: "on"
        }

		main "hueMotion"
		details(["hueMotion", "illiminance", "temperature", "battery", "switch", "refresh"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'motion' attribute
    
}

def refresh() {
	parent.pollTheSensors(data:[elevatedPolling:false])
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def on() {
	parent.configHueMotion([action: true, sensor:device.deviceNetworkId])
}

def off() {
	parent.configHueMotion([action: false, sensor:device.deviceNetworkId])
}

def initialize() {
	// Arrival sensors only goes OFFLINE when Hub is off
    if (parent)	{
    	sendEvent(name: "DeviceWatch-Enroll", value: groovy.json.JsonOutput.toJson([protocol: "LAN", scheme:"untracked"]), displayed: false)
    	sendEvent(name: "motion", value: "inactive", isStateChange: true, displayed: false)
        }
	else {
    	log.error "You cannot use this DTH without the related SmartAPP Hue Sensor (Connect), the device needs to be a child of this App"
        sendEvent(name: "motion", value: "Error", descriptionText: "$device.displayName You cannot use this DTH without the related SmartAPP Hue Sensor (Connect)", isStateChange: true)
    }
}