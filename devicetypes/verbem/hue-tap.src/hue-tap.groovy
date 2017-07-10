import groovy.json.JsonOutput
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
 *  Revision History
 *  ----------------
 *  2017-07-10 1.00 Initial Release
 */
metadata {
	definition (name: "Hue Tap", namespace: "verbem", author: "Martin Verbeek") {
		capability "Actuator"
		capability "Button"
		capability "Sensor"
        capability "Refresh"
		capability "Health Check"

		command "buttonEvent", ["number"]
	}

	tiles {
		standardTile("hueTap", "device.button", decoration: "flat", width: 2, height: 2) {
			state "default", label: "", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/hue-tap.src/Hue Tap Button 1.PNG", backgroundColor: "#ffffff"
		}
        
        standardTile("refresh", "device.motion", inactiveLabel: false, decoration: "flat", width:2, height:2) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

		main "hueTap"
		details(["hueTap", "refresh"])
	}
}

def parse(String description) {

}

def buttonEvent(button) {
	button = button as Integer
    switch (button) {
        case "34":
            button = 1
            break
        case "16":
            button = 2
            break
        case "17":
            button = 3
            break
        case "18":
            button = 4
            break
	}
    
    def iconPath = "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/hue-tap.src/Hue Tap Button ${button.toString()}.PNG"
    
    sendEvent(name: "button", value: "pushed", data: [buttonNumber: button, icon:iconPath], descriptionText: "$device.displayName button $button was pushed", isStateChange: true)

}

def refresh() {
	
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
	// Arrival sensors only goes OFFLINE when Hub is off
	sendEvent(name: "DeviceWatch-Enroll", value: JsonOutput.toJson([protocol: "zigbee", scheme:"untracked"]), displayed: false)
	sendEvent(name: "numberOfButtons", value: 4)
}