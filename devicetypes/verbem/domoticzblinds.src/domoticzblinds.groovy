/**
 *  domoticzBlinds
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
 *  2.1 2016-11-21 Rework of setColor 
 *	2.2 2016-12-01 added calibration of the closing time, now you can use setlevel or ask alexa to dim to a percentage
 *	3.0 2016-12-24 cleanup of DTH statuses
 */
import groovy.time.TimeCategory 
import groovy.time.TimeDuration

preferences {
    input(name:"stopSupported", type:"bool", title: "Stop command supported?", description:"Does your blind use the STOP command to halt the blind. Not the Somfy Stop/My command!", defaultValue:false)
}   
metadata {
	definition (name: "domoticzBlinds", namespace: "verbem", author: "Martin Verbeek") {
    
        capability "Actuator"
        capability "Switch"
        capability "Switch Level"
        capability "Refresh"
        capability "Polling"
        capability "Door Control"
        capability "Signal Strength"

        // custom attributes
        attribute "networkId", "string"
        attribute "calibrationInProgress", "string"
        attribute "startCalibrationTime", "number"
        attribute "endCalibrationTime", "number"
        attribute "blindClosingTime", "number"

        attribute "windBearing", "string"
        attribute "windSpeed", "number"
		attribute "cloudCover", "number"
		attribute "sunBearing", "string"


        // custom commands
        command "parse"     // (String "<attribute>:<value>[,<attribute>:<value>]")
        command "close"
        command "open"
        command "stop"
        command "setLevel"
        command "calibrate"
        command "generateEvent"

    }

    tiles (scale: 2) {
	    multiAttributeTile(name:"richDomoticzBlind", type:"generic",  width:6, height:4, canChangeIcon: true, canChangeBackground: true) {
        	tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "Open", 			label:"   Open   ", backgroundColor:"#19f028", nextState:"Going Down", action:"stop"	
                attributeState "Going Up", 		label:" Going Up ", backgroundColor:"#FE9A2E", nextState:"Going Down", action:"open"

				attributeState "Stopped", 		label:" Stopped  ", backgroundColor:"#11A81C", action:"close"
                
                attributeState "Closed", 		label:"  Closed  ",  backgroundColor:"#08540E", nextState:"Going Up"
                attributeState "Going Down", 	label:"Going Down",  backgroundColor:"#FE9A2E", nextState:"Going Up", action:"close"
            }
            tileAttribute("device.level", key: "SLIDER_CONTROL", range:"0..16") {
            	attributeState "level", action:"setLevel" 
            }
        }
 
        
        standardTile("Up", "device.switch", width: 2, height: 2, inactiveLabel:false, decoration:"flat") {
            state "default", label:'Up', icon:"st.doors.garage.garage-opening",
                action:"open"
        }

        standardTile("Stop", "device.switch", width: 2, height: 2, inactiveLabel:false, decoration:"flat") {
            state "default", label:'Stop', icon:"st.doors.garage.garage-open",
                action:"stop"
        }

        standardTile("Down", "device.switch", width: 2, height: 2, inactiveLabel:false, decoration:"flat") {
            state "default", label:'Down', icon:"st.doors.garage.garage-closing",
                action:"close"
        }

		standardTile("Cal", "device.switch", width: 2, height: 2, inactiveLabel:false, decoration:"flat") {
            state "default", label:'Calibrate', icon:"st.doors.garage.garage-closing",
                action:"calibrate"
        }

 		standardTile("windBearing", "device.windBearing",  inactiveLabel: false, width: 2, height: 2) {
			state "windBearing", label:'${currentValue}', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzblinds.src/windBearing.png"            
        }

 		standardTile("windSpeed", "device.windSpeed",  inactiveLabel: false, width: 2, height: 2) {
			state "windSpeed", label:'${currentValue} km/h', unit:"km/h", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzblinds.src/windSpeed.png"            
        }

 		standardTile("sunBearing", "device.sunBearing",  inactiveLabel: false, width: 2, height: 2) {
			state "sunBearing", label:'${currentValue}', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzblinds.src/sunBearing.png"            
        }

 		standardTile("cloudCover", "device.cloudCover",  inactiveLabel: false, width: 2, height: 2) {
			state "cloudCover", label:'${currentValue}%', unit:"%", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzblinds.src/cloudCover.png"            
        }

		standardTile("rssi", "device.rssi", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "rssi", label:'Signal ${currentValue}', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/network-signal.png"
		}
        
        standardTile("Refresh", "device.refresh", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["richDomoticzBlind"])
        details(["richDomoticzBlind", "Up", "Stop", "Down", "Cal", "windBearing", "windSpeed", "rssi", "sunBearing", "cloudCover", "Refresh"])

    }    
}

// parse events into attributes
def parse(String message) {
    log.debug "parse(${message})"

    Map msg = stringToMap(message)
    if (msg?.size() == 0) {
        log.error "Parse- Invalid message: ${message}"
        return null
    }


    if (msg.containsKey("switch")) {
    	log.debug "Parse- value  ${msg.switch}"
        switch (msg.switch.toUpperCase()) {
        case "ON": close(); break
        case "STOPPED": stop(); break
        case "OFF": open(); break
        sendPush( "Parse- Invalid message: ${message}")
        return null
        }
    }
//    STATE()
    return null
}

// handle commands

def on() {
	log.debug "Close()"
    if (parent) {
        parent.domoticz_on(getIDXAddress())
    }

}

def off() {
	log.debug "Open()"
    if (parent) {
        parent.domoticz_off(getIDXAddress())
    }
}

def close() {
	log.debug "Close()"
    if (parent) {
    	log.debug parent.state.devices[getIDXAddress()].switchTypeVal
        parent.domoticz_on(getIDXAddress())
    }
}

def refresh() {
	log.debug "Refresh()"
    if (parent) {
        parent.domoticz_poll(getIDXAddress())
    }
}

def poll() {
	log.debug "Poll()"
    if (parent) {
        parent.domoticz_poll(getIDXAddress())
    }
}

def open() {
	log.debug "Open()"
    if (parent) {
        parent.domoticz_off(getIDXAddress())
    }
}

def stop() {
	log.debug "Stop()"
    if (parent) {
        sendEvent(name:'switch', value:"Stopped" as String)
        parent.domoticz_stop(getIDXAddress())
    }

}

def setUpTile() {
	return "XX"
}
/* 	special implementation through setlevel
	It also makes it possible to use Alexa DIM!!!
*/
def setLevel(level) {
	log.debug "setLevel() level ${level}"
   
    if (parent) {
    	if (device.currentValue("blindClosingTime")) {
        	if (device.currentValue("blindClosingTime") > 0 && device.currentValue("blindClosingTime") < 100000) {
        		parent.domoticz_off(getIDXAddress())

				def Sec = Math.round(device.currentValue("blindClosingTime").toInteger()/1000)
				log.debug "setLevel() OFF runIn ${Sec} s"
                runIn(Sec, setLevelCloseAgain)
         		
                Sec = Sec + Math.round(Sec*level/100)
				log.debug "setLevel() Stop runIn ${Sec} s"
                runIn(Sec, setLevelStopAgain)
         		
            }
        }
        else {
            sendEvent(name:'switch', value:"Stopped" as String)
            parent.domoticz_stop(getIDXAddress())
        }
    }
}

def setLevelCloseAgain() {
    parent.domoticz_on(getIDXAddress())
    log.debug "setLevel() ON "
}
def setLevelStopAgain() {

    if (stopSupported) {
        parent.domoticz_stop(getIDXAddress())
        log.debug "setLevel() STOP"
    	}
    else {
        parent.domoticz_on(getIDXAddress())
        log.debug "setLevel() second ON"
    	}
}


def calibrate() {
    
    if (device.currentValue("calibrationInProgress") == "yes")
    	{
        sendEvent(name: "calibrationInProgress", value: "no")
        def eTime = new Date().time
        sendEvent(name: "endCalibrationTime", value: eTime)
        def eT = device.currentValue("endCalibrationTime")
        def sT = device.currentValue("startCalibrationTime")
        def blindClosingTime = (eT - sT)
        sendEvent(name: "blindClosingTime", value: blindClosingTime)
		log.debug "Calibrate End() - blindClosingTime ${device.currentValue("blindClosingTime")} ms"       
        parent.domoticz_off(getIDXAddress())
        }
    else
    	{
		log.debug "Calibrate Start()"       
        sendEvent(name: "calibrationInProgress", value: "yes")
        def sTime = new Date().time
        sendEvent(name: "startCalibrationTime", value: sTime)
        parent.domoticz_on(getIDXAddress())
        }
}

private String makeNetworkId(ipaddr, port) {

    String hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    String hexPort = String.format('%04X', port)
    return "${hexIp}:${hexPort}"
}

// gets the address of the device
private getHostAddress() {
	
    def ip = getDataValue("ip")
    def port = getDataValue("port")
    
    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 3) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    //log.debug "Using ip: $ip and port: $port for device: ${device.id}"
    return ip + ":" + port

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

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

// gets the address of the hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

/*----------------------------------------------------*/
/*			execute event can be called from the service manager!!!
/*----------------------------------------------------*/
def generateEvent (Map results) {

results.each { name, value ->
	log.info "generateEvent " + name + " " + value
	sendEvent(name:"${name}", value:"${value}")
    }
    return null
}