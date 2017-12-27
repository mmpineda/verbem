/**
 *  domoticzPower component
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
 */
metadata {
	definition (name: "domoticzSelectorState", namespace: "verbem", author: "Martin Verbeek") {
		capability "Button"
		capability "Actuator"
		capability "Sensor"
        
        command "buttonPress"
	}

	tiles
    {
		standardTile("stateButton", "device.button", decoration: "flat", width: 1, height: 1) {
			state "default", label: 'Select', action: "buttonPress", icon: "st.Electronics.electronics13"
			state "Error", label: "Install Error", backgroundColor: "#bc2323"
		}        
		main (["stateButton"])
		details(["stateButton"])
	}
}

def buttonPress() {
	log.info parent.state.selector
    def stateLevels = device.displayName.tokenize("-")
    def stateLevel = stateLevels[stateLevels.size()-1]
    stateLevels = parent.state.selector.tokenize("|")
    def ix = 0
    def found = 200
    stateLevels.each {
    	if (it == stateLevel) {
        	found = ix
        }
        ix = ix + 10
    }
    
    if (found != 200) {
    	log.info "${device.displayName} set Level ${found} for ${stateLevel}"
        parent.setLevel(found)
    }
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
	// Arrival sensors only goes OFFLINE when Hub is off
    if (parent) {
        sendEvent(name: "numberOfButtons", value: 1)
    }
    else {
    	log.error "You cannot use this DTH without the related DTH domoticzSelector, the device needs to be a child of this DTH"
        sendEvent(name: "button", value: "Error", descriptionText: "$device.displayName You cannot use this DTH without the related SmartAPP Hue Sensor (Connect)", isStateChange: true)
    }
}