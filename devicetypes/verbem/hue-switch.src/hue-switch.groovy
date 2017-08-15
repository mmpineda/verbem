/**
 *  Hue Dimmer Switch.
 *
 *  SmartDevice type for domoticz switches and dimmers.
 *  
 *
 *  Copyright (c) 2017 Martin Verbeek
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation, either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *  Revision History
 *  ----------------
 *  2017-07-10 1.00 Initial Release
 */

metadata {
    definition (name:"Hue Switch", namespace:"verbem", author:"Martin Verbeek") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
        capability "Refresh"
        capability "Polling"
        capability "Battery"
		capability "Button"
		capability "Health Check"        
      
        // custom commands
        command "parse"     // (String "<attribute>:<value>[,<attribute>:<value>]")
       	command "setLevel"
       	command "buttonEvent", ["number"]

    }

    tiles(scale:2) {
    	multiAttributeTile(name:"hueDimmerSwitch", type:"lighting",  width:6, height:4, canChangeIcon: true, canChangeBackground: true) {
        	tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "off", label:'Off', icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/hue-switch.src/Hue Dimmer Switch.jpg", backgroundColor:"#ffffff"
                attributeState "Off", label:'Off', icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff"
                attributeState "OFF", label:'Off',icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff"
                
                attributeState "on", label:'On', icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/hue-switch.src/Hue Dimmer Switch.jpg", backgroundColor:"#00a0dc"
                attributeState "On", label:'On', icon:"st.lights.philips.hue-single", backgroundColor:"#00a0dc"
                attributeState "ON", label:'On', icon:"st.lights.philips.hue-single", backgroundColor:"#00a0dc"
                attributeState "Error", label:'Install Error', icon:"st.lights.philips.hue-single", backgroundColor:"#bc2323"
                
            }
            tileAttribute("device.level", key: "SLIDER_CONTROL", range:"0..16") {
            	attributeState "level", action:"setLevel" 
            }
        }
     
		standardTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/battery.png"
		}
        
        standardTile("refresh", "device.motion", inactiveLabel: false, decoration: "flat", width:2, height:2) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["hueDimmerSwitch"])
        
        details(["hueDimmerSwitch", "battery", "refresh"])
    }
}

def parse(String message) {
    TRACE("parse(${message})")

    return null
}

def buttonEvent(button) {
	int xButton = (button / 1000)

	
log.info "Button ${button} xButton ${xButton}" 
	
    switch (xButton) {
        case 1:
            sendEvent(name: "switch", value: "on", descriptionText: "$device.displayName On button $xButton was pushed", isStateChange: true)
            break
        case 2:
        	def xLevel = 10
            if (device.currentValue("level")) {
                xLevel = device.currentValue("level")
                if (xLevel < 91) xLevel = xLevel+10
            }
            sendEvent(name: "level", value: xLevel, isStateChange: true)
            break
        case 3:
        	def xLevel = 100
            if (device.currentValue("level")) {
                xLevel = device.currentValue("level") 
                if (xLevel > 9) xLevel = xLevel-10
            }
            sendEvent(name: "level", value: xLevel, isStateChange: true)
            break
        case 4:
            sendEvent(name: "switch", value: "off", descriptionText: "$device.displayName Off button $xButton was pushed", isStateChange: true)
           break
	}

	
	if (button in [1001, 1003, 2001, 2003, 3001, 3003, 4001, 4003]) {
    	state.sceneCycle = 10
    	sendEvent(name: "button", value: "held", data: [buttonNumber: xButton, icon:iconPath], descriptionText: "$device.displayName button $xButton was pushed Long", isStateChange: true)
        }
    else if (button == 1002) {
    		if (!state.sceneCycle) state.sceneCycle = 10
            
    		sendEvent(name: "button", value: "pushed", data: [buttonNumber: state.sceneCycle], descriptionText: "$device.displayName button $state.sceneCycle was pushed", isStateChange: true)
            state.sceneCycle = state.sceneCycle + 1
            
            if (state.sceneCycle > 14) state.sceneCycle = 10 
    	}
    		else {
            	state.sceneCycle = 10
        		sendEvent(name: "button", value: "pushed", data: [buttonNumber: xButton], descriptionText: "$device.displayName button $xButton was pushed", isStateChange: true)
            }
    
}
// switch.poll() command handler
def poll() {
}

// switch.poll() command handler
def refresh() {
}

// switch.on() command handler
def on() {
}

// switch.off() command handler
def off() {
}

// Custom setlevel() command handler
def setLevel(level) {
	sendEvent(name: "level", value: level, isStateChange: true)
}

private def TRACE(message) {
    log.debug message
}

private def STATE() {
    log.debug "switch is ${device.currentValue("switch")}"
    log.debug "deviceNetworkId: ${device.deviceNetworkId}"
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
        sendEvent(name: "DeviceWatch-Enroll", value: groovy.json.JsonOutput.toJson([protocol: "LAN", scheme:"untracked"]), displayed: false)
        sendEvent(name: "level", value: 100)
        sendEvent(name: "numberOfButtons", value: 14)
        state.sceneCycle = 10
        log.info "number of buttons is 14"
    }
    else {
    	log.error "You cannot use this DTH without the related SmartAPP Hue Sensor (Connect), the device needs to be a child of this App"
        sendEvent(name: "switch", value: "Error", descriptionText: "$device.displayName You cannot use this DTH without the related SmartAPP Hue Sensor (Connect)", isStateChange: true)
    }
}