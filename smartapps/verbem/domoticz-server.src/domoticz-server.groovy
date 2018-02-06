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
    V6.00	Abilty to check oAuth in DZ settings against current oAuth
    		Restructure of SocketSend routine (only path and callback provided to sendHub)
            Restructure of UpdateDeviceList routine
	V6.10	Start with implementing creation of ST real devices as DZ virtual devices
    		Create Hardware routine, Create Device routine, Create Sensor Routine
            Setup page with what devicetypes to select
    V6.11	toInteger check on device return
    V6.12	hardwareIdx was not set on time for virtual devices creation
    V6.13	Add lock as a virtual device, fix of null pointer check in ucount
    V6.14	uninstalled with clearAllNotifications, redo device.Temp check 
 */

import groovy.json.*
import groovy.time.*
import java.Math.*

private def cleanUpNeeded() {return true}
private def runningVersion() {"6.14"}
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
    page name:"setupSmartThingsToDomoticz"

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
        if (!state.validUrl) state.validUrl = false
        socketSend([request: "Settings"])
        pause 5
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
            href "setupSmartThingsToDomoticz", title:"Define SmartThing Devices in Domoticz", description:"Tap to open"
        }
        section([title:"Options", mobileOnly:true]) {
            label title:"Assign a name", required:false
        }
        section("HTTP Custom Action URL for Domoticz") {
            paragraph "${state.urlCustomActionHttp}"
            paragraph "Valid setting in Domoticz? : ${state.validUrl}"
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
        title       : "Create Utility Report device(s)",
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
                
                if (state.optionsGas && iMap.type == "domoticzThermostat") {
                	input "idxGas[${key}]", "enum", title:"Add Gas Usage?", options: state.optionsGas, required: false
                }
        	}
        }
      }
    }
    
    sendThermostatModes()
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
/*		SET Up Configure Domoticz PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupSmartThingsToDomoticz() {
    TRACE("[setupSmartThingsToDomoticz]")
     
    def inputST2DZ = [
        name        : "domoticzVirtualDevices",
        submitOnChange : true,
        type        : "bool",
        title       : "Define native Smartthings devices as Domoticz Virtuals",
        defaultValue: false
    ]
    def pageProperties = [
        name        : "setupSmartThingsToDomoticz",
        title       : "Configure SmartThings to Domoticz Server",
        nextPage    : "setupMenu",
        install     : false,
        uninstall   : false
    ]
    return dynamicPage(pageProperties) {
      section {
            input inputST2DZ
        	}
      if (domoticzVirtualDevices) {
      	section {
      		paragraph "Select native SmartThing devices \n\n Devices created by this App will be IGNORED when you select them"
      		input "dzDevicesSwitches", "capability.switch", title:"Select switch devices", multiple:true, required:false
      		input "dzDevicesLocks", "capability.lock", title:"Select Locks", multiple:true, required:false
      		input "dzSensorsContact", "capability.contactSensor", title:"Select contact sensors", multiple:true, required:false
      		input "dzSensorsMotion", "capability.motionSensor", title:"Select motion sensors", multiple:true, required:false
      		input "dzSensorsTemp", "capability.temperatureMeasurement", title:"Select Temperature sensors", multiple:true, required:false
      		input "dzSensorsHum", "capability.relativeHumidityMeasurement", title:"Select Humidity sensors", multiple:true, required:false
      		//input "dzSensorsPressure", "capability.relativeHumidityMeasurement", title:"Select Pressure sensors", multiple:true, required:false
      		input "dzSensorsIll", "capability.illuminanceMeasurement", title:"Select Illuminance sensors", multiple:true, required:false
        }
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

	socketSend([request : "List"])
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
    unsubscribe()
    unschedule()
	// delete ST HARDWARE in DZ
    if (settings?.domoticzVirtualDevices == true) socketSend([request:"DeleteHardware"])
	// clear custom notifications in DZ
    clearAllNotifications()
    
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
    notifyNewVersion()
    
    unschedule()    
    unsubscribe()    
    
    if (state.accessToken) state.urlCustomActionHttp = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"
    
    state.setup.installed 		= true
    state.networkId 			= settings.domoticzIpAddress + ":" + settings.domoticzTcpPort        
    state.alive 				= true
    state.aliveAgain 			= true
    state.devicesOffline 		= false
    state.scheduleCycle 		= 59i  // next cycle is 60, which triggers refresh of devices
	state.optionsLux 			= [:]
    state.optionsMotion 		= [:]
    state.optionsTemperature 	= [:]
    state.optionsCarbon1 		= [:]
    state.optionsCarbon2 		= [:]
    state.optionsPower 			= [:]
    state.optionsModes 			= [:]
    state.optionsGas 			= [:]
    state.optionsHumidity		= [:]
    state.optionsPressure		= [:]

    updateDeviceList()
	addReportDevices()
    assignSensorToDevice()
    
    if (settings?.domoticzVirtualDevices == true) {
    	if (state.dzHardwareIdx == null) {
        	socketSend([request:"CreateHardware"])
        	pause 5
        	socketSend([request:"UpdateHardware"])
        	pause 5
            socketSend([request:"ListHardware"])
            pause 5
       	}

        if (dzDevicesSwitches) subscribe(dzDevicesSwitches, "switch", handlerEvents)
        if (dzDevicesLocks) subscribe(dzDevicesLocks, "lock", handlerEvents)
        if (dzSensorsContact) subscribe(dzSensorsContact, "contact", handlerEvents)
        if (dzSensorsMotion) subscribe(dzSensorsMotion, "motion", handlerEvents)
        if (dzSensorsTemp) subscribe(dzSensorsTemp, "temperature", handlerEvents)
        if (dzSensorsHum) subscribe(dzSensorsHum, "humidity", handlerEvents)
        if (dzSensorsIll) subscribe(dzSensorsIll, "illuminance", handlerEvents)
        
        runIn(10, defineSmartThingsInDomoticz) 

    }
    else {
    	state.remove("virtualDevices")
        socketSend([request:"DeleteHardware"])
        state.remove("dzHardwareIdx")
        pause 5
    }
        
    if 	(cleanUpNeeded() == true) {
        if (state?.runUpdateRoutine != runningVersion()) runUpdateRoutine()
        state.runUpdateRoutine = runningVersion()
    }    
    
    runEvery1Minute(aliveChecker)        
    schedule("2015-01-09T12:00:00.000-0600", notifyNewVersion)   
}

private def runUpdateRoutine() {
}

private def clearAllNotifications() {
	state.devices.each {key, item ->
		if (item.type == "switch") {
        	log.info "Clear Notifications for Devices ${item.type} ${item.dni} idx ${item.idx}"
        	socketSend([request : "ClearNotification", idx : item.idx])
            pause 2
        }
    }

	def options = state.findAll { key, value -> key.startsWith("options") }
    options.each { key, sensor ->
    	sensor.each { idx, content ->
        	log.info "Clear Notifications for Sensor ${content} idx ${item.idx}"
        	socketSend([request : "ClearNotification", idx : idx])
            pause 2        
        }
    }
    
}

private def assignSensorToDevice() {
	    
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
            if (k.contains("idxGas")) {
                state.devices[idx].idxGas = v
            }
    }
}

void scheduledListSensorOptions() {
	socketSend([request : "OptionUtility"])
    socketSend([request : "OptionTemperature"])
    socketSend([request : "OptionDevices"])
}

void scheduledPowerReport() {
	state.requestedReport = "Power"   
	state.reportPowerDay = [:]		// last 24 hours graph
    state.reportPowerMonth = [:]	// last 31 days graph
    state.reportPowerYear = [:]		// last 52 weeks graph
    pause 3

	state.optionsPower.each { key, item ->
        socketSend([request : "counters", idx : key, range : "day"])
        socketSend([request : "counters", idx : key, range : "month"])
        socketSend([request : "counters", idx : key, range : "year"])
    }
}

void scheduledGasReport() {
	state.requestedReport = "Gas"      
	state.reportGasDay = [:]		// last 24 hours graph
    state.reportGasMonth = [:]		// last 31 days graph
    state.reportGasYear = [:]		// last 52 weeks graph
    pause 3
    
	state.optionsGas.each { key, item ->
        socketSend([request : "counters", idx : key, range : "day"])
        socketSend([request : "counters", idx : key, range : "month"])
        socketSend([request : "counters", idx : key, range : "year"])
    }
}

void defineSmartThingsInDomoticz() {
	if (!state?.unitcode) state.unitcode = 0
    def unitcode = state.unitcode
    def type
    def exists
      
        dzDevicesSwitches.each { dev ->
        	if (dev.deviceNetworkId.contains("IDX") == false) {
            	type = "switch"
                if (getVirtualIdx([name: dev.displayName, type: type]) == null) {
                    socketSend([request:"CreateVirtualDevice", deviceName:dev.displayName.replaceAll(" ", "%20"), switchType:0, unitcode: unitcode])
                    unitcode = unitcode + 1
                	// type 0 = on Off, 7 = dimmer, 8 = motion, contact = 2, lock = 19
             	}
           	}
        }
        dzDevicesLocks.each { dev ->
        	if (dev.deviceNetworkId.contains("IDX") == false) {
            	type = "lock"
                if (getVirtualIdx([name: dev.displayName, type: type]) == null) {
                    socketSend([request:"CreateVirtualDevice", deviceName:dev.displayName.replaceAll(" ", "%20"), switchType:19, unitcode: unitcode])
                    unitcode = unitcode + 1
             	}
           	}
        }
        dzSensorsContact.each { dev ->
        	if (dev.deviceNetworkId.contains("IDX") == false) {
            	type = "contact"
                if (getVirtualIdx([name: dev.displayName, type: type]) == null) {
                    socketSend([request:"CreateVirtualDevice", deviceName:dev.displayName.replaceAll(" ", "%20"), switchType:2, unitcode: unitcode])
                    unitcode = unitcode + 1
              	}
            }
        }
        dzSensorsMotion.each { dev ->
        	if (dev.deviceNetworkId.contains("IDX") == false) {
            	type = "motion"
                if (getVirtualIdx([name: dev.displayName, type: type]) == null) {
                    socketSend([request:"CreateVirtualDevice", deviceName:dev.displayName.replaceAll(" ", "%20"), switchType:8, unitcode: unitcode])
                    unitcode = unitcode + 1
                }
            }
        }
        state.unitcode = unitcode
        
        dzSensorsTemp.each { dev ->
        	if (dev.deviceNetworkId.contains("IDX") == false) {
            	type = "temperature"
                if (getVirtualIdx([name: dev.displayName, type: type]) == null) {
                	socketSend([request:"CreateVirtualSensor", deviceName:dev.displayName.replaceAll(" ", "%20"), sensorType:80])
               	}
            }
        }
       	dzSensorsIll.each { dev ->
        	if (dev.deviceNetworkId.contains("IDX") == false) {
            	type = "illuminance"
                if (getVirtualIdx([name: dev.displayName, type: type]) == null) {
	                socketSend([request:"CreateVirtualSensor", deviceName:dev.displayName.replaceAll(" ", "%20"), sensorType:246])
                }
            }
        }
}
/*-----------------------------------------------------------------------------------------*/
/*		Update the usage info in virtual domoticz devices that have been selected by user to sync to DZ
/*-----------------------------------------------------------------------------------------*/
void handlerEvents(evt) {

	if (evt?.isStateChange() == false) {
        return
    }

	def dev = evt?.device
    
    if (!dev) return
    if (dev?.typeName.contains("domoticz")) return
         
	def idx = getVirtualIdx([name:dev.displayName, type: evt.name])
    
    if (idx) {   
    	log.info "${evt.name} ${evt.stringValue} for ${dev.displayName} idx ${idx}"
        switch (evt.name) {
        case "switch":
            socketSend([request: evt.stringValue, idx: idx])
            break
        case "lock":
        	if (evt.stringValue == "locked") socketSend([request: "on", idx: idx]) else socketSend([request: "off", idx: idx])
            break
        case "motion":
        	if (evt.stringValue == "inactive") socketSend([request: "off", idx: idx]) else socketSend([request: "on", idx: idx])
            break
        case "contact":
        	if (evt.stringValue == "closed") socketSend([request: "off", idx: idx]) else socketSend([request: "on", idx: idx])
            break
        case "temperature":
        	socketSend([request: "SetTemp", idx: idx, temp:evt.stringValue])
            break
        case "humidity":
        	socketSend([request: "SetHumidity", idx: idx, temp:evt.stringValue])
            break
        case "illuminance":
        	socketSend([request: "SetLux", idx: idx, lux:evt.stringValue])
            break
        default:
            break
        }
	}
}

private def getVirtualIdx(passed) {
	if (!settings?.domoticzVirtualDevices) return

    def virtual = state?.virtualDevices.find {key, item -> item.name.toUpperCase() == passed.name.toUpperCase() && item.type.toUpperCase() == passed.type.toUpperCase() }
  
    if (virtual) return virtual.key   
}
/*-----------------------------------------------------------------------------------------*/
/*		Update the usage info on composite DZ devices that report on a utility
/*		kWh, Lux etc...
/*-----------------------------------------------------------------------------------------*/
void callbackForUCount(evt) {
    def response = getResponse(evt)   
    if (response?.result == null)  return
	//TRACE("callbackForUCount")

	response.result.each { utility ->
		// Power usage    
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
                    TRACE("[callbackForUCount] Not found kWh ${utility.ID} ${utility.idx}")
            	}            
            }
        }
        // Lux
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
		// Motion        
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
 		// Temperature       
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
void callbackForRoom(evt) {
	def response = getResponse(evt)
	if (response?.result == null) return

    TRACE("[callbackForRoom] Domoticz response with Title : ${response.title} number of items returned ${response.result.size()}") 

    response.result.each {
		if (it?.SubType != "kWh") {
            TRACE("[callbackForRoom] Device ${it.Name} with idx ${it.devidx}")
            state.listOfRoomPlanDevices.add(it.devidx)
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Get Room Plans defined into Selectables for setupDomoticz
/*-----------------------------------------------------------------------------------------*/
void callbackForPlans(evt) {
	def response = getResponse(evt)   
    if (response?.result == null) return
    
    state.statusPlansRsp = response.result

    TRACE("[callbackForPlans] Domoticz response with Title : ${response.title} number of items returned ${response.result.size()}") 

    state.listPlans = []
    pause 1

    response.result.each {
        TRACE("[callbackForPlans] ${it.Devices} devices in room plan ${it.Name} with idx ${it.idx}")
        state.listPlans.add(it.Name)
        pause 1
    }

    state.listPlans.sort()
    pause 1
}

/*-----------------------------------------------------------------------------------------*/
/*		proces for adding and updating status for Scenes and Groups
/*-----------------------------------------------------------------------------------------*/
void callbackForScenes(evt) {
    def response = getResponse(evt)
    def groupIdx = response?.result.collect {it.idx}.sort()
    state.statusGrpRsp = groupIdx
    pause 2

	if (response?.result == null) return

    TRACE("[callbackForScenes] Domoticz response with Title : ${response.title} number of items returned ${response.result.size()}") 

	response?.result.each {
        TRACE("[callbackForScenes] ${it.Type} ${it.Name} ${it.Status} ${it.Type}")
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
/*		callback for adding and updating status of devices in SmartThings
/*-----------------------------------------------------------------------------------------*/
private def callbackForDevices(statusrsp) {

	if (statusrsp?.result== null) return	
    
	def compareTypeVal
    def SubType
    def dni
    def idxST = 9999999
    
    if (state?.dzHardwareIdx) idxST = state.dzHardwareIdx.toInteger() 
    
	if (!state.listSensors) state.listSensors = [:]
    if (!state.virtualDevices) state.virtualDevices = [:]
    
	statusrsp.result.each { device ->
    	if (device?.Used == 1) {						// Only devices that are defined as being USED in DZ will make it as real devices
            compareTypeVal = device?.SwitchTypeVal
            dni = null

            // handle SwitchTypeVal Exceptions
            if (device?.Temp) compareTypeVal = 99
            if (device?.SetPoint) compareTypeVal = 98
            if (compareTypeVal == null) compareTypeVal = 100
			
            // REAL SmartThings devices that where defined in Domoticz as virtual devices
            if (settings?.domoticzVirtualDevices == true && device?.HardwareID == idxST) {
  
                switch (compareTypeVal) {
                case 0:
                	SubType = "switch"
                    def dev
                    settings.dzDevicesSwitches.each {
                        if (it.displayName == device.Name) dev = it
                    }
                    dni = dev.deviceNetworkId

                	if (device.Notifications == "false") {
                        socketSend([request : "Notification", idx : device.idx, type : 7, action : "on"])
                        socketSend([request : "Notification", idx : device.idx, type : 16, action : "off"])
                    }
                    break
                case 19:
                	SubType = "lock"
                    def dev
                    settings.dzDevicesSwitches.each {
                        if (it.displayName == device.Name) dev = it
                    }
                    dni = dev.deviceNetworkId

                	if (device.Notifications == "false") {
                        socketSend([request : "Notification", idx : device.idx, type : 7, action : "on"])
                        socketSend([request : "Notification", idx : device.idx, type : 16, action : "off"])
                    }
                    break
               	case 2:
                	SubType = "contact"
                    break
                case 8:
                	SubType = "motion"
                    break
                case 99:
                	SubType = "temperature"
                    break
                case 100:
                	SubType = "illuminance"
                    break
                }
                state.virtualDevices[device.idx] = [idx: device.idx, name: device.Name, type: SubType, dni: dni ]
            	compareTypeVal = 100
            }
			
            switch (compareTypeVal) 
            {
                case [3, 13, 6, 16]:		//	Window Coverings, 6 & 16 are inverted
                    if (domoticzTypes.contains('Window Coverings')) addSwitch(device.idx, "domoticzBlinds", device.Name, device.Status, device.Type, device)
                    break
                case [0, 7]:		// 	Lamps OnOff, Dimmers and RGB
                    SubType = device?.SubType
                    if (domoticzTypes.contains('On/Off/Dimmers/RGB') && SubType != "kWh") addSwitch(device.idx, "domoticzOnOff", device.Name, device.Status, device.Type, device)
                    break
                case [2, 11]:				//	Contact 
                    if (domoticzTypes.contains('Contact Sensors')) addSwitch(device.idx, "domoticzContact", device.Name, device.Status, device.Type, device)
                    state.listSensors[device.idx] = [name: device.Name, idx: device.idx, type: "domoticzContact"]
                    break
                case 5:				//	Smoke Detector
                    if (domoticzTypes.contains('Smoke Detectors')) addSwitch(device.idx, "domoticzSmokeDetector", device.Name, device.Status, device.Type, device)
                    state.listSensors[device.idx] = [name: device.Name, idx: device.idx, type: "domoticzSmokeDetector"]
                    break
                case 8:				//	Motion Sensors
                    if (domoticzTypes.contains('Motion Sensors')) addSwitch(device.idx, "domoticzMotion", device.Name, device.Status, device.Type, device)
                    state.listSensors[device.idx] = [name: device.Name, idx: device.idx, type: "domoticzMotion"]
                    break
                case 12:			//	Dusk Sensors/Switch
                    if (domoticzTypes.contains('Dusk Sensors')) addSwitch(device.idx, "domoticzDuskSensor", device.Name, device.Status, device.Type, device)
                    state.listSensors[device.idx] = [name: device.Name, idx: device.idx, type: "domoticzDuskSensor"]
                    break
                case 18:			//	Selector Switch
                    if (domoticzTypes.contains("On/Off/Dimmers/RGB")) addSwitch(device.idx, "domoticzSelector", device.Name, device.Status, device.SwitchType, device)
                    break
                case 98:			//	Thermostats
                    if (domoticzTypes.contains("Thermostats")) addSwitch(device.idx, "domoticzThermostat", device.Name, device.SetPoint, device.Type, device)
                    state.listSensors[device.idx] = [name: device.Name, idx: device.idx, type: "domoticzThermostat"]
                   break
                case 99:			//	Sensors
                    if (domoticzTypes.contains("(Virtual) Sensors")) addSwitch(device.idx, "domoticzSensor", device.Name, "Active", device.Type, device)
                    state.listSensors[device.idx] = [name: device.Name, idx: device.idx, type: "domoticzSensor"]
                    break
                case 100:
                    break
                default:
                    TRACE("[callbackForDevices] non handled SwitchTypeVal ${compareTypeVal} ${device}")
                break
            }	
        } 
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		callback for updating usage counters and reporting in SmartThings
/*		it will also do some linking between individual domoticz devices into a single SmartThings device
/*-----------------------------------------------------------------------------------------*/
def callbackForEveryThing(evt) {
    def response = getResponse(evt)
	if (response?.result == null) return
    
    def kwh = 0f
    def watt = 0f
    def powerUnit = "kWh"
    def powerUsage = "Watt"    
    def gasUsage = 0f
    def gasTotal = 0f
    def gasUnit = "m3"
    def stateDevice
    def ID
    def IDX
    
    response.result.each {
		//TEMP		    	        
        if (it?.Type == "Temp") {
        	state.optionsTemperature[it.idx] = "${it.idx} : ${it.Name}"
            if (it.Notifications == "false") socketSend([request : "SensorTempNotification", idx : it.idx])
        }
        //MOTION
        if (it?.SwitchTypeVal == 8) {
        	state.optionsMotion[it.idx] = "${it.idx} : ${it.Name}"
        }	
		//MODES for thermostatFanModes and thermostatModes
		if (it?.SwitchTypeVal == 18) {
        	state.optionsModes[it.idx] = "${it.idx} : ${it.Name}"
        }	
        //SMOKE       
        if (it?.SwitchTypeVal == 5) {
        	state.optionsCarbon1[it.idx] = "${it.idx} : ${it.Name}"
        	state.optionsCarbon2[it.idx] = "${it.idx} : ${it.Name}"
        }
        //LUX
        if (it?.Type == "Lux") {
        	state.optionsLux[it.idx] = "${it.idx} : ${it.Name}"
            if (it.Notifications == "false") socketSend([request : "SensorLuxNotification", idx : it.idx])
        }
        //HUMIDITY
        if (it?.Type == "Humidity") {
        	state.optionsHumidity[it.idx] = "${it.idx} : ${it.Name}"
            if (it.Notifications == "false") socketSend([request : "SensorHumidityNotification", idx : it.idx])
        }
        //USAGE POWER
        if (it?.SubType == "kWh") {	
        	state.optionsPower[it.idx] = "${it.idx} : ${it.Name}" 
            
            if (it.Notifications == "false") socketSend([request : "SensorKWHNotification", idx : it.idx])
            
            kwh = kwh + Float.parseFloat(it.Data.split()[0])
            watt = watt + Float.parseFloat(it.Usage.split()[0])
			powerUnit = it.Data.split()[1]
            powerUsage = it.Usage.split()[1]
			//add idxPower to real device by matching the ID
			ID = it?.ID
			stateDevice = state.devices.find {key, item -> 
		    	item.deviceId == ID
    		}
            
            if (!stateDevice) { // XIAOMI try
            	ID = "${it?.ID.substring(6)}${it?.ID.substring(0,6)}"                
                stateDevice = state.devices.find {key, item -> 
                    item.deviceId == ID
                }
            }
            
            IDX = it.idx
            if (stateDevice) {
            	if (state.devices[stateDevice.key]?.idxPower != IDX) {
            		state.devices[stateDevice.key].idxPower = IDX
                	pause 2
                }
            }
		}
		//USAGE GAS
        if (it?.SubType == "Gas") {	
        	state.optionsGas[it.idx] = "${it.idx} : ${it.Name}"       
            gasTotal = gasTotal + Float.parseFloat(it.Counter)
            gasUsage = gasUsage + Float.parseFloat(it.CounterToday.split()[0])
            gasUnit = it.CounterToday.split()[1]
		}
}
    
	// pass to Devices that report Usage totals   
    if (kwh > 0 && state.devReportPower != null) {
    	def devReportPower = getChildDevice(state.devReportPower)
        if (devReportPower) {
            devReportPower.sendEvent(name:"powerTotal", value: "${kwh.round(3)} ${powerUnit}", unit:powerUnit)
            devReportPower.sendEvent(name:"power", value: watt.round(), unit:powerUsage)
        }
   	}
    if (gasUsage > 0 && state.devReportGas != null) {
    	def devReportGas = getChildDevice(state.devReportGas)
        if (devReportGas) {
            devReportGas.sendEvent(name:"powerTotal", value: "${gasTotal.round(3)} ${gasUnit}", unit:gasUnit)
            devReportGas.sendEvent(name:"power", value: gasUsage.round(3), unit:gasUnit)
        }
   	}
}

/*-----------------------------------------------------------------------------------------*/
/*		callback for creating usage reports (DAY, MONTH and YEAR)
/*-----------------------------------------------------------------------------------------*/
def callbackForCounters(evt) {
    def response = getResponse(evt)
	if (response?.result == null) return
    
    def hour
    def day
    def week
    def date

    switch (response.title.split()[2]) {
        case "day":
			def dayList
            if (state.requestedReport == "Power") dayList = state.reportPowerDay
            if (state.requestedReport == "Gas") dayList = state.reportGasDay
            
            response.result.each { p ->
            	hour = p.d
                if (dayList[hour]) dayList[hour] = dayList[hour] + p.v.toFloat() else dayList[hour] = p.v.toFloat()
            }
            if (state.requestedReport == "Power") state.reportPowerDay = dayList
            if (state.requestedReport == "Gas") state.reportGasDay = dayList

	        break;
            
        case "month":
			def monthList
            if (state.requestedReport == "Power") monthList = state.reportPowerMonth
            if (state.requestedReport == "Gas") monthList = state.reportGasMonth
            
            response.result.each { p ->
            	day = p.d
                if (monthList[day]) monthList[day] = monthList[day] + p.v.toFloat() else monthList[day] = p.v.toFloat()
                monthList[day] = monthList[day].round(3)
            }
            if (state.requestedReport == "Power") state.reportPowerMonth = monthList
            if (state.requestedReport == "Gas") state.reportGasMonth = monthList

            break;
            
        case "year":
			def yearList
            if (state.requestedReport == "Power") yearList = state.reportPowerYear
            if (state.requestedReport == "Gas") yearList = state.reportGasYear
            
            response.result.each { p ->
                date = new Date().parse('yyyy-MM-dd', "${p.d}")
                week = "${date.getAt(Calendar.YEAR)}-${date.getAt(Calendar.WEEK_OF_YEAR)}"
                if (yearList[week]) yearList[week] = yearList[week] + p.v.toFloat() else yearList[week] = p.v.toFloat()
                yearList[week] = yearList[week].round(3)
            }
            if (state.requestedReport == "Power") state.reportPowerYear = yearList
            if (state.requestedReport == "Gas") state.reportGasYear = yearList

            break;
    } 

}

/*-----------------------------------------------------------------------------------------*/
/*		callback for getting all devices 
/*-----------------------------------------------------------------------------------------*/
def callbackList(evt) {
    def response = getResponse(evt)    
    if (response?.result == null) return
    
	state.listInprogress = true
    
    def listIdx = response.result.collect {it.idx}.sort() 
    callbackForDevices(response)    
    state.statusrsp = listIdx
    state.listInprogress = false
}

/*-----------------------------------------------------------------------------------------*/
/*		callback for getting single device status
/*-----------------------------------------------------------------------------------------*/
def callbackStatus(evt) {
	if (state.listInprogress) return
    def response = getResponse(evt)
    callbackForDevices(response)    
}

/*-----------------------------------------------------------------------------------------*/
/*		dummy callback handler for speed
/*-----------------------------------------------------------------------------------------*/
def callbackLog(evt) {
	// dummy handler for status returns, it prevents these responses from going into "normal" response processing
}

/*-----------------------------------------------------------------------------------------*/
/*		Capture the created hardware IDX for SmartThings
/*-----------------------------------------------------------------------------------------*/
def callbackListHardware(evt) {
    def response = getResponse(evt)    
    if (response?.result == null) return
    
    state.dzHardwareIdx			= null
	response.result.each { hardware ->
    	if (hardware.Name == "SmartThings") {
        	state.dzHardwareIdx = hardware.idx
            pause 5
            TRACE("[] SmartThings Hardware id in Domoticz is ${state.dzHardwareIdx}")
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		callback for getting the Domoticz Settings
/*-----------------------------------------------------------------------------------------*/
def callbackForSettings(evt) {
    def response = getResponse(evt)
	if (response.HTTPURL == null) return

    def decoded = response.HTTPURL.decodeBase64()
    def httpURL = new String(decoded)
    TRACE("[callbackForSettings] ${httpURL} ${httpURL.contains(state.urlCustomActionHttp)}")
    state.validUrl = httpURL.contains(state.urlCustomActionHttp) 
}

/*-----------------------------------------------------------------------------------------*/
/*		callback after creation of the virtual sensor/device it will provide the new IDX
/*		call for the provided device, this will create the link between IDX and NAME
/*		in state.virtualDevices
/*-----------------------------------------------------------------------------------------*/
def callbackVirtualDevices(evt) {
    def response = getResponse(evt)
    log.info response
	response?.result.each { hardware ->
    	socketSend([request:"status", idx:hardware.idx])
    }  
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
        //TRACE("[addSwitch] Updating child device ${addr}, ${passedFile}, ${passedName}, ${passedStatus}")        
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
            socketSend([request : "Notification", idx : addr, type : 16, action : "off"])
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

/*-----------------------------------------------------------------------------------------*/
/*		generate the sendEvent that will be send to devices
/*-----------------------------------------------------------------------------------------*/
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
/*		Create a status-attribute list that will be passed to sendEvent handler method
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
            	if (domoticzDevice.hasAttribute("level")) attributeList.put('level', v)
                
                if (domoticzStatus?.LevelInt != v && state.devices[addr]?.MaxDimLevel == null) {
					state.devices[addr].MaxDimLevel = domoticzStatus.MaxDimLevel                   
                }    
                	
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

/*-----------------------------------------------------------------------------------------*/
/*		send ThermostatModes that are supported to the thermostat device
/*		the modes are being retrieved from a multiselector switch that is related to
/*		the thermostat in the smartapp (this is done with composite devices)
/*-----------------------------------------------------------------------------------------*/
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

/*-----------------------------------------------------------------------------------------*/
/*		adding reporting devices for usage
/*-----------------------------------------------------------------------------------------*/
private def addReportDevices() {
	def passedName
    def newdni
    def dev

	if (settings.domoticzReportPower) {
		if (state?.optionsPower) {
            passedName = "Power Reporting Device"
            newdni = app.id + ":Power Reporting Device:" + 10000
            dev = getChildDevice(newdni)

            if (!dev) {      
                dev = addChildDevice("verbem", "domoticzPowerReport", newdni, getHubID(), [name:passedName, label:passedName, completedSetup:true])
                pause 5
            }
            state.devReportPower = newdni
        }

		if (state?.optionsGas) {
            passedName = "Gas Reporting Device"
            newdni = app.id + ":Gas Reporting Device:" + 10000
            dev = getChildDevice(newdni)

            if (!dev) {      
                dev = addChildDevice("verbem", "domoticzPowerReport", newdni, getHubID(), [name:passedName, label:passedName, completedSetup:true])
                pause 5
            } 
            state.devReportGas = newdni
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Purge devices that were removed from Domoticz
/*-----------------------------------------------------------------------------------------*/
def updateDeviceList() {
	if (state.alive == false) return		// never execute when OFFLINE

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
    def tempStateDevices = [:] << state.devices      
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
    // remove all USED devices in DZ, unused will remain
	temprspDevices.each { idx ->
		tempStateDevices.remove(idx)		    	
    }

	if (tempStateDevices.size() > 0) {
        def copyStateDevices = [:] << state.devices  

        tempStateDevices.each { idx, item ->
            log.error "removing from STATE " + idx
            copyStateDevices.remove(idx)
        }

        state.devices = copyStateDevices
        pause 5
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

/*-----------------------------------------------------------------------------------------*/
/*		REGULAR DOMOTICZ COMMAND HANDLERS FOR THE DEVICES
/*-----------------------------------------------------------------------------------------*/
def domoticz_poll(nid) {
    socketSend([request : "status", idx : nid])
    // also put out poll requests for composite parts of a device e.g. idxPower=idx, idxIlluminance
    if (state.devices[nid]?.idxPower) 		socketSend([request : "utilityCount", idx : state.devices[nid].idxPower])
    if (state.devices[nid]?.idxIlluminance) socketSend([request : "utilityCount", idx : state.devices[nid].idxIlluminance])
    if (state.devices[nid]?.idxTemperature) socketSend([request : "utilityCount", idx : state.devices[nid].idxTemperature])
    if (state.devices[nid]?.idxMotion) 		socketSend([request : "utilityCount", idx : state.devices[nid].idxMotion])
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
            if (state.devices[nid]?.MaxDimLevel != null) {
            	xLevel = xLevel/100*state.devices[nid].MaxDimLevel
                xLevel = xLevel.toInteger() + 1i
                log.info xLevel
            }
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
/*		Excecute the real request via the local HUB
/*-----------------------------------------------------------------------------------------*/
private def socketSend(passed) {
	//TRACE("[socketSend] entered with ${passed}")
	def hubPath = null
	def hubAction = null
    def hubCallback = [callback: callbackLog]
   
    switch (passed.request) {
		case "status":
			hubPath = "/json.htm?type=devices&rid=${passed.idx}"
            hubCallback = [callback: callbackStatus]
			break;
		case "utilityCount":
        	hubPath = "/json.htm?type=devices&rid=${passed.idx}"
            hubCallback = [callback: callbackForUCount] 
 			break;
		case "OptionTemperature":
        	hubPath = "/json.htm?type=devices&filter=temp"
            hubCallback = [callback: callbackForEveryThing]
            break;
		case "OptionUtility":
        	hubPath = "/json.htm?type=devices&filter=utility"
            hubCallback = [callback: callbackForEveryThing]
            break;
		case "OptionDevices":
        	hubPath = "/json.htm?type=devices&filter=all&used=true&order=Name"
            hubCallback = [callback: callbackForEveryThing]
            break;
        case "List":
        	hubPath = "/json.htm?type=devices&filter=all&used=true&order=Name"   // ALL USED Devices
            hubCallback = [callback: callbackList]
			break;
		case "scenes":
        	hubPath = "/json.htm?type=scenes"
            hubCallback = [callback: callbackForScenes]
 			break;
		case "roomplans":
        	hubPath = "/json.htm?type=plans&order=name&used=true"
            hubCallback = [callback: callbackForPlans]
 			break;
		case "roomplan":
        	hubPath = "/json.htm?type=command&param=getplandevices&idx=${passed.idx}"
            hubCallback = [callback: callbackForRoom]
 			break;
        case "sceneon":
        	hubPath = "/json.htm?type=command&param=switchscene&idx=${passed.idx}&switchcmd=On"
            break;
        case "sceneoff":
        	hubPath = "/json.htm?type=command&param=switchscene&idx=${passed.idx}&switchcmd=Off"
            break;            
		case "alive":
			hubPath = "/json.htm?type=devices&rid=0"
            hubCallback = [callback: aliveResponse]
			break;
        case ["off","on","stop", "toggle"]:
        	hubPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=${passed.request.capitalize()}"
            break;
        case "setlevel":
        	hubPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=Set%20Level&level=${passed.level}"
            break;
        case "setcolor":
        	hubPath = "/json.htm?type=command&param=setcolbrightnessvalue&idx=${passed.idx}&hex=${passed.hex}&iswhite=false&brightness=${passed.brightness}&saturation=${passed.saturation}"
            break;
        case "setcolorhue":
        	hubPath = "/json.htm?type=command&param=setcolbrightnessvalue&idx=${passed.idx}&hue=${passed.hue}&iswhite=false&brightness=${passed.brightness}&saturation=${passed.saturation}"
            break;
         case "setcolorwhite":
        	hubPath = "/json.htm?type=command&param=setcolbrightnessvalue&idx=${passed.idx}&hex=${passed.hex}&iswhite=true&brightness=${passed.brightness}&saturation=${passed.saturation}"
            break;
         case "counters":
         	hubPath = "/json.htm?type=graph&sensor=counter&idx=${passed.idx}&range=${passed.range}"
            hubCallback = [callback: callbackForCounters]
            break;
         case "SetPoint":  
         	hubPath = "/json.htm?type=setused&idx=${passed.idx}&setpoint=${passed.setpoint}&mode=ManualOverride&until=&used=true"
            break;
         case "SetLux":  
         	hubPath = "/json.htm?type=command&param=udevice&idx=${passed.idx}&svalue=${passed.lux}"
            break;
         case "SetTemp":  
         	hubPath = "/json.htm?type=command&param=udevice&idx=${passed.idx}&nvalue=0&svalue=${passed.temp}"
            break;
         case "SetHumidity":  
         	hubPath = "/json.htm?type=command&param=udevice&idx=${passed.idx}&svalue=0&nvalue=${passed.humidity}"
            break;
         case "Notification": 
         	def tWhen = 0
            def tValue = 0
            
            if (passed?.value > 0) {
            	tWhen = 2
                tValue = passed.value
                passed.action = "${passed.action}%20${tValue}"
            }
        	hubPath = "/json.htm?type=command&param=addnotification&idx=${passed.idx}&ttype=${passed.type}&twhen=${tWhen}&tvalue=${tValue}&tmsg=IDX%20${passed.idx}%20${passed.action}&tsystems=http&tpriority=0&tsendalways=false&trecovery=false"
            break;
        case ["SensorKWHNotification", "SensorLuxNotification", "SensorHumidityNotification"]:         
            hubPath = "/json.htm?type=command&param=addnotification&idx=${passed.idx}&ttype=5&twhen=1&tvalue=0&tmsg=SENSOR%20${passed.idx}&tsystems=http&tpriority=0&tsendalways=false&trecovery=false"
            break;         
        case "SensorTempNotification":         
            hubPath = "/json.htm?type=command&param=addnotification&idx=${passed.idx}&ttype=0&twhen=3&tvalue=-99&tmsg=IDX%20${passed.idx}%20%24value&tsystems=http&tpriority=0&tsendalways=false&trecovery=false"
            break;                
        case "ClearNotification":  
            hubPath = "/json.htm?type=command&param=clearnotifications&idx=${passed.idx}"
            break;
		case "Settings":  
            hubPath = "/json.htm?type=settings"
            hubCallback = [callback: callbackForSettings]
            break;
		case "CreateHardware":  
            hubPath = "/json.htm?type=command&param=addhardware&htype=15&port=1&name=SmartThings&enabled=true"
            break;
		case "UpdateHardware":  
            hubPath = "/json.htm?type=command&param=updatehardware&htype=15&name=SmartThings&enabled=true&idx=${state.dzHardwareIdx}&datatimeout=0&Mode1=0&Mode2=0&Mode3=0&Mode4=0&Mode5=0&Mode6=0"
            break;
		case "DeleteHardware":  
            hubPath = "/json.htm?type=command&param=deletehardware&idx=${state.dzHardwareIdx}"
            break;
		case "ListHardware":  
            hubPath = "/json.htm?type=hardware"
            hubCallback = [callback: callbackListHardware]
            break;        
		case "CreateVirtualDevice":  
            hubPath = "/json.htm?type=command&param=addswitch&hwdid=${state.dzHardwareIdx}&name=${passed.deviceName}&description=undefined&switchtype=${passed.switchType}&lighttype=0&housecode=80&unitcode=${passed.unitcode}"
            hubCallback = [callback: callbackVirtualDevices]
            log.info hubPath
			break;        
		case "CreateVirtualSensor":  
            hubPath = "/json.htm?type=createvirtualsensor&idx=${state.dzHardwareIdx}&sensorname=${passed.deviceName}&sensortype=${passed.sensorType}"
            hubCallback = [callback: callbackVirtualDevices]
            break;        
        default:
        	return
            break;           
	}
	
	if (hubPath == null) {
    	log.error "Error in calling socketsend!!! check rooPath/Request assignment"
        return
    }

    sendHubCommand(new physicalgraph.device.HubAction(method: "GET", path: hubPath, headers: [HOST: "${state.networkId}"], null, hubCallback))
}

void aliveResponse(evt) {
	state.alive = true
    state.aliveCount = 0
    
    if (state.aliveAgain == false) {
    	state.aliveAgain = true
    	log.info "Domoticz server is alive again"
        sendNotification("Domoticz server is responding again", [method: "push"])
        
        if (state.devicesOffline) devicesOnline()
        socketSend([request : "List"])
    }
}

void aliveChecker(evt) {
	def cycles 
	if (state?.scheduleCycle == null)  cycles = 59i else cycles = state.scheduleCycle + 1
    
	if (state.alive == false && state.aliveCount > 1) {
    	state.aliveAgain = false
    	log.error "Domoticz server is not responding"
        sendNotification("Domoticz server is not responding", [method: "push"])
        if (!state.devicesOffline) devicesOffline()
    }
    
    if (state.aliveCount) state.aliveCount = state.aliveCount + 1
    else state.aliveCount = 1
    
    socketSend([request : "alive"])
    state.alive = false

	// -----------------------------------------------
	// standard scheduling, not using ST schedule methods
	// -----------------------------------------------

    runIn(30, scheduledListSensorOptions)
    
    if (cycles % 60 == 0) {
        runIn(10,refreshDevicesFromDomoticz)
        
        if (state.devReportPower != null) {      
        	runIn(40, scheduledPowerReport)
        }
        if (state.devReportGas != null) {      
        	runIn(60+40, scheduledGasReport)
        }
    }
    state.scheduleCycle = cycles

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
        
	socketSend([request : "List"])
    socketSend([request : "scenes"])
	runIn(2,updateDeviceList)
    
}

/*-----------------------------------------------------------------------------------------*/
/*		Domoticz will send an notification message to ST for all devices THAT HAVE BEEN SELECTED to do that
/*-----------------------------------------------------------------------------------------*/
def eventDomoticz() {

	if (settings?.domoticzVirtualDevices == true) {
        if (params.message.contains("IDX ") && params.message.split().size() == 3) {
            def idx = params.message.split()[1]
            def status = params.message.split()[2]
            //SWITCHES
            def item = state.virtualDevices.find {key, item -> item.idx == idx && item.type == "switch" }
            if (item != null) {
                settings.dzDevicesSwitches.each { device ->
                    if (device.deviceNetworkId == item.value.dni) {
                        if (device.currentValue("switch").toUpperCase() != status.toUpperCase()) {		// status was changed in DZ for a virtual device
                            if (status == "on") device.on() else device.off()
                        }
                    }
                }
                return
            }
            //LOCKS
            def lock = state.virtualDevices.find {key, lock -> lock.idx == idx && lock.type == "lock" }
            if (lock != null) {
                log.debug "Found virtual device ${lock.value.dni} of lock type"
                settings.dzDevicesLocks.each { device ->
                    if (device.deviceNetworkId == lock.value.dni) {
                        if (device.currentValue("lock").toUpperCase() != status.toUpperCase()) {		// status was changed in DZ for a virtual device
                            log.debug "change status of virtual device ${device.displayName}"
                            if (status == "Locked") device.lock() else device.unlock()
                        }
                    }
                }
                return
            }
        }
   	}
    
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
            	if (switchTypeVal != 7) attr = "switch"   // 7 is a dimmer , just request complete status to catch all states
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
            	// multiselector switches will have a level in their custom notification
            	getChildDevice(dni).sendEvent(name: "level", value: level)
                domoticz_poll(idx)
                }
        }
        else {
            socketSend([request : "status", idx : idx]) 
        } 
    }
    else if (params.message.contains("SENSOR ") && params.message.split().size() >= 2) { 
            def idx = params.message.split()[1]
            socketSend([request : "utilityCount", idx : idx])
    	}  
    else {
		TRACE("[eventDomoticz] no custom message in Notification (unknown device in ST) perform List ${params.message}")
		// get the unknown device defined in ST (if part of selected types)    
        socketSend([request : "List"])
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