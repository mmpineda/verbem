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
        }

	tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
				attributeState "on", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
				attributeState "On", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
				attributeState "ON", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "off", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "Off", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "OFF", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
			}
		}

		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}
        
		valueTile("temperature", "device.temperature", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "temparature", label:'${currentValue}', unit:""
		}

		valueTile("rssi", "device.rssi", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "rssi", label:'Signal ${currentValue}', unit:""
        }

		main "motion"
		details(["motion", "temperature", "battery", "rssi", "refresh"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'motion' attribute

}

def refresh() {
	log.debug "Executing 'refresh'"

    if (parent) {
        parent.domoticz_poll(getIDXAddress())
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

/*----------------------------------------------------*/
/*			execute event can be called from the service manager!!!
/*----------------------------------------------------*/
def generateEvent (Map results) {
    results.each { name, value ->
    	def v = value
    	if (name.toUpperCase() == "MOTION") 
        	if (value.toUpperCase() == "ON") v = "active" else v = "inactive"
            
        log.info "generateEvent " + name + " " + v
        sendEvent(name:"${name}", value:"${v}")
        }
        return null
}