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
 *	2018-01-28 6.00 Check state before issue on or off, dont with same state
 *	2017-12-31 5.02	Added power today and total to powerToday event, changed tile to display
 *	2017-12-15 5.00 implemented image graphs for power usage if available
 *  2017-10-28 4.12 correct to saturation color in normal setColor mode
 * 	2017-10-17 4.11 ColorTemperature added, added multi parent control for domoticz and hue sensor (connect) 
 *	2017-04-28 3.13 Color setting for White types
 *  2017-04-14 3.12 Multistate support for DZ selector
 *  2017-01-25 3.09 Put in check for switch name in generateevent
 *	2017-01-18 3.08 get always an lowercase value for switch on/off in generateevent
 */
import Calendar.*
import groovy.time.*

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
        capability "Image Capture"

		attribute "powerToday", "String"

        // custom commands
        command "parse"     
        command "generateEvent"
        command "hourLog"
        command	"day"
        command "week"
        command "month"
        command "year"
        
        attribute "hour", "string"
        attribute "graph", "string"
    }

    tiles(scale:2) {
    	multiAttributeTile(name: "richDomoticzOnOff", type: "lighting",  width:6, height:4, canChangeIcon: true, canChangeBackground: true) {
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
            tileAttribute ("device.powerToday", key: "SECONDARY_CONTROL") {
                attributeState "powerToday", label:'${currentValue}', icon: "st.Appliances.appliances17", defaultState: true
                attributeState "noPower", label:''
            }            
            tileAttribute("device.level", key: "SLIDER_CONTROL") {
            	attributeState "level", label:'${currentValue}', action:"setLevel" 
            }
            tileAttribute ("device.color", key: "COLOR_CONTROL") {
        		attributeState "color", action:"setColor"
            }
        }
        
        carouselTile("graph", "device.image", width: 6, height: 4)
        
        standardTile("HourLog", "device.hour", decoration: "flat", width: 1, height: 1) {
        	state "Graph", label:'48 Hour Log', action: "hourLog", defaultState: true
        	state "noGraph", label:'No 48 Hour Log', action: "hourLog"
        }
     
        standardTile("day", "device.graph", decoration: "flat", width: 1, height: 1) {
        	state "Graph", label:'Day Usage', action: "day", defaultState: true
        	state "noGraph", label:'No Day Usage', action: "day"
        }
     
        standardTile("week", "device.graph", decoration: "flat", width: 1, height: 1) {
        	state "Graph", label:'Week Usage', action: "week", defaultState: true
        	state "noGraph", label:'No Week Usage', action: "week"
        }
     
        standardTile("month", "device.graph", decoration: "flat", width: 1, height: 1) {
        	state "Graph", label:'Month Usage', action: "month", defaultState: true
        	state "noGraph", label:'No Month Usage', action: "month"
        }
     
        standardTile("year", "device.graph", decoration: "flat", width: 1, height: 1) {
        	state "Graph", label:'Year Usage', action: "year", defaultState: true
        	state "noGraph", label:'No Year Usage', action: "year"
        }
     
		standardTile("rssi", "device.rssi", decoration: "flat", width: 1, height: 1) {
			state "rssi", label:'Signal ${currentValue}', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/network-signal.png"
		}
        
        standardTile("debug", "device.motion", decoration: "flat", width:1, height:1) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["richDomoticzOnOff"])
        
        details(["richDomoticzOnOff", "day", "week", "month", "year", "graph", "rssi", "debug"])
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

// switch.refresh() command handler
def refresh() {


    if (parent.name == "Domoticz Server") {
    	parent.domoticz_poll(getIDXAddress())
    }
    
    if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "poll", "dni": device.deviceNetworkId]) 

}

// switch.on() command handler
def on() {
	if (device.currentValue("switch").toUpperCase() == "ON") return
    
    if (parent.name == "Domoticz Server") parent.domoticz_on(getIDXAddress())
    if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "on", "dni": device.deviceNetworkId])
}

// switch.off() command handler
def off() {
	if (device.currentValue("switch").toUpperCase() == "OFF") return
    
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
	log.trace "[setColor] " + color
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
                if (parent.name == "Hue Sensor (Connect)") parent.groupCommand(["command" : "hue", "dni": device.deviceNetworkId, "level": color.level, "hue": color.hue, "sat": color.saturation])                           
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

// Graph related commands

def hourLog() {
    if (parent.name == "Domoticz Server") {
    	sendEvent(name:"hour", value:"Graph")
    	sendLightlogRequest()    	
    }
    else sendEvent(name:"hour", value:"noGraph")
}

def day() {
    sendPowerChartRequest("day")	
}

def week() {
    sendPowerChartRequest("week")	
}

def month() {
    sendPowerChartRequest("month")	
}

def year() {
    sendPowerChartRequest("year")	
}

private def createDaily(result) {
    
	result.each { value ->
        state.chd = state.chd + value.v + "," 
        state.chxl = state.chxl + value.d.split()[1] + "%7C"
    }
}

private def createMonthkyOrWeekly(result) {
    
	result.each { value ->
        state.chd = state.chd + value.v + "," 
        state.chxl = state.chxl + value.d + "%7C"
    }
}

private def createYearly(result) {
	
    def weeks = [:]
    def weekno
	def date 
    
	result.each { value ->
        date = new Date().parse('yyyy-MM-dd', "${value.d}")
        weekno = "${date.getAt(Calendar.YEAR)}-${date.getAt(Calendar.WEEK_OF_YEAR)}"
        if (weeks[weekno]) weeks[weekno] = weeks[weekno] + value.v.toFloat()
        else weeks[weekno] = value.v.toFloat()
    }
      
    weeks.each { key, item ->
        state.chd = state.chd + item.round(3) + "," 
        state.chxl = state.chxl + key + "%7C"
    }
}

private def sendPowerChartRequest(range) {

	def idx = parent.state.devices[getIDXAddress()].idxPower
    if (!idx) return

	def power = device.currentValue("power")
    
    if (parent.name == "Domoticz Server" && power != null) {
    	sendEvent(name:"graph", value:"Graph")
    }
    else {
    	sendEvent(name:"graph", value:"noGraph")
        return
	}
    
    def rooPath = "/json.htm?type=graph&sensor=counter&idx=${idx}&range=${range}"
	def hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${parent.settings.domoticzIpAddress}:${parent.settings.domoticzTcpPort}"], null, [callback: handlerPowerChartRequest] )
    sendHubCommand(hubAction)
}

def handlerPowerChartRequest(evt) {
    def response = getResponse(evt)

	if (response?.result == null) return
    
    sendEvent(name:"${response.title.split()[2]}", value:"Graph")
    state.chd = "t:"
    state.chxl = "0:%7C"
    
    switch (response.title.split()[2]) {
        case "day":
	        state.chtt  = "Day+Usage"
            createDaily(response.result)
	        break;
        case "week":
	        state.chtt  = "Week+Usage"
            createMonthkyOrWeekly(response.result)
            break;
        case "month":
	        state.chtt  = "Month+Usage"
            createMonthkyOrWeekly(response.result)
            break;
        case "year":
	        state.chtt  = "Year+Usage"
        	createYearly(response.result)
        	break;
    }
	// this will also make a series of 1 valid for image-charts
    state.chd = state.chd + 0
    state.chxl = state.chxl + "%7C"

	state.chco = "0000FF"
	take()
}

private def sendLightlogRequest() {
	def idx = getIDXAddress()

    if (!idx) return

    sendEvent(name:"hour", value:"Graph")
    def rooPath = "/json.htm?type=lightlog&idx=${idx}"
	def hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${parent.settings.domoticzIpAddress}:${parent.settings.domoticzTcpPort}"], null, [callback: handler48HourRequest] )
    sendHubCommand(hubAction)
}

void handler48HourRequest(evt) {
    def response = getResponse(evt)

	if (response?.result == null) {
    	sendEvent(name:"hour", value:"noGraph")
    	return
    }
    
    initUsageMap()
    
// find the index in events that is the first with a lower date than the last entry in usageMap
    def dateItem
    def date48 = state.usageMap[48].startHour
    def breakEach = false
    def lastIndex
    
    response.result.eachWithIndex { item, index ->
		dateItem = new Date().parse('yyyy-MM-dd hh:mm:ss', "${item.Date}")
        if (item.Status.contains("Set")) item.Status = "On"
        if (dateItem < date48 && breakEach == false) {
        	breakEach = true
            lastIndex = item.idx
        }
    }
// eventmap with needed entries in reverse!
	def eventMap = response.result.findAll {it.idx >= lastIndex}
    eventMap = eventMap.reverse()
    def nextIndex = 0 
    def removeList = []
    
    eventMap.eachWithIndex { item, index ->
    	nextIndex++
    	if (nextIndex < eventMap.size() ) {       	
        	if (item.Status == eventMap[nextIndex].Status) {
                removeList = removeList << nextIndex
            }
        }
    }
    removeList.reverseEach {
    	eventMap.remove(it)
    }
    log.trace "The eventmap size is ${eventMap.size()}"
    
// Create the usage Map, only ON is needed, the remainder is OFF
    def tz = location.timeZone as TimeZone
    def date = new Date().format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone(tz.ID))
    def nowDate = new Date().parse('yyyy-MM-dd HH', "${date}")

	nextIndex = 0
    long secondsDuration = 0
    long secondsInitial = 0
    long secondsRemaining = 0
    long secondsLeftInHour = 3600
    long startTime
    long stopTime
    def startDate
    def stopDate
    def eventHour = 48

    eventMap.eachWithIndex { item, index ->
    	nextIndex++
		secondsRemaining = 0
        
        if (index == 0) startDate = state.usageMap[48].startHour
        else startDate = new Date().parse('yyyy-MM-dd HH:mm:ss', "${item.Date}")   
        
    	if (nextIndex < eventMap.size() ) {            
          	stopDate = new Date().parse('yyyy-MM-dd HH:mm:ss', "${eventMap[nextIndex].Date}")
        }
        else {
        	stopDate = nowDate
        }

		startTime = startDate.getTime()         
        stopTime = stopDate.getTime()
        secondsDuration = Math.abs(stopTime-startTime)/1000  // seconds                        
        log.info "${index} : ${item.Status} for ${secondsDuration} seconds, start ${startDate}, stop ${stopDate}, initial ${secondsInitial}"
		
        if (secondsInitial > 0 && secondsInitial <= secondsLeftInHour) {
        	eventHour = eventHour-1
            if (item.Status == "Off") state.usageMap[eventHour].On = secondsInitial // this is remainder of ON status, but current status is off
            secondsLeftInHour = secondsLeftInHour - secondsInitial 
        }
        
        if (secondsDuration <= secondsLeftInHour) {
        	if (item.Status == "On") state.usageMap[eventHour].On = state.usageMap[eventHour].On + secondsDuration
            secondsLeftInHour = secondsLeftInHour - secondsDuration 
        }
        else {
        	if (item.Status == "On") state.usageMap[eventHour].On = state.usageMap[eventHour].On + secondsLeftInHour
            secondsLeftInHour = 3600
            secondsRemaining = secondsDuration - secondsLeftInHour

            for (secondsRemaining; secondsRemaining >= 3600; secondsRemaining=secondsRemaining-3600) {
                eventHour = eventHour-1 
                try {
                    if (item.Status == "On" && secondsRemaining >= 3600) state.usageMap[eventHour].On = 3600
                }
                catch (e) {
                    log.error eventHour
                }
            }
            if (secondsRemaining > 0 && eventMap.size() == 1) {            
            	eventHour = eventHour - 1
                state.usageMap[eventHour].On = secondsRemaining
            }
            
            secondsInitial = secondsRemaining
            secondsLeftInHour = 3600 - secondsRemaining
        }     
    }   
    
    state.chd = ""
    def seriesOn = ""
    def labels = "0:%7C"
    float On 
    float Off
    
    state.usageMap.each { key, item ->
		On = item.On / 60
 		seriesOn = seriesOn + "${On.toInteger()},"
        labels = labels + "${item.hourLabel}%7C"
    }
    seriesOn = seriesOn.substring(0, seriesOn.length() - 1)
    labels = labels.substring(0, labels.length() - 3)
    state.chd = seriesOn
    state.chxl = labels
    takeLightLog()
}

def take() {
	log.debug "Take()"
	def imageCharts = "https://image-charts.com/chart?"
	def params = [uri: "${imageCharts}chs=720x480&chd=${state.chd}&cht=bvg&chds=a&chxt=x,y&chxl=${state.chxl}&chts=0000FF,20&chco=${state.chco}&chtt=${state.chtt}"]
    
    if (state.imgCount == null) state.imgCount = 0
 
    try {
        httpGet(params) { response ->
        	
            if (response.status == 200 && response.headers.'Content-Type'.contains("image/png")) {
                def imageBytes = response.data
                if (imageBytes) {
                    state.imgCount = state.imgCount + 1
                    def name = "PowerUsage$state.imgCount"

                    // the response data is already a ByteArrayInputStream, no need to convert
                    try {
                        storeImage(name, imageBytes, "image/png")
                    } catch (e) {
                        log.error "error storing image: $e"
                    }
                }
            }
        else log.error "wrong format of content"
        }
    } catch (err) {
        log.error ("Error making request: $err")
    }
}

def takeLightLog() {
	log.debug "TakeLightLog()"
	def imageCharts = "https://image-charts.com/chart?"
//	def params = [uri: "${imageCharts}chs=900x480&chd=t:${state.chd}&cht=lc&chls=5&chds=a&chxt=x,y&chf=c,s,EFEFEF&chxr=0,1,48,4&chts=0000FF,20&chco=00a0dc&chtt=48+hours+of+LightLog"]
	def params = [uri: "${imageCharts}chs=900x480&chd=t:${state.chd}&chxl=${state.chxl}&cht=bvg&chls=5&chds=a&chxt=x,y&chts=0000FF,20&chco=00a0dc&chtt=48+hours+of+LightLog"]
    
    if (state.imgCount == null) state.imgCount = 0
 	log.info params
    try {
        httpGet(params) { response ->
        	
            if (response.status == 200 && response.headers.'Content-Type'.contains("image/png")) {
                def imageBytes = response.data
                if (imageBytes) {
                    state.imgCount = state.imgCount + 1
                    def name = "LightLog$state.imgCount"

                    // the response data is already a ByteArrayInputStream, no need to convert
                    try {
                        storeImage(name, imageBytes, "image/png")
                    } catch (e) {
                        log.error "error storing image: $e"
                    }
                }
            }
        else log.error "wrong format of content"
        }
    } catch (err) {
        log.error ("Error making request: $err")
    }
}

private def initUsageMap() {
	// init a map with the previous 48 hours

    def tz = location.timeZone as TimeZone
    def date = new Date().format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone(tz.ID))
    def start = new Date().parse('yyyy-MM-dd HH', "${date}")
    int hour
    Calendar calendar = GregorianCalendar.getInstance(TimeZone.getTimeZone(tz.ID)); // creates a new calendar instance
    state.usageMap = [:]
      
    use (TimeCategory) {
    	def i
    	for (i=1 ; i<49 ; i++) {
            start = start-1.hour
			calendar.setTime(start);   // assigns calendar to given date 
			hour = calendar.get(Calendar.HOUR_OF_DAY); // gets hour in 24h format
            state.usageMap[i] = [startHour: start, On: 0, hourLabel: hour]
        }
    } 
}

private def getResponse(evt) {

    if (evt instanceof physicalgraph.device.HubResponse) {
        return evt.json
    }
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
        sendEvent(name: "powerToday", value: "noPower")
    }
    else {
    	log.error "You cannot use this DTH without the related SmartAPP Domoticz Server, the device needs to be a child of this App"
        sendEvent(name: "switch", value: "Error", descriptionText: "$device.displayName You cannot use this DTH without the related SmartAPP Domoticz Server", isStateChange: true)
    }
}