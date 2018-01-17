/*
 *  Domoticz (server)
 *
 *  Copyright 2018 Martin Verbeek
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
 *	
	V4.05	check for null on result in onLocationEvtForEveryThing
    V4.06	[updateDeviceList] remove obsolete childs, ones that are unused in Domoticz
 	V4.07	Adding linkage to power usage devices
    V4.08	checking for usage types returned fromDZ that should n0t be added
    V5.00	Added power reporting device and collecting of usage
    V5.01	Remove NefitEasy specific support and add General Thermostat
    V5.02	Added power today and total to powerToday event -> domoticzOnOff
    V5.04	Automatic settings of notifications for HTTP on/off in Domoticz
    V5.05	use lowcase on/off for ActionTile use.
    V5.06	changed the way sensor counts are updated, it will be reported with customer notify from DZ instead op pulled by ST
    		inspect HaveTimeout property, false == Online health, true == Offline health
 */

import groovy.json.*
import groovy.time.*
import java.Math.*

private def cleanUpNeeded() {return true}

private def runningVersion() {"5.06"}

private def textVersion() { return "Version ${runningVersion()}"}

definition(
    name: "Domoticz Server",
    namespace: "verbem",
    author: "Martin Verbeek",
    description: "Connects to local Domoticz server and define Domoticz devices in ST",
    category: "My Apps",
    singleInstance: false,
    oauth: true,
    iconUrl: "http://www.thermosmart.nl/wp-content/uploads/2015/09/domoticz-450x450.png",
    iconX2Url: "http://www.thermosmart.nl/wp-content/uploads/2015/09/domoticz-450x450.png",
    iconX3Url: "http://www.thermosmart.nl/wp-content/uploads/2015/09/domoticz-450x450.png"
)
/*-----------------------------------------------------------------------------------------*/
/*		PREFERENCES      
/*-----------------------------------------------------------------------------------------*/
preferences {
    page name:"setupInit"
    page name:"setupMenu"
    page name:"setupDomoticz"
    page name:"setupListDevices"
    page name:"setupDeviceRequest"
    page name:"setupAddDevices"
    page name:"setupRefreshToken"
    page name:"setupCompositeSensors"

}
/*-----------------------------------------------------------------------------------------*/
/*		Mappings for REST ENDPOINT to communicate events from Domoticz      
/*-----------------------------------------------------------------------------------------*/
mappings {
    path("/EventDomoticz") {
        action: [ GET: "eventDomoticz" ]
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		SET Up INIT
/*-----------------------------------------------------------------------------------------*/
private def setupInit() {
    TRACE("[setupInit]")
    unsubscribe()
    subscribe(location, null, onLocation, [filterEvents:true])

    if (!state.accessToken) {
        initRestApi()
    }
    if (state.setup) {
        // already initialized, go to setup menu
        return setupMenu()
    }
    /* 		Initialize app state and show welcome page */
    state.setup = [:]
    state.setup.installed = false
    state.devices = [:]
    
    return setupWelcome()
}
/*-----------------------------------------------------------------------------------------*/
/*		SET Up Welcome PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupWelcome() {
    TRACE("[setupWelcome]")

    def textPara1 =
        "Domoticz Server allows you to integrate Domoticz defined devices into " +
        "SmartThings. Support for blinds, scenes, groups, on/off/rgb/dimmer, contact, motion, smoke detector devices now. " +
        "Please note that it requires a server running " +
        "Domoticz. This must be installed on the local network and accessible from " +
        "the SmartThings hub.\n\n"
     

    def textPara2 = "${app.name}. ${textVersion()}\n${textCopyright()}"

    def textPara3 =
        "Please read the License Agreement below. By tapping the 'Next' " +
        "button at the top of the screen, you agree and accept the terms " +
        "and conditions of the License Agreement."

    def textLicense =
        "This program is free software: you can redistribute it and/or " +
        "modify it under the terms of the GNU General Public License as " +
        "published by the Free Software Foundation, either version 3 of " +
        "the License, or (at your option) any later version.\n\n" +
        "This program is distributed in the hope that it will be useful, " +
        "but WITHOUT ANY WARRANTY; without even the implied warranty of " +
        "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU " +
        "General Public License for more details.\n\n" +
        "You should have received a copy of the GNU General Public License " +
        "along with this program. If not, see <http://www.gnu.org/licenses/>."

    def pageProperties = [
        name        : "setupInit",
        title       : "Welcome!",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : state.setup.installed
    ]

    return dynamicPage(pageProperties) {
        section {
            paragraph textPara1
            paragraph textPara3
        }
        section("License") {
            paragraph textLicense
        }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		SET Up Menu PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupMenu() {
    TRACE("[setupMenu]")
    if (state.accessToken) {
		state.urlCustomActionHttp = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"
    }

    if (!settings.containsKey('domoticzIpAddress')) {
        return setupDomoticz()
    }

    def pageProperties = [
        name        : "setupMenu",
        title       : "Setup Menu",
        nextPage    : null,
        install     : true,
        uninstall   : state.setup.installed
    ]

    state.setup.deviceType = null
	//state.domoticzRefresh = false

	return dynamicPage(pageProperties) {
        section {
            href "setupDomoticz", title:"Configure Domoticz Server", description:"Tap to open"
            href "setupDeviceRequest", title:"Add all selected Devicetypes or those in selected Rooms", description:"Tap to open"
            if (state.devices.size() > 0) {
                href "setupListDevices", title:"List Installed Devices", description:"Tap to open"
            }
            if (state?.listSensors?.size() > 0) {
            	href "setupCompositeSensors", title:"Create Composite Devices", description:"Tap to open"	
            }
            href "setupRefreshToken", title:"Revoke/Recreate Access Token", description:"Tap to open"
        	}
        section([title:"Options", mobileOnly:true]) {
            label title:"Assign a name", required:false
        	}
        section("HTTP Custom Action URL for Domoticz") {
            paragraph "${state.urlCustomActionHttp}"
        	}        
        section("About") {
            paragraph "${app.name}. ${textVersion()}\n${textCopyright()}"
        	}
    	}
}

/*-----------------------------------------------------------------------------------------*/
/*		SET Up Configure Composite Sensors PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupCompositeSensors() {
    TRACE("[setupCompositeSensors]")
    
    def pageProperties = [
        name        : "setupCompositeSensors",
        title       : "Configure Composite Sensors",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]
    
    def inputReportPower = [
        name        : "domoticzReportPower",
        type        : "bool",
        title       : "create Power Report device(s)",
        defaultValue: false
    ]
    
    return dynamicPage(pageProperties) {
		section {
        	input inputReportPower
        }
        section {	
      		state.listSensors.sort().each { key, item ->
        	if (item.type == "domoticzSensor" || item.type == "domoticzMotion" || item.type == "domoticzThermostat" ) {
                def iMap = item as Map
                paragraph "Extend ${iMap.type.toUpperCase()}\n${iMap.name}"
                
                if (state.optionsPower) {input "idxPower[${key}]", "enum", title:"Add power usage?", options: state.optionsPower, required: false}
                
                if (state.optionsLux && iMap.type != "domoticzThermostat") {input "idxIlluminance[${key}]", "enum", title:"Add Lux measurement?", options: state.optionsLux, required: false}
                
                if (state.optionsTemperature && iMap.type != "domoticzSensor") input "idxTemperature[${key}]", "enum", title:"Add Temperature measurement?",options: state.optionsTemperature , required: false
                
                if (state.optionsMotion && (iMap.type != "domoticzMotion" || iMap.type != "domoticzThermostat")) input "idxMotion[${key}]", "enum", title:"Add motion detection?", options: state.optionsMotion, required: false
                
                if (state.optionsModes && iMap.type == "domoticzThermostat") {
                	input "idxFanMode[${key}]", "enum", title:"Add Thermostat Fan modes?", options: state.optionsModes, required: false
                	input "idxMode[${key}]", "enum", title:"Add Thermostat Operating modes?", options: state.optionsModes, required: false
                }
        	}
        }
      }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		SET Up Configure Domoticz PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupDomoticz() {
    TRACE("[setupDomoticz]")

	if (settings.containsKey('domoticzIpAddress')) {
    	state.networkId = settings.domoticzIpAddress + ":" + settings.domoticzTcpPort
    	socketSend([request: "roomplans"])
		pause 5
    	}
    
    def textPara1 =
        "Enter IP address and TCP port of your Domoticz Server, then tap " +
        "Next to continue."

    def inputIpAddress = [
        name        : "domoticzIpAddress",
        submitOnChange : true,
        type        : "string",
        title       : "Local Domoticz IP Address",
        defaultValue: "0.0.0.0"
    ]

    def inputTcpPort = [
        name        : "domoticzTcpPort",
        type        : "number",
        title       : "Local Domoticz TCP Port",
        defaultValue: "8080"
    ]

    def inputDzTypes = [
        name        : "domoticzTypes",
        type        : "enum",
        title       : "Devicetypes you want to add",
        options	    : ["Contact Sensors", "Dusk Sensors", "Motion Sensors", "On/Off/Dimmers/RGB", "Smoke Detectors", "Thermostats", "(Virtual) Sensors", "Window Coverings"],
        multiple	: true
    ]
    
    def inputRoomPlans = [
        name        : "domoticzRoomPlans",
        submitOnChange : true,
        type        : "bool",
        title       : "Support Room Plans from Domoticz?",
        defaultValue: false
    ]
    
    def inputPlans = [
        name        : "domoticzPlans",
        type        : "enum",
        title       : "Select the rooms",
        options	    : state.listPlans,
        multiple	: true
    ]

    def inputGroup = [
        name        : "domoticzGroup",
        type        : "bool",
        title       : "Add Groups from Domoticz?",
        defaultValue: false
    ]
    
    def inputScene = [
        name        : "domoticzScene",
        type        : "bool",
        title       : "Add Scenes from Domoticz?",
        defaultValue: false
    ]
    
    def inputTrace = [
        name        : "domoticzTrace",
        type        : "bool",
        title       : "Debug trace output in IDE log",
        defaultValue: true
    ]
      
    def pageProperties = [
        name        : "setupDomoticz",
        title       : "Configure Domoticz Server",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]

    return dynamicPage(pageProperties) {
      section {
            input inputIpAddress
            input inputTcpPort
            input inputDzTypes
            if (settings.containsKey('domoticzIpAddress') && settings?.domoticzIpAddress != "0.0.0.0") input inputRoomPlans
            if (domoticzRoomPlans && settings.containsKey('domoticzIpAddress')) input inputPlans
            input inputGroup
            input inputScene
            input inputTrace
        	}
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		SET Up Add Domoticz Devices PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupDeviceRequest() {
    TRACE("[setupDeviceRequest]")

    def textHelp =
        "Add all Domoticz devices that have the following definition: \n\n" +
        "Type ${domoticzTypes}.\n\n" 
    if (domoticzRoomPlans) 	textHelp = textHelp + "Devices in Rooms ${domoticzPlans} with the above types \n\n"
    if (domoticzScene) 		textHelp = textHelp + "Scenes will be added. \n\n"
    if (domoticzGroup) 		textHelp = textHelp + "Groups will be added. \n\n"

	textHelp = textHelp +  "Tap Next to continue."
          
    def pageProperties = [
        name        : "setupDeviceRequest",
        title       : "Add Domoticz Devices?",
        nextPage    : "setupAddDevices",
        install     : false,
        uninstall   : false
    ]

    return dynamicPage(pageProperties) {
        section {
            paragraph textHelp
        }
    }
}
/*-----------------------------------------------------------------------------------------*/
/*		Execute Domoticz LIST devices from the server
/*-----------------------------------------------------------------------------------------*/
private def setupAddDevices() {
    TRACE("[setupAddDevices]")

	updateDeviceList()

    def pageProperties = [
        name        : "setupAddDevices",
        title       : "Adding Devices",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]

    
	state.listOfRoomPlanDevices = []
    if (domoticzRoomPlans)
    	{
        settings.domoticzPlans.each { v ->       	
        	state.statusPlansRsp.each {
            if (v == it.Name) {
            	socketSend([request: "roomplan", idx: idx])
                pause 10
                }
        	}
          }
        }

	socketList()
    pause 10
    socketSend([request : "scenes"])
	pause 10

    return dynamicPage(pageProperties) {
        section {
            paragraph "Requested Domoticz Devices have been added to SmartThings"
            paragraph "Tap Next to continue."
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		When having problems accessing DZ then execute refresh Token
/*-----------------------------------------------------------------------------------------*/
private def setupRefreshToken() {
    TRACE("[setupRefreshToken]")
	
    revokeAccessToken()
    def token = createAccessToken()
    
    state.urlCustomActionHttp = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"

    def pageProperties = [
        name        : "setupRefreshToken",
        title       : "Refresh the access Token",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]

    return dynamicPage(pageProperties) {
        section {
            paragraph "The Access Token has been refreshed"
            paragraph "${state.urlCustomActionHttp}"
            paragraph "Tap Next to continue."
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		List the child devices in the SMARTAPP
/*-----------------------------------------------------------------------------------------*/
private def setupListDevices() {
    TRACE("[setupListDevices]")
	refreshDevicesFromDomoticz()
    def textNoDevices =
        "You have not configured any Domoticz devices yet. Tap Next to continue."

    def pageProperties = [
        name        : "setupListDevices",
        title       : "Connected Devices idx - name",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]

    if (state.devices.size() == 0) {
        return dynamicPage(pageProperties) {
            section {
                paragraph textNoDevices
            }
        }
    }

    def switches = getDeviceListAsText('switch')
    def sensors = getDeviceListAsText('sensor')
    def thermostats = getDeviceListAsText('thermostat')
    
    return dynamicPage(pageProperties) {
        section("Switch types") {paragraph switches}     
        section("Sensors") {paragraph sensors}
        section("Thermostats") {paragraph thermostats}
    }
}


def installed() {
    TRACE("[installed]")
    initialize()
}

def updated() {
    TRACE("[updated]")
    initialize()
}

def uninstalled() {
    TRACE("[uninstalled]")

    // delete all child devices
    def devices = getChildDevices()
    devices?.each {
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (e) {
            log.error "[uninstalled] Cannot delete device ${it.deviceNetworkId}. Error: ${e}"
        }
    }
}

private def initialize() {
    TRACE("[Initialize] ${app.name}. ${textVersion()}. ${textCopyright()}")
    STATE()

    notifyNewVersion()
    schedule("2015-01-09T12:00:00.000-0600", notifyNewVersion)  

	// Subscribe to location events with filter disabled
    TRACE ("[Initialize] Subcribe to Location")
    unsubscribe()
    
    if (state.accessToken) {
        state.urlCustomActionHttp = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"
	}
    
	state.setStatusrsp = false
    state.setup.installed = true
    state.networkId = settings.domoticzIpAddress + ":" + settings.domoticzTcpPort
    updateDeviceList()
        
    state.alive = true
    state.aliveAgain = true
    state.devicesOffline = false
    state.scheduleCycle = 59i
	   
    unschedule()

	addReportDevices()
        
    runEvery1Minute(aliveChecker)    

	state.optionsLux = [:]
    state.optionsMotion = [:]
    state.optionsTemperature = [:]
    state.optionsCarbon1 = [:]
    state.optionsCarbon2 = [:]
    state.optionsPower = [:]
    state.optionsModes = [:]

    scheduledListSensorOptions()
      
    if 	(cleanUpNeeded() == true) {
        if (state?.runUpdateRoutine != runningVersion() ) runUpdateRoutine()
        state.runUpdateRoutine = runningVersion()
    }
    
    sendThermostatModes()
}

private def runUpdateRoutine() {
    
	log.info "UPDATE ROUTINE!!!"
	state.devices.each {key, item ->
		if (item.type == "switch") {
        	log.info "Clear Notifications for ${item.type} ${item.dni} ${item.idx}"
        	socketSend([request : "ClearNotification", idx : item.idx])
            pause 2
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Handle the location events that are being triggered from sendHubCommand response
/*		Most of the sendHubCommands have specific callback handlers
/*-----------------------------------------------------------------------------------------*/
void onLocation(evt) {

	if (evt instanceof physicalgraph.device.HubResponse == false) { 
        return
    }
    
    def hMap = stringToMap(evt.description)
    def evtNetworkId = null  
	
    if (hMap?.ip == null) return
    
    evtNetworkId = convertHexToIP(hMap.ip) + ":" + convertHexToInt(hMap.port)
	  
    // response from Domoticz Server IP:Port adress   
	if (evtNetworkId == state.networkId) { // response from Domoticz

        if (!evt?.json?.result) return

        if (evt.json.title == "Devices") onLocationEvtForDevices(evt.json)

    }
}

void refreshUtilityCounts() {

	def listIdx = [:]
	    
	idxSettings().each { k, v ->
    
        	def idx = k.tokenize('[')[1]
            idx = idx.tokenize(']')[0].toString()
            if (k.contains("idxPower")) {
                state.devices[idx].idxPower = v
            }
            if (k.contains("idxTemperature")) {
                state.devices[idx].idxTemperature = v
            }
            if (k.contains("idxMotion")) {
                state.devices[idx].idxMotion = v
            }
            if (k.contains("idxIlluminance")) {
                state.devices[idx].idxIlluminance = v
            }
            if (k.contains("idxFanMode")) {
                state.devices[idx].idxFanMode = v
            }
            if (k.contains("idxMode")) {
                state.devices[idx].idxMode = v
            }
    }
}

void scheduledListSensorOptions() {

	socketSend([request : "OptionUtility"])
    socketSend([request : "OptionTemperature"])
    socketSend([request : "OptionDevices"])
}

void scheduledPowerReport() {
   
	state.reportPowerDay = [:]		// last 24 hours graph
    state.reportPowerMonth = [:]	// last 31 days graph
    state.reportPowerYear = [:]		// last 52 weeks graph
    pause 3

	state.devices.each { key, item ->
    	if (item?.idxPower != null) { 
            socketSend([request : "counters", idx : item.idxPower, range : "day"])
            socketSend([request : "counters", idx : item.idxPower, range : "month"])
            socketSend([request : "counters", idx : item.idxPower, range : "year"])
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Update the usage info on composite DZ devices that report on a utility
/*		kWh, Lux etc...
/*-----------------------------------------------------------------------------------------*/
void onLocationEvtForUCount(evt) {
    def response = getResponse(evt)

	response.result.each { utility ->
    
    	if (utility?.SubType == "kWh") {

			def stateDevice = state.devices.find {key, item -> 
		    	item.deviceId == utility.ID
    		}
            
            if (!stateDevice) { 		//XIAOMI plugs?? plug ID is xxAABBCC, usage idx is AABBCCxx
            	def ID = "${utility.ID.substring(6)}${utility.ID.substring(0,6)}"  
                stateDevice = state.devices.find {key, item -> 
                    item.deviceId == ID
                }
            }

			if (stateDevice) {
                stateDevice = stateDevice.toString().split("=")[0]
                def dni = state.devices[stateDevice].dni
                getChildDevice(dni).sendEvent(name:"power", value: Float.parseFloat(utility.Usage.split(" ")[0]).round(1))
                getChildDevice(dni).sendEvent(name:"powerToday", value: "Now  :${utility.Usage}\nToday:${utility.CounterToday} Total:${utility.Data}")
            }
			else {
                stateDevice = state.devices.find {key, item -> 
                    item.idxPower == utility.idx
                }
                
                if (stateDevice) {
                    stateDevice = stateDevice.toString().split("=")[0]
                    def dni = state.devices[stateDevice].dni
                    getChildDevice(dni).sendEvent(name:"power", value: Float.parseFloat(utility.Usage.split(" ")[0]).round(1))
                    getChildDevice(dni).sendEvent(name:"powerToday", value: "Now  :${utility.Usage}\nToday:${utility.CounterToday} Total:${utility.Data}")
                }
                else {
                    TRACE("[onLocationEvtForUCount] Not found kWh ${utility.ID} ${utility.idx}")
            	}            
            }
        }
        
    	if (utility?.SubType == "Lux") {
			def stateDevice = state.devices.find {key, item -> 
		    	item.idxIlluminance == utility.idx
    		}

			if (stateDevice) {
                stateDevice = stateDevice.toString().split("=")[0]
                def dni = state.devices[stateDevice].dni
                getChildDevice(dni).sendEvent(name:"illuminance", value:"${utility.Data.split()[0]}")

            }
        }
        
    	if (utility?.SwitchTypeVal == 8) {
			def stateDevice = state.devices.find {key, item -> 
		    	item.idxMotion == utility.idx
    		}

			if (stateDevice) {
                stateDevice = stateDevice.toString().split("=")[0]
                def dni = state.devices[stateDevice].dni
                def motion = "inactive"
                if (utility.status.toUpperCase() == "ON") motion = "active"
                getChildDevice(dni).sendEvent(name:"motion", value:"${motion}")
            }
        }
        
    	if (utility?.Temp) {
			def stateDevice = state.devices.find {key, item -> 
		    	item.idxTemperature == utility.idx
    		}

			if (stateDevice) {
                stateDevice = stateDevice.toString().split("=")[0]
                def dni = state.devices[stateDevice].dni
                float t = utility.Temp
                t = t.round(1)
                getChildDevice(dni).sendEvent(name:"temperature", value:"${t}")
            }
        }
	}    
}

/*-----------------------------------------------------------------------------------------*/
/*		Build the idx list for Devices that are part of the selected room plans
/*-----------------------------------------------------------------------------------------*/
void onLocationEvtForRoom(evt) {
	def response = getResponse(evt)

	if (response.result == null) {
        TRACE("[onLocationEvtForRoom] Domoticz response ${response}")
        return
    }

    TRACE("[onLocationEvtForRoom] Domoticz response with Title : ${response.title} number of items returned ${response.result.size()}") 

    response.result.each {
		if (it?.SubType != "kWh") {
            TRACE("[onLocationEvtForRoom] Device ${it.Name} with idx ${it.devidx}")
            state.listOfRoomPlanDevices.add(it.devidx)
            pause 1
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Get Room Plans defined into Selectables for setupDomoticz
/*-----------------------------------------------------------------------------------------*/
void onLocationEvtForPlans(evt) {
	def response = getResponse(evt)
    state.statusPlansRsp = response.result

    if (response.result == null) return

    TRACE("[onLocationEvtForPlans] Domoticz response with Title : ${response.title} number of items returned ${response.result.size()}") 

    state.listPlans = []
    pause 1

    response.result.each {
        TRACE("[onLocationEvtForPlans] ${it.Devices} devices in room plan ${it.Name} with idx ${it.idx}")
        state.listPlans.add(it.Name)
        pause 1
    }

    state.listPlans.sort()
    pause 1
}

/*-----------------------------------------------------------------------------------------*/
/*		proces for adding and updating status for Scenes and Groups
/*-----------------------------------------------------------------------------------------*/
void onLocationEvtForScenes(evt) {
    def response = getResponse(evt)
    def groupIdx = response.result.collect {it.idx}.sort()
    state.statusGrpRsp = groupIdx
    pause 2

	if (response.result == null) return

    TRACE("[onLocationEvtForScenes] Domoticz response with Title : ${response.title} number of items returned ${response.result.size()}") 

	response.result.each {
        TRACE("[onLocationEvtForScenes] ${it.Type} ${it.Name} ${it.Status} ${it.Type}")
        switch (it.Type) {
        case "Scene":
            if (domoticzScene) {
                addSwitch(it.idx, "domoticzScene", it.Name, it.Status, it.Type, 0)
            }
            break;
        case "Group":
            if (domoticzGroup) {
                addSwitch(it.idx, "domoticzScene", it.Name, it.Status, it.Type, 0)
            }
            break;
        }    
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Proces for adding and updating status of devices
/*-----------------------------------------------------------------------------------------*/
private def onLocationEvtForDevices(statusrsp) {

	def compareTypeVal
    def SubType
	if (!state.listSensors) state.listSensors = [:]

	statusrsp.result.each { 
        compareTypeVal = it?.SwitchTypeVal
        
       	// handle SwitchTypeVal Exceptions
        if (it?.Temp) compareTypeVal = 99
        if (it?.SetPoint) compareTypeVal = 98
        if (compareTypeVal == null) compareTypeVal = 100
        
        switch (compareTypeVal) 
        {
            case [3, 13, 6, 16]:		//	Window Coverings, 6 & 16 are inverted
	            if (domoticzTypes.contains('Window Coverings')) addSwitch(it.idx, "domoticzBlinds", it.Name, it.Status, it.Type, it)
	            break
            case [0, 7]:		// 	Lamps OnOff, Dimmers and RGB
            	SubType = it?.SubType
                if (domoticzTypes.contains('On/Off/Dimmers/RGB') && SubType != "kWh") addSwitch(it.idx, "domoticzOnOff", it.Name, it.Status, it.Type, it)
                break
            case 2:				//	Contact 
                if (domoticzTypes.contains('Contact Sensors')) addSwitch(it.idx, "domoticzContact", it.Name, it.Status, it.Type, it)
                state.listSensors[it.idx] = [name: it.Name, idx: it.idx, type: "domoticzContact"]
                break
            case 5:				//	Smoke Detector
                if (domoticzTypes.contains('Smoke Detectors')) addSwitch(it.idx, "domoticzSmokeDetector", it.Name, it.Status, it.Type, it)
                state.listSensors[it.idx] = [name: it.Name, idx: it.idx, type: "domoticzSmokeDetector"]
                break
            case 8:				//	Motion Sensors
                if (domoticzTypes.contains('Motion Sensors')) addSwitch(it.idx, "domoticzMotion", it.Name, it.Status, it.Type, it)
                state.listSensors[it.idx] = [name: it.Name, idx: it.idx, type: "domoticzMotion"]
                break
            case 12:			//	Dusk Sensors/Switch
                if (domoticzTypes.contains('Dusk Sensors')) addSwitch(it.idx, "domoticzDuskSensor", it.Name, it.Status, it.Type, it)
                state.listSensors[it.idx] = [name: it.Name, idx: it.idx, type: "domoticzDuskSensor"]
                break
            case 18:			//	Selector Switch
                if (domoticzTypes.contains("On/Off/Dimmers/RGB")) addSwitch(it.idx, "domoticzSelector", it.Name, it.Status, it.SwitchType, it)
                break
            case 98:			//	Thermostats
                if (domoticzTypes.contains("Thermostats")) addSwitch(it.idx, "domoticzThermostat", it.Name, it.SetPoint, it.Type, it)
                state.listSensors[it.idx] = [name: it.Name, idx: it.idx, type: "domoticzThermostat"]
               break
            case 99:			//	Sensors
                if (domoticzTypes.contains("(Virtual) Sensors")) addSwitch(it.idx, "domoticzSensor", it.Name, "Active", it.Type, it)
                state.listSensors[it.idx] = [name: it.Name, idx: it.idx, type: "domoticzSensor"]
                break
           	case 100:
            	break
            default:
                TRACE("[onLocationEvtForDevices] non handled SwitchTypeVal ${compareTypeVal} ${it}")
            break
        }	
    }            
}

def onLocationEvtForEveryThing(evt) {

    def response = getResponse(evt)
    def kwh = 0f
    def watt = 0f
    
	if (response?.result == null) return

	//TRACE("[onLocationEvtForEveryThing] Domoticz response with Title : ${response.title}, number of items returned ${response.result.size()}, state occupancy is ${state.toString().length()}")

    response.result.each {
		    	
        if (it?.SubType == "Lux") {
        	state.optionsLux[it.idx] = "${it.idx} : ${it.Name}"
            //if (it.Notifications == "false") socketSend([request : "SensorLuxNotification", idx : it.idx])
        }
        
        if (it?.SwitchTypeVal == 8) {
        	state.optionsMotion[it.idx] = "${it.idx} : ${it.Name}"
        }	
		//MODES for thermostatFanModes and thermostatModes
		if (it?.SwitchTypeVal == 18) {
        	state.optionsModes[it.idx] = "${it.idx} : ${it.Name}"
            //if (it.Notifications == "false") socketSend([request : "SensorNotification", idx : it.idx])
        }	
        
        if (it?.Temp) {
        	state.optionsTemperature[it.idx] = "${it.idx} : ${it.Name}"
            if (it.Notifications == "false") socketSend([request : "SensorTempNotification", idx : it.idx])
        }
        
        if (it?.SwitchTypeVal == 5) {
        	state.optionsCarbon1[it.idx] = "${it.idx} : ${it.Name}"
        	state.optionsCarbon2[it.idx] = "${it.idx} : ${it.Name}"
            //if (it.Notifications == "false") socketSend([request : "SensorNotification", idx : it.idx])
        }
        
        if (it?.SubType == "kWh") {	
        	state.optionsPower[it.idx] = "${it.idx} : ${it.Name}" 
            
            if (it.Notifications == "false") socketSend([request : "SensorKWHNotification", idx : it.idx])
            kwh = kwh + Float.parseFloat(it.Data.split(" ")[0])
            watt = watt + Float.parseFloat(it.Usage.split(" ")[0])
            //add idxPower to real device by matching the ID
			def ID = it?.ID
			def stateDevice = state.devices.find {key, item -> 
		    	item.deviceId == ID
    		}
            
            if (!stateDevice) { // XIAOMI try
            	ID = "${it?.ID.substring(6)}${it?.ID.substring(0,6)}"                
                stateDevice = state.devices.find {key, item -> 
                    item.deviceId == ID
                }
            }
            
            def IDX = it.idx
            if (stateDevice) {
            	if (state.devices[stateDevice.key]?.idxPower != IDX) {
            		state.devices[stateDevice.key].idxPower = IDX
                	pause 2
                }
            }
		}
    }
    
	// report to Device that report Power totals   
    if (kwh > 0 && state.devReportPower != null) {
    	def devReportPower = getChildDevice(state.devReportPower)
        if (devReportPower) {
            devReportPower.sendEvent(name:"powerTotal", value: kwh.round(3))
            devReportPower.sendEvent(name:"power", value: watt.round())
        }
   	}
}

def onLocationForCounters(evt) {
    def response = getResponse(evt)

	if (response.result == null) return
    
    def hour
    def day
    def week
    def date

    switch (response.title.split()[2]) {
        case "day":
			def dayList = state.reportPowerDay
            
            response.result.each { p ->
            	hour = p.d
                if (dayList[hour]) dayList[hour] = dayList[hour] + p.v.toFloat() else dayList[hour] = p.v.toFloat()
            }
            state.reportPowerDay = dayList
	        break;
            
        case "month":
			def monthList = state.reportPowerMonth
            response.result.each { p ->
            	day = p.d
                if (monthList[day]) monthList[day] = monthList[day] + p.v.toFloat() else monthList[day] = p.v.toFloat()
                monthList[day] = monthList[day].round(3)
            }
            state.reportPowerMonth = monthList
            break;
            
        case "year":
			def yearList = state.reportPowerYear
            response.result.each { p ->
                date = new Date().parse('yyyy-MM-dd', "${p.d}")
                week = "${date.getAt(Calendar.YEAR)}-${date.getAt(Calendar.WEEK_OF_YEAR)}"
                if (yearList[week]) yearList[week] = yearList[week] + p.v.toFloat() else yearList[week] = p.v.toFloat()
                yearList[week] = yearList[week].round(3)
            }
			state.reportPowerYear = yearList
            break;
    }    
}

def onLocationSettings(evt) {

    def response = getResponse(evt)
	if (response.HTTPURL == null) return

    def decoded = response.HTTPURL.decodeBase64()
    def httpURL = new String(decoded)
    TRACE("[onLocationSettings] ${httpURL}")
}

/*-----------------------------------------------------------------------------------------*/
/*		Execute the real add or status update of the child device
/*-----------------------------------------------------------------------------------------*/
private def addSwitch(addr, passedFile, passedName, passedStatus, passedType, passedDomoticzStatus) {

    def newdni = app.id + ":IDX:" + addr
	def switchTypeVal = ""
    def deviceType = ""
    def deviceId = ""
    def subType = ""

    def dev = getChildDevice(newdni)

 	if (passedDomoticzStatus instanceof java.util.Map) {
    	 
    	if (passedDomoticzStatus?.ID != null) {
        	deviceId = passedDomoticzStatus.ID
        }
        
        subType = passedDomoticzStatus?.SubType
        
    	if (passedDomoticzStatus?.SwitchTypeVal != null) {
        	switchTypeVal = passedDomoticzStatus.SwitchTypeVal
            deviceType = "switch"
        }
        else deviceType = "sensor"
        
		// offline/not accessible in DZ???
        if (passedDomoticzStatus?.HaveTimeout == false) {
            devOnline(dev)
        }
        else {
            log.error "[addSwitch] Device ${passedName} offline"
            devOffline(dev)
        }    
    }
    
    if (dev) {      
        TRACE("[addSwitch] Updating child device ${addr}, ${passedFile}, ${passedName}, ${passedStatus}, ${deviceId}")
        
 		if (!state.devices[addr]) {       
            state.devices[addr] = [
                    'dni' : newdni,
                    'ip' : settings.domoticzIpAddress,
                    'port' : settings.domoticzTcpPort,
                    'idx' : addr,
                    'type'  : deviceType,
                    'deviceType' : passedFile,
                    'subType' : passedType,
                    'deviceId' : deviceId,
                    'switchTypeVal' : switchTypeVal
                    ]
            pause 5
		}
        else if (state.devices[addr]?.deviceId == null) state.devices[addr]?.deviceId = deviceId
                        
        if (passedName != dev.name) {
        	dev.label = passedName
            dev.name = passedName
        }
 
    }
    else if ((state.listOfRoomPlanDevices?.contains(addr) && settings.domoticzRoomPlans == true) || settings.domoticzRoomPlans == false) {
        
        try {
            TRACE("[addSwitch] Creating child device ${addr}, ${passedFile}, ${passedName}, ${passedStatus}, ${passedDomoticzStatus}")
            dev = addChildDevice("verbem", passedFile, newdni, getHubID(), [name:passedName, label:passedName, completedSetup: true])
            
            state.devices[addr] = [
                'dni'   : newdni,
                'ip' : settings.domoticzIpAddress,
                'port' : settings.domoticzTcpPort,
                'idx' : addr,
                'type'  : deviceType,
                'deviceType' : passedFile,
                'subType' : passedType,
                'deviceId' : deviceId,
                'switchTypeVal' : switchTypeVal
            	]
			pause 5
        } 
        catch (e) { 
            log.error "[addSwitch] Cannot create child device. ${devParam} Error: ${e}" 
        }
    }
    else return
    
    if (passedDomoticzStatus instanceof java.util.Map) {        
        def attributeList = createAttributes(dev, passedDomoticzStatus, addr)
        generateEvent(dev, attributeList)

        if ((passedDomoticzStatus?.Notifications == false || passedDomoticzStatus?.Notifications == "false" ) && deviceType == "switch" && passedFile != "domoticzSelector") {
            socketSend([request : "Notification", idx : addr, type : 7, action : "on"])
            socketSend([request : "Notification", idx : addr, type : 16,, action : "off"])
        }
        if (passedFile == "domoticzSelector" && (passedDomoticzStatus?.Notifications == false || passedDomoticzStatus?.Notifications == "false" )) {
            socketSend([request : "Notification", idx : addr, type : 16, action : "off"])
            def levelNames = passedDomoticzStatus?.LevelNames.tokenize("|")
            def ix = 10
            def maxIx = levelNames.size() * 10
            for (ix=10; ix < maxIx; ix = ix+10) {
                socketSend([request : "Notification", idx : addr, type : 7, action : "on", value: ix])
            }
        }
    }
}

private def generateEvent (dev, Map attributeList) {

	attributeList.each { name, value ->
    	def v = value
    	if (name.toUpperCase() == "SWITCH") {
        	if (v instanceof String) {
                if (v.toUpperCase() == "OFF" ) v = "off"
                if (v.toUpperCase() == "ON") v = "on"
            }
        }

		if (name.toUpperCase() == "MOTION") { if (value.toUpperCase() == "ON") v = "active" else v = "inactive"}

    	if (name.toUpperCase() == "SMOKE") { 
        	if (value.toUpperCase() == "ON") v = "smoke"
        	if (value.toUpperCase() == "OFF") v = "clear"
        }

        dev.sendEvent(name:"${name}", value:"${v}")
    }        
}

/*-----------------------------------------------------------------------------------------*/
/*		Create a status-attribute list that will be passed to generateevent method of device
/*-----------------------------------------------------------------------------------------*/
private def createAttributes(domoticzDevice, domoticzStatus, addr) {

	if (domoticzStatus instanceof java.util.Map == false) {
       	TRACE("[createAttributes] ${domoticzDevice} ${domoticzDevice.getSupportedAttributes()} NOT PASSED A MAP : RETURNING")
        return [:]
        }
              
    def attributeList = [:]
    domoticzStatus.each { k, v ->
    	switch (k)
        {
        	case "BatteryLevel":
            	if (domoticzDevice.hasAttribute("battery")) if (v == 255) attributeList.put('battery',100) else attributeList.put('battery',v)
            	break;
            case "Level":
				if (domoticzStatus?.LevelInt > 0 && v == 0 && domoticzDevice.hasAttribute("level")) attributeList.put('level',domoticzStatus?.LevelInt)
                else if (domoticzDevice.hasAttribute("level")) attributeList.put('level', v)
                	
                if (domoticzStatus?.LevelNames) {
                	def ix = v / 10
                    def status = domoticzStatus?.LevelNames.tokenize('|')
                	attributeList.put('selectorState', status[ix.toInteger()])
                    attributeList.put('selector', domoticzStatus?.LevelNames)
                    //check for associated thermostats
                    domoticz_modeChange(addr, "Mode", status[ix.toInteger()])
                    domoticz_modeChange(addr, "FanMode", status[ix.toInteger()])
                }
            	break;
            case "Temp":
            	double vd = v               
				if (domoticzDevice.hasAttribute("temperature")) attributeList.put('temperature', vd.round(1))
            	break;
            case "SetPoint":
                //double sp= v
            	if (domoticzDevice.hasAttribute("thermostatSetpoint")) 	attributeList.put("thermostatSetpoint", v)
				if (domoticzDevice.hasAttribute("coolingSetpoint"))		attributeList.put("coolingSetpoint", v)
                if (domoticzDevice.hasAttribute("heatingSetpoint"))    	attributeList.put("heatingSetpoint", v)
                break
            case "Barometer":
				if (domoticzDevice.hasAttribute("pressure")) attributeList.put('pressure', v)
            	break;
            case "Humidity":
				if (domoticzDevice.hasAttribute("humidity")) attributeList.put('humidity', v)
            	break;
            case "SignalLevel":
				if (domoticzDevice.hasAttribute("rssi")) attributeList.put('rssi', v)
            	break;
            case "Status":
            	if (domoticzDevice.hasAttribute("motion")) attributeList.put('motion', v)
            	if (domoticzDevice.hasAttribute("contact")) attributeList.put('contact', v)
            	if (domoticzDevice.hasAttribute("smoke")) attributeList.put('smoke', v)
            	if (domoticzDevice.hasAttribute("switch")) {
                	if (v.contains("Level")) attributeList.put('switch', 'On') 
                    else attributeList.put('switch', v)
                }
            	break;
            case "Notifications":
				attributeList.put('NotificationsDefinedInDomoticz', v)
            	break;
            case "Type":
				if (v == "RFY") attributeList.put('somfySupported', true)
            	break;
       }    
    }
	return attributeList
}

private def sendThermostatModes() {
	def thermoDev
    def selectorDev
    def idxMode
    def tModes

	idxComponentDevices([type : "Mode"]).each { key, device ->
    	thermoDev = getChildDevice(device.dni)
        idxMode = device.idxMode
        
        if (idxMode) {
        	selectorDev = getChildDevice("${app.id}:IDX:${idxMode}")
            if (selectorDev) {
            	tModes = selectorDev.currentValue("selector").tokenize("|")
                thermoDev.sendEvent(name : "supportedThermostatModes", value : JsonOutput.toJson(tModes))               
            }
            else {
            	log.error "mode device not found ${app.id}:IDX:${idxMode}"
            }
        }
	}

}
private def addReportDevices() {

	if (settings.domoticzReportPower) {

        def passedName = "Power Reporting Device"
        def newdni = app.id + ":Power Reporting Device:" + 10000
		def dev = getChildDevice(newdni)
        
        if (!dev) {      
            try {
                    dev = addChildDevice("verbem", "domoticzPowerReport", newdni, getHubID(), [name:passedName, label:passedName, completedSetup:true])
                    pause 5
            } 
            catch (e) 
                {
                    log.error "[addReportDevices] Cannot create child device. ${newdni} Error: ${e}"
                    return 
           	}
        }
        state.devReportPower = newdni       
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Purge devices that were removed from Domoticz
/*-----------------------------------------------------------------------------------------*/
def updateDeviceList() {
return
    TRACE("[updateDeviceList]")
    def deletedDevices = new ArrayList()
    def findrspDevice
    def findrspGroup
    def inStatusrsp
    def idx10k
    def allChildren = getAllChildDevices()
    
	def temprspDevices = state.statusrsp
    pause 5
    def temprspGroups = state.statusGrpRsp
    pause 5
    def tempStateDevices = state.devices    
    pause 5
    
    if(temprspDevices) log.trace temprspDevices.size() + " Devices in response : " + temprspDevices
    
    if (temprspGroups) log.trace temprspGroups?.size() + " Groups in response : " + temprspGroups
    
    TRACE("${tempStateDevices?.size()} state Devices : ${tempStateDevices?.collect {it.value.idx as int}.sort()}")
       
    allChildren.each { child ->
    	
    	findrspDevice = temprspDevices.find {it == child.deviceNetworkId.split(":")[2] }
    	findrspGroup = temprspGroups.find {it == child.deviceNetworkId.split(":")[2] }
        idx10k = child.deviceNetworkId.split(":")[2]

        if (idx10k != "10000") {   // special devices that should not be deleted automatically have idx = 10000
            if (!findrspDevice && !findrspGroup) {
                TRACE("[updateDeviceList] NOT FOUND ${child.name} delete childDevice")
                try {
                	deleteChildDevice(child.deviceNetworkId)
                }
                catch (e) {
                	log.error "[updateDeviceList] ${e} during delete"
                }
             
            }
      	}
    }
    
    tempStateDevices.each { k,v ->
        inStatusrsp = false
        
        if (temprspDevices) findrspDevice = temprspDevices.find {it == k }
        if (tmprspGroups) findrspGroup = temprspGroups.find {it == k }

        if (findrspDevice || findrspGroup) inStatusrsp = true
        
        if (inStatusrsp == false ) {
            deletedDevices.add(k)
            try {
            	if (v.hasProperty("dni")){
                    TRACE("[updateDeviceList] Removing deleted device from state and from childDevices ${k}")
                    deleteChildDevice(v.dni)
                    }
            } 	catch (e) {
            	log.error "[updateDeviceList] Cannot delete device ${v.dni}. Error: ${e}"
            }
        }
    }
    
	deletedDevices.each { k ->
    	state.devices.remove(k)
    }
}

private def getDeviceListAsText(type) {
    String s = ""
    
    state.devices.sort().each { k,v ->
    	if (type == "thermostat") {
            if (v.deviceType == "domoticzThermostat") {
                def dev = getChildDevice(v.dni)           
                if (!dev) TRACE("[getDeviceListAsText] ${v.dni} NOT FOUND")
                s += "${k.padLeft(4)} - ${dev?.displayName} - ${v.deviceType}\n"
        	}
        }
        else {
            if (v.type == type) {
                def dev = getChildDevice(v.dni)           
                if (!dev) TRACE("[getDeviceListAsText] ${v.dni} NOT FOUND")
                s += "${k.padLeft(4)} - ${dev?.displayName} - ${v.deviceType}\n"
            }          	
        }    
    }

    return s
} 

private def TRACE(message) {
    if(domoticzTrace) {log.trace message}
}

private def STATE() {
	if(domoticzTrace) {
    	TRACE("state: ${state}")
    	TRACE("settings: ${settings}")
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		REGULAR DOMOTICZ COMMAND HANDLERS
/*-----------------------------------------------------------------------------------------*/
def domoticz_poll(nid) {
    socketSend([request : "status", idx : nid])
}

def domoticz_scenepoll(nid) {
	socketSend([request : "scenes", idx : nid])
}

def domoticz_off(nid) {
	socketSend([request : "off", idx : nid])
}

def domoticz_sceneoff(nid) {
    // find out if it is a scene or a group, scenes do only ON commands
    if (state.devices[nid].subType == "Scene") 
		socketSend([request : "sceneon", idx : nid])
    else 
		socketSend([request : "sceneoff", idx : nid])
}

def domoticz_on(nid) {
    socketSend([request : "on", idx : nid])
}

def domoticz_sceneon(nid) {
    socketSend([request : "sceneon", idx : nid])
}

def domoticz_stop(nid) {
    socketSend([request : "stop", idx : nid])
}

def domoticz_setlevel(nid, xLevel) {
    if (xLevel.toInteger() == 0) {
        socketSend([request : "setlevel", idx : nid, level : xLevel])
    	socketSend([request : "off", idx : nid])
    }    
    else {
        if (state.devices[nid].subType == "RFY") {
            socketSend([request : "stop", idx : nid])
        } 
        else {
            socketSend([request : "setlevel", idx : nid, level : xLevel])
        }
	}
}

def domoticz_setcolor(nid, xHex, xSat, xBri) {
    socketSend([request : "setcolor", idx : nid, hex : xHex, saturation : xSat, brightness : xBri])
    socketSend([request : "on", idx : nid])
}

def domoticz_setcolorHue(nid, xHex, xSat, xBri) {
    socketSend([request : "setcolorhue", idx : nid, hue : xHex, saturation : xSat, brightness : xBri])
    socketSend([request : "on", idx : nid])
}

def domoticz_setcolorWhite(nid, xHex, xSat, xBri) {
    socketSend([request : "setcolorwhite", idx : nid, hex : xHex, saturation : xSat, brightness : xBri])
    socketSend([request : "on", idx : nid])
}

def domoticz_counters(nid, range) {
	socketSend([request : "counters", idx : nid, range : range])
}

def domoticz_setpoint(nid, setpoint) {
	socketSend([request : "SetPoint", idx : nid, setpoint : setpoint])
}

def domoticz_modeChange(nid, modeType, nameLevel) {

	log.debug "[domoticz_modeChange] Thermostat association to be found $nid $modeType $nameLevel"
    idxComponentDevices([type: modeType, idx: nid]).each { key, device ->
        def thermostatDev = getChildDevice(device.dni)

		if (thermostatDev != null) {
            if (modeType == "Mode") thermostatDev.setThermostatMode(nameLevel.toLowerCase())
            if (modeType == "FanMode") thermostatDev.setThermostatFanMode(nameLevel.toLowerCase())
        }
        else log.debug "[domoticz_modeChange] Thermostat association not found $nid $modeType $nameLevel"
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute The real request via the local HUB
/*-----------------------------------------------------------------------------------------*/
private def socketSend(passed) {
	//TRACE("[socketSend] entered with ${passed}")
	def rooPath = ""
    def rooLog = ""
	def hubAction = null
   
    switch (passed.request) {
		case "utilityCount":
        	rooLog = "/json.htm?type=devices&param=addlogmessage&message=SmartThings%20UtilityCount "
        	rooPath = "/json.htm?type=devices&rid=${passed.idx}"   // "UtilityCount"
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationEvtForUCount] )
 			break;
		case "OptionTemperature":
        	rooLog = ""
        	rooPath = "/json.htm?type=devices&filter=temp"   // "OptionTemp"
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationEvtForEveryThing] )
 			break;
		case "OptionUtility":
        	rooLog = ""
        	rooPath = "/json.htm?type=devices&filter=utility"   // "OptionTemp"
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationEvtForEveryThing] )
 			break;
		case "OptionDevices":
        	rooLog = ""
        	rooPath = "/json.htm?type=devices&filter=all&used=true&order=Name"   // "OptionTemp"
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationEvtForEveryThing] )
 			break;
		case "listsensors":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ListSensors"
        	rooPath = "/json.htm?type=devices&filter=temp&used=true&order=Name"   // "Devices"
 			break;
		case "scenes":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ListScenes"
        	rooPath = "/json.htm?type=scenes"										// "Scenes"
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationEvtForScenes] )
 			break;
		case "roomplans":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Room%20Plans"
        	rooPath = "/json.htm?type=plans&order=name&used=true"					// "Plans"  
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationEvtForPlans] )
 			break;
		case "roomplan":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Roomplan%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=getplandevices&idx=${passed.idx}"		// "GetPlanDevices"
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationEvtForRoom] )
 			break;
        case "sceneon":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchscene&idx=${passed.idx}&switchcmd=On" // "SwitchScene"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
        case "sceneoff":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchscene&idx=${passed.idx}&switchcmd=Off"  // "SwitchScene"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;            
		case "status":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Status%20for%20${passed.idx}"
			rooPath = "/json.htm?type=devices&rid=${passed.idx}" 									// "Devices"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackStatus] )
			break;
		case "alive":
        	rooLog = ""
			rooPath = "/json.htm?type=devices&rid=0" 									// "Devices"
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: aliveResponse] )
			break;
        case "off":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Off%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=Off"	// "SwitchLight"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
        case "on":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=On"		// "SwitchLight"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
        case "stop":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Stop%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=Stop"		// "SwitchLight"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
        case "setlevel":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Level%20${passed.level}%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=Set%20Level&level=${passed.level}"  // "SwitchLight"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
        case "setcolor":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ColorHex%20${passed.hex}%20for%20${passed.idx}%20brightness=${passed.brightness}%saturation=${passed.saturation}"
        	rooPath = "/json.htm?type=command&param=setcolbrightnessvalue&idx=${passed.idx}&hex=${passed.hex}&iswhite=false&brightness=${passed.brightness}&saturation=${passed.saturation}" // "SetColBrightnessValue"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
        case "setcolorhue":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ColorHue%20${passed.hue}%20for%20${passed.idx}%20brightness=${passed.brightness}%saturation=${passed.saturation}"
        	rooPath = "/json.htm?type=command&param=setcolbrightnessvalue&idx=${passed.idx}&hue=${passed.hue}&iswhite=false&brightness=${passed.brightness}&saturation=${passed.saturation}" // "SetColBrightnessValue"
            break;
         case "setcolorwhite":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ColorHue%20${passed.hex}%20for%20${passed.idx}%20brightness=${passed.brightness}%saturation=${passed.saturation}"
        	rooPath = "/json.htm?type=command&param=setcolbrightnessvalue&idx=${passed.idx}&hex=${passed.hex}&iswhite=true&brightness=${passed.brightness}&saturation=${passed.saturation}" // "SetColBrightnessValue"
            break;
         case "counters":
         	rooPath = "/json.htm?type=graph&sensor=counter&idx=${passed.idx}&range=${passed.range}"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationForCounters] )
            break;
         case "SetPoint":  
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20SetPoint%20${passed.setpoint}%20for%20${passed.idx}"
         	rooPath = "/json.htm?type=setused&idx=${passed.idx}&setpoint=${passed.setpoint}&mode=ManualOverride&until=&used=true"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
         case "Notification": 
         	def tWhen = 0
            def tValue = 0
            
            if (passed?.value > 0) {
            	tWhen = 2
                tValue = passed.value
                passed.action = "${passed.action}%20${tValue}"
            }
            
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20HTTPNotification%20Type%20${passed.type}%20For%20${passed.idx}"		// type7 is ON , 16 is OFF
            rooPath = "/json.htm?type=command&param=addnotification&idx=${passed.idx}&ttype=${passed.type}&twhen=${tWhen}&tvalue=${tValue}&tmsg=IDX%20${passed.idx}%20${passed.action}&tsystems=http&tpriority=0&tsendalways=false&trecovery=false"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
         case "SensorKWHNotification":         
            rooPath = "/json.htm?type=command&param=addnotification&idx=${passed.idx}&ttype=5&twhen=1&tvalue=0&tmsg=SENSOR%20${passed.idx}&tsystems=http&tpriority=0&tsendalways=false&trecovery=false"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;         
         case "SensorTempNotification":         
            rooPath = "/json.htm?type=command&param=addnotification&idx=${passed.idx}&ttype=0&twhen=3&tvalue=-99&tmsg=IDX%20${passed.idx}%20%24value&tsystems=http&tpriority=0&tsendalways=false&trecovery=false"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;         
         case "SensorLuxNotification":         
            rooPath = "/json.htm?type=command&param=addnotification&idx=${passed.idx}&ttype=5&twhen=1&tvalue=0&tmsg=SENSOR%20${passed.idx}&tsystems=http&tpriority=0&tsendalways=false&trecovery=false"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;         
        case "ClearNotification":  
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20HTTPNotification%20Clear%20For%20${passed.idx}"		
            rooPath = "/json.htm?type=command&param=clearnotifications&idx=${passed.idx}"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: callbackLog] )
            break;
		case "Settings":  
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Settings"
            rooPath = "/json.htm?type=settings"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationSettings] )
            break;
        default:
        	return
            break;
           
	}
    
    if (rooLog != "" && domoticzTrace) {
        def hubActionLog = new physicalgraph.device.HubAction(
            method: "GET",
            path: rooLog,
            headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"],
            null,
            [callback: callbackLog])

        sendHubCommand(hubActionLog)
    }

    if (hubAction == null) hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocation] )
          
    sendHubCommand(hubAction)


}

private def socketList() {

    def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/json.htm?type=devices&filter=all&used=true&order=Name",
        headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"],
        null,
        [callback: callbackList] )

    sendHubCommand(hubAction)
}

void callbackList(evt) {

    def response = getResponse(evt)
    def listIdx = response.result.collect {it.idx}.sort() 
    state.statusrsp = listIdx
    pause 10
    onLocationEvtForDevices(response)    
}

void callbackStatus(evt) {
    def response = getResponse(evt)
    onLocationEvtForDevices(response)    
}

void callbackLog(evt) {
	// dummy handler for status returns, it prevents these responses from going into "normal" response processing
}

void aliveResponse(evt) {
	state.alive = true
    state.aliveCount = 0
    if (state.aliveAgain == false) {
    	state.aliveAgain = true
    	log.info "Domoticz server is alive again"
        if (state.devicesOffline) devicesOnline()
        socketList()
    	}
}

void aliveChecker(evt) {
	if (state.alive == false && state.aliveCount > 1) {
    	state.aliveAgain = false
    	log.error "Domoticz server is not responding"
        
        if (!state.devicesOffline) devicesOffline()
    	}
    
    if (state.aliveCount) state.aliveCount = state.aliveCount + 1
    else state.aliveCount = 1
    
    socketSend([request : "alive"])
    state.alive = false

	// -----------------------------------------------
	// standard scheduling, not using schedule methods
	// -----------------------------------------------

    runIn(20, refreshUtilityCounts)
    runIn(30, scheduledListSensorOptions)
 
	if (state?.scheduleCycle == null)  state.scheduleCycle = 59i
    
	state.scheduleCycle = state.scheduleCycle + 1
    
    if (state.scheduleCycle % 60 == 0) {
        runIn(10,refreshDevicesFromDomoticz)
        
        if (state.devReportPower != null) {      
        	runIn(40, scheduledPowerReport)
        }
    }
}

private def devOnline(dev) {

    if (dev?.currentValue("DeviceWatch-DeviceStatus") == "offline") dev.sendEvent(name: "DeviceWatch-DeviceStatus", value: "online", displayed: false, isStateChange: true)

}

private def devOffline(dev) {

	if (dev?.currentValue("DeviceWatch-DeviceStatus") == "online") dev.sendEvent(name: "DeviceWatch-DeviceStatus", value: "offline", displayed: false, isStateChange: true)

}

void devicesOnline() {
    log.info "[devicesOnline] turn devices ONLINE"

	getChildDevices().each { dev ->
    	
		if (!dev?.currentValue("DeviceWatch-Enroll")) {
            dev.sendEvent(name: "DeviceWatch-Enroll", value: groovy.json.JsonOutput.toJson([protocol: "LAN", scheme:"untracked"]), displayed: false)
        } 
        else {
	        dev.sendEvent(name: "DeviceWatch-DeviceStatus", value: "online", displayed: false, isStateChange: true)
        }        
    }
    state.devicesOffline = false
    pause 2
}

void devicesOffline() {
    log.error "[devicesOffline] turn devices OFFLINE"


	getChildDevices().each { dev ->
		if (!dev?.currentValue("DeviceWatch-Enroll")) {
            dev.sendEvent(name: "DeviceWatch-Enroll", value: groovy.json.JsonOutput.toJson([protocol: "LAN", scheme:"untracked"]), displayed: false)
        } 
        dev.sendEvent(name: "DeviceWatch-DeviceStatus", value: "offline", displayed: false, isStateChange: true)
    }
    state.devicesOffline = true
    pause 2

}

/*-----------------------------------------------------------------------------------------*/
/*		
/*-----------------------------------------------------------------------------------------*/
void refreshDevicesFromDomoticz() {

    socketSend([request : "roomplans"])
    pause 5
    
	//state.domoticzRefresh = true
	state.listOfRoomPlanDevices = []
    settings.domoticzPlans.each { v -> 
        state.statusPlansRsp.each {
            if (v == it.Name) {
                socketSend([request : "roomplan", idx : it.idx])
                pause 10
            }
            pause 2
        }
    }
        
	socketList()
    socketSend([request : "scenes"])
	runIn(2,updateDeviceList)
    
}

/*-----------------------------------------------------------------------------------------*/
/*		Domoticz will send an notification message to ST for all devices THAT HAVE BEEN SELECTED to do that
/*-----------------------------------------------------------------------------------------*/
def eventDomoticz() {

	if (params.message.contains("IDX ") && params.message.split().size() >= 3) {
    	def idx = params.message.split()[1]
    	def status = params.message.split()[2]
        def dni = state.devices[idx]?.dni
        def deviceType = state.devices[idx]?.deviceType
        def switchTypeVal = state.devices[idx]?.switchTypeVal

		def attr = null
        def level = ""

        if (params.message.split().size() == 4) level = params.message.split()[3]
        
       	switch (deviceType) {
        	case "domoticzOnOff":
            	if (switchTypeVal != 7) attr = "switch"   // 7 is a dimmer , just request complete status
            	break
            case "domoticzMotion":
            	attr = "motion"
                if (status == "on") status = "active" else status = "inactive"
            	break
            case "domoticzBlinds":
            	attr = "switch"
				if (status == "on") status = "Closed" else status = "Open"
                break
            case "domoticzSelector":
            	attr = "switch"
				if (status == "off") level = 0
                break
            case "domoticzDuskSensor":
            	attr = "switch"
                break
            case "domoticzContact":
            	attr = "contact"
                if (status == "on") status = "Open" else status = "Closed"
               	break
            case "domoticzSmokeDetector":
            	attr = "smoke"
                if (status == "on") status = "smoke" else status = "clear"
            	break                
        }
        
        if (attr) {
        	getChildDevice(dni).sendEvent(name: attr, value: status)
            
            if (level != "") {
            	getChildDevice(dni).sendEvent(name: "level", value: level)
                domoticz_poll(idx)
                }
        }
        else {
            TRACE("[eventDomoticz] requesting status for ${idx}")
            socketSend([request : "status", idx : idx]) 
        } 
    }
    else if (params.message.contains("SENSOR ") && params.message.split().size() >= 2) { 
            def idx = params.message.split()[1]
            socketSend([request : "utilityCount", idx : idx])
    	}  
    else {
		TRACE("[eventDomoticz] no custom message in Notification (unknown device in ST) perform SocketList ${params.message}")
		// get the unknown device defined in ST (if part of selected types)    
   		socketList()
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		get the access token. It will be displayed in the log/IDE. Plug this in the Domoticz Notification Settings access_token
/*-----------------------------------------------------------------------------------------*/
private def initRestApi() {
    TRACE("[initRestApi]")
    if (!state.accessToken) {
        try {
        	def token = createAccessToken()
        	TRACE("[initRestApi] Created new access token: ${state.accessToken}")
        }
        catch (e) {
			log.error "[initRestApi] did you enable OAuth in the IDE for this APP?"
        }
    }
    state.urlCustomActionHttp = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"

}
//-----------------------------------------------------------
private def getResponse(evt) {

    if (evt instanceof physicalgraph.device.HubResponse) {
        return evt.json
    }
}

private def getHubID(){
    TRACE("[getHubID]")
    def hubID
    def hubs = location.hubs.findAll{ it.type == physicalgraph.device.HubType.PHYSICAL } 
    TRACE("[getHubID] hub count: ${hubs.size()}")
    if (hubs.size() == 1) hubID = hubs[0].id 
    TRACE("[getHubID] hubID: ${hubID}")
    return hubID
}
private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private def textCopyright() {
    return "Copyright (c) 2018 Martin Verbeek"
}

private def idxSettings(type) {
	if (!type) type = "" else type = type + "[" 
	return settings.findAll { key, value -> key.contains("idx${type}") }
}

private def idxComponentDevices(passed) {
	if (!passed.type) return
   
	if (!passed.idx) return state.devices.findAll { key, value -> value?."idx${passed.type}" != null}
    else return state.devices.findAll { key, value -> value?."idx${passed.type}" == passed.idx.toString()}
}

def notifyNewVersion() {

	TRACE("[notifyNewVersion] on GitHub ${appVerInfo().split()[1]} running ${runningVersion()} ")
	if (appVerInfo().split()[1] != runningVersion()) {
    	sendNotificationEvent("Domoticz Server App has a newer version, ${appVerInfo().split()[1]}, please visit IDE to update app/devices")
    }
}

def getWebData(params, desc, text=true) {
	try {
		httpGet(params) { resp ->
			if(resp.data) {
				if(text) { return resp?.data?.text.toString() } 
                else { return resp?.data }
			}
		}
	}
	catch (ex) {
		if(ex instanceof groovyx.net.http.HttpResponseException) {log.error "${desc} file not found"} 
        else { log.error "[getWebData] (params: $params, desc: $desc, text: $text) Exception:", ex}
		
        return "[getWebData] ${label} info not found"
	}
}

private def appVerInfo()		{ return getWebData([uri: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/DomoticzData", contentType: "text/plain; charset=UTF-8"], "changelog") }