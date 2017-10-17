/**
 *  Domoticz OnOff SubType Switch.
 *
 *  SmartDevice type for domoticz switches and dimmers.
 *  
 *
 *  Copyright (c) 2016 Martin Verbeek
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
 * 	2017-10-17 4.11 ColorTemperature added, added multi parent control for domoticz and hue sensor (connect) 
 *	2017-04-28 3.13 Color setting for White types
 *  2017-04-14 3.12 Multistate support for DZ selector
 *  2017-01-25 3.09 Put in check for switch name in generateevent
 *	2017-01-18 3.08 get always an lowercase value for switch on/off in generateevent
 */

metadata {
    definition (name:"domoticzOnOff", namespace:"verbem", author:"Martin Verbeek") {
        capability "Actuator"
        capability "Sensor"
        capability "Color Control"
		capability "Color Temperature"
		capability "Switch"
        capability "Switch Level"
        capability "Refresh"
        capability "Polling"
        capability "Signal Strength"
		capability "Health Check"
        capability "Power Meter"
      
        // custom commands
        command "parse"     // (String "<attribute>:<value>[,<attribute>:<value>]")
        command "generateEvent"
        command "eodRunOnce"
    }

    tiles(scale:2) {
    	multiAttributeTile(name:"richDomoticzOnOff", type:"lighting",  width:6, height:4, canChangeIcon: true, canChangeBackground: true) {
        	tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "off", label:'Off', icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", action:"on", nextState:"Turning On"
                attributeState "Off", label:'Off', icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", action:"on", nextState:"Turning On"
                attributeState "OFF", label:'Off',icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", action:"on", nextState:"Turning On"
                attributeState "Turning Off", label:'Turning Off', icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", nextState:"Turning On"
                
                attributeState "on", label:'On', icon:"st.lights.philips.hue-single", backgroundColor:"#00a0dc", action:"off", nextState:"Turning Off"
                attributeState "On", label:'On', icon:"st.lights.philips.hue-single", backgroundColor:"#00a0dc", action:"off", nextState:"Turning Off"
                attributeState "ON", label:'On', icon:"st.lights.philips.hue-single", backgroundColor:"#00a0dc", action:"off", nextState:"Turning Off"
                attributeState "Set Level", label:'On', icon:"st.lights.philips.hue-single", backgroundColor:"#00a0dc", action:"off", nextState:"Turning Off"
                attributeState "Turning On", label:'Turning On', icon:"st.lights.philips.hue-single", backgroundColor:"#00a0dc", nextState:"Turning Off"
				attributeState "Error", label:'Install Error', backgroundColor: "#bc2323"
                
            }
            tileAttribute ("device.power", key: "SECONDARY_CONTROL") {
                attributeState "power", label:'Power level: ${currentValue}', icon: "st.Appliances.appliances17"
            }
            
            tileAttribute("device.level", key: "SLIDER_CONTROL") {
            	attributeState "level", label:'Dim Level: ${currentValue}', action:"setLevel" 
            }
            tileAttribute ("device.color", key: "COLOR_CONTROL") {
        		attributeState "color", action:"setColor"
            }
        }
     
		standardTile("rssi", "device.rssi", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "rssi", label:'Signal ${currentValue}', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/network-signal.png"
		}
        
        standardTile("debug", "device.motion", inactiveLabel: false, decoration: "flat", width:2, height:2) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["richDomoticzOnOff"])
        
        details(["richDomoticzOnOff", "rssi", "debug"])
    }
}

def parse(String message) {
    TRACE("parse(${message})")

    Map msg = stringToMap(message)
    if (msg?.size() == 0) {
        log.error "Invalid message: ${message}"
        return null
    }

    if (msg.containsKey("switch")) {
        def value = msg.switch.toInteger()
        switch (value) {
        case 0: off(); break
        case 1: on(); break
        }
    }

    STATE()
    return null
}

// switch.poll() command handler
def poll() {

    if (parent) {
        TRACE("poll() ${device.deviceNetworkId}")
        parent.domoticz_poll(getIDXAddress())
    }
}

// switch.poll() command handler
def refresh() {
    if (parent.name == "Domoticz Server") parent.domoticz_poll(getIDXAddress())
    if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "poll", "dni": device.deviceNetworkId])

}

// switch.on() command handler
def on() {
    if (parent.name == "Domoticz Server") parent.domoticz_on(getIDXAddress())
    if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "on", "dni": device.deviceNetworkId])
}

// switch.off() command handler
def off() {
    if (parent.name == "Domoticz Server") parent.domoticz_off(getIDXAddress())
    if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "off", "dni": device.deviceNetworkId])
}

// Custom setlevel() command handler
def setLevel(level) {
    TRACE("setLevel Level " + level)
    state.setLevel = level

    if (parent.name == "Domoticz Server") parent.domoticz_setlevel(getIDXAddress(), level)
    if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "level", "dni": device.deviceNetworkId, "level": level])
}

def setColorTemperature(ct) {
    //if (parent.name == "Domoticz Server") parent.domoticz_setlevel(getIDXAddress(), level)
    if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "colorTemperature", "dni": device.deviceNetworkId, "ct": ct])

}

// Custom setcolor() command handler hue from ST is percentage of 360 which is max HUE
def setColor(color) {

	def hexCode = null

    if (!color?.hex) {
        //hue:83, saturation:100, level:80
        log.trace color
        TRACE("SetColor HUE " + (color.hue*3.6) + " Sat " + Math.round(color.saturation) + " Level " + color.level)
        if (parent) {
            if (color.hue == 5 && color.saturation == 4) {
                hexCode = "FEFFFA"
                log.debug "Soft White - Default ${hexCode}"
            }
            else if (color.hue == 63 && color.saturation == 28) {
                hexCode = "EFF9FF"
                log.debug "White - Concentrate ${hexCode}"
            }
            else if (color.hue == 63 && color.saturation == 43) {
                hexCode = "FAFDFF"
                log.debug "Daylight - Energize ${hexCode}"
            }
            else if (color.hue == 79 && color.saturation == 7) {
                hexCode = "FFFAEE"
                log.debug "Warm White - Relax ${hexCode}"
            }
            if (hexCode == null) {
                log.trace "normal"
                if (parent.name == "Domoticz Server") parent.domoticz_setcolorHue(getIDXAddress(), (color.hue*3.6), Math.round(color.saturation), color.level)
                if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "hue", "dni": device.deviceNetworkId, "level": color.level, "hue": color.hue, "sat": color.sat])                           
            }
            else {
                log.trace "whitelevel"
                if (parent.name == "Domoticz Server") parent.domoticz_setcolorWhite(getIDXAddress(), hexCode, Math.round(color.saturation), color.level)
                if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "white", "dni": device.deviceNetworkId, "level": color.level, "hue": color.hue, "sat": Math.round(color.saturation)])                           
            }
        }
    }
    else {
        log.trace color
        TRACE("SetColor HEX " + color.hex[-6..-1] + " Sat " + Math.round(color.saturation) + " Level " + state.setLevel)
        if (parent) {
            if (parent.name == "Domoticz Server") parent.domoticz_setcolor(getIDXAddress(), color.hex[-6..-1], Math.round(color.saturation), state.setLevel)
            if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "hue", "dni": device.deviceNetworkId, "level": state.setLevel, "hue": color.hue, "sat": color.saturation])                        
        }
    }
}

private def TRACE(message) {
    log.debug message
}

private def STATE() {
    log.debug "switch is ${device.currentValue("switch")}"
    log.debug "deviceNetworkId: ${device.deviceNetworkId}"
}

// gets the IDX address of the device
private getIDXAddress() {
    
    def idx = getDataValue("idx")
        
    if (!idx) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 3) {
            idx = parts[2]
        } else {
            log.warn "Can't figure out idx for device: ${device.name} returning ${device.deviceNetworkId}"
            return device.deviceNetworkId.toString()
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
    	if (name == "switch") {
        	if (v instanceof String) {
                if (v.toUpperCase() == "OFF" ) v = "off"
                if (v.toUpperCase() == "ON") v = "on"
                }
            }
        log.info "generateEvent " + name + " " + v
        sendEvent(name:"${name}", value:"${v}")
        }
        return null
}

def getWhite(value) {
	log.debug "getWhite($value)"
	def level = Math.min(value as Integer, 99)    
    level = 255 * level/99 as Integer
	log.debug "level: ${level}"
	return level
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
    }
    else {
    	log.error "You cannot use this DTH without the related SmartAPP Domoticz Server, the device needs to be a child of this App"
        sendEvent(name: "switch", value: "Error", descriptionText: "$device.displayName You cannot use this DTH without the related SmartAPP Domoticz Server", isStateChange: true)
    }
}