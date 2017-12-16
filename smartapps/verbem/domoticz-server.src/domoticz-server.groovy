/*
 *  Domoticz (server)
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
 *	
	V4.05	check for null on result in onLocationEvtForEveryThing
    V4.06	[updateDeviceList] remove obsolete childs, ones that are unused in Domoticz
 	V4.07	Adding linkage to power usage devices
    V4.08	checking for usage types returned fromDZ that should n0t be added
 */
 
import groovy.json.*
import java.Math.*

private def runningVersion() {"4.08"}

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
    page name:"setupNefitEasy"
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
    /*-------------------------------------------------------------------------------------*/
    /* get oAUTH token which you need to plug in Domoticz Notifications system
    /* to do this go to domotics Setup / settings / notifications and add it to the HTTP 
    /* custom notification with access_token=, together with the url for smartthings and #MESSAGE
	/* will look like this:
    /*
	/* https://graph.api.smartthings.com/api/smartapps/installations/put the ST App id here/EventDomoticz?access_token=put the oAuth token here&message=#MESSAGE
    /*
    /* to have a switch or other device in domoticz report back the status you have to activate the
    /* notifications on the device itself also for on and off (http)
    /* look in domoticz under switches and in every switch you will see the notifications 
    /*
    /*-------------------------------------------------------------------------------------*/

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
	state.domoticzRefresh = false

	return dynamicPage(pageProperties) {
        section {
            href "setupDomoticz", title:"Configure Domoticz Server", description:"Tap to open"
            href "setupDeviceRequest", title:"Add all selected Devicetypes or those in selected Rooms", description:"Tap to open"
            if (state.devices.size() > 0) {
                href "setupListDevices", title:"List Installed Devices", description:"Tap to open"
            }
            if (state?.listSensors?.size() > 0) {
            	href "setupCompositeSensors", title:"Create Composite Sensors", description:"Tap to open"	
            }
            href "setupRefreshToken", title:"Revoke/Recreate Access Token", description:"Tap to open"
        	}
        section([title:"Options", mobileOnly:true]) {
            label title:"Assign a name", required:false
        	}
        section("About") {
            paragraph "${app.name}. ${textVersion()}\n${textCopyright()}"
        	}
    	}
}

/*-----------------------------------------------------------------------------------------*/
/*		SET Up Configure NefitEasy PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupNefitEasy() {
    TRACE("[setupNefitEasy]")
    def textPara1 =
        "Enter IP address and TCP port of your Nefit Easy Server, then tap " +
        "Next to continue."

    def inputNE = [
        name        : "nefitEasy",
        submitOnChange : true,
        type        : "bool",
        title       : "Support for Nefit Easy Thermostat?",
        defaultValue: false
    ]
    
    def inputNEAddress = [
        name        : "nefitEasyIpAddress",
        type        : "string",
        title       : "Local Nefit Easy Server Address",
        defaultValue: settings.domoticzIpAddress
    ]

    def inputNEPort = [
        name        : "nefitEasyTcpPort",
        type        : "number",
        title       : "Local Nefit Easy Server Port",
        defaultValue: "3000"
    ]
    
    def pageProperties = [
        name        : "setupNefitEasy",
        title       : "Configure Nefit Easy Server",
        nextPage    : "setupDomoticz",
        install     : false,
        uninstall   : false
    ]

    return dynamicPage(pageProperties) {
      section {
      		input inputNE
            if(nefitEasy) {
            	input inputNEAddress
            	input inputNEPort
                }
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
      		state.listSensors.each { key, item ->
        	if (item.type == "domoticzSensor" || item.type == "domoticzMotion" ) {
                def iMap = item as Map
                paragraph "Compose ${iMap.type}/${iMap.name} with IDX ${key}"
                if (state.optionsPower) {input "idxPower[${key}]", "enum", title:"Add power usage?", options: state.optionsPower, required: false}
                if (state.optionsLux) {input "idxIlluminance[${key}]", "enum", title:"Add Lux measurement?", options: state.optionsLux, required: false}
                if (state.optionsTemperature && iMap.type != "domoticzSensor") input "idxTemperature[${key}]", "enum", title:"Add Temperature measurement?",options: state.optionsTemperature , required: false
                if (state.optionsMotion && iMap.type != "domoticzMotion") input "idxMotion[${key}]", "enum", title:"Add motion detection?", options: state.optionsMotion, required: false
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
        options	    : ["Window Coverings", "On/Off/Dimmers/RGB", "Smoke Detectors", "Contact Sensors", "Dusk Sensors", "Motion Sensors", "(Virtual) Sensors"],
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
            href "setupNefitEasy", title:"Configure Nefit Easy Thermostat", description:"Tap to open"
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

	if (domoticzTypes.contains("(Virtual) Sensors")) {
        runEvery10Minutes(scheduledSensorRefresh)
    }

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
        
        if (settings.nefitEasy == true) {
        	section("Nefit Easy") {paragraph thermostats}
        }
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
    state.nefitNetworkId = settings.nefitEasyIpAddress + ":" + settings.nefitEasyTcpPort
    updateDeviceList()
    
    if (settings.nefitEasy == true) {
    	TRACE("[initialize] Nefit Easy Send Serialnumber")
    	nefitEasySend("serialnumber",0)	
    	}
        
    state.alive = true
    state.aliveAgain = true
    state.devicesOffline = false
	
    addReportDevices()
    
    unschedule()
    
    runEvery1Hour(refreshDevicesFromDomoticz)
    
    runEvery1Minute(aliveChecker)
    
    runEvery1Minute(refreshUtilityCounts)

	state.optionsLux = [:]
    state.optionsMotion = [:]
    state.optionsTemperature = [:]
    state.optionsCarbon1 = [:]
    state.optionsCarbon2 = [:]
    state.optionsPower = [:]

    scheduledListSensorOptions()
    runEvery1Minute(scheduledListSensorOptions)
    
    if (domoticzTypes.contains("(Virtual) Sensors")) {
    	scheduledSensorRefresh()
        runEvery10Minutes(scheduledSensorRefresh)
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		DeleteChilds command
/*-----------------------------------------------------------------------------------------*/
private def deleteChilds() {
    TRACE("[deleteChilds]")

    // delete all child devices
    def devices = getChildDevices()
    devices?.each {
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (e) {
            log.error "[deleteChilds] Cannot delete device ${it.deviceNetworkId}. Error: ${e}"
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
	TRACE("[refreshUtilityCounts]")
	def listIdx = [:]

	settings.each { k, v ->
    	if (k.contains("idxIlluminance") || k.contains("idxPower") || k.contains("idxTemperature") || k.contains("idxMotion")) {
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
        }
    }
    
	state.devices.each { key, item ->
    	if (item?.idxPower != null) { listIdx[item.idxPower] = [name: key]}
		if (item?.idxMotion != null) { listIdx[item.idxMotion] = [name: key]}
    	if (item?.idxIlluminance != null) {listIdx[item.idxIlluminance] = [name: key]}
    	if (item?.idxTemperature != null ) {listIdx[item.idxTemperature] = [name: key]}
    }
    
    listIdx.each {k, v -> socketSend([request : "utilityCount", idx : k])} 

}

void scheduledListSensorOptions() {
	TRACE("[scheduledListSensorOptions]")
	socketSend([request : "OptionUtility"])
    socketSend([request : "OptionTemperature"])
    socketSend([request : "OptionDevices"])
}

void scheduledSensorRefresh() {
	TRACE("[scheduledSensorRefresh]")
    //sendHubCommand(new physicalgraph.device.HubAction("lan discovery ssdp:all", physicalgraph.device.Protocol.LAN))
	  
	if (domoticzTypes.contains("(Virtual) Sensors")) {
        socketSend([request : "listsensors"])
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
            
            if (!stateDevice) { //XIAOMI plugs?? plug ID is xxAABBCC, usage idx is AABBCCxx
            	def ID = "${utility.ID.substring(6)}${utility.ID.substring(0,7)}"                
                stateDevice = state.devices.find {key, item -> 
                    item.deviceId == ID
                }
            }

			if (stateDevice) {
                stateDevice = stateDevice.toString().split("=")[0]
                def dni = state.devices[stateDevice].dni
                getChildDevice(dni).sendEvent(name:"power", value:"${utility.Usage}")
            }
            else {
            	TRACE("[onLocationEvtForUCount] Not found kWh ${utility.ID} ${utility.idx}")
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
            pause 5
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
    state.statusGrpRsp = response.result

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
	TRACE("[onLocationEvtForDevices]")

	def compareTypeVal
    def SubType
	if (!state.listSensors) state.listSensors = [:]

	statusrsp.result.each { 
        compareTypeVal = it?.SwitchTypeVal
        //if (it?.SwitchTypeVal?.value) compareTypeVal = it.SwitchTypeVal
        if (it?.Temp) compareTypeVal = 99 
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
            case 99:			//	Sensors
                if (domoticzTypes.contains("(Virtual) Sensors")) addSwitch(it.idx, "domoticzSensor", it.Name, "Active", it.Type, it)
                state.listSensors[it.idx] = [name: it.Name, idx: it.idx, type: "domoticzSensor"]
                break
            default:
                log.error "[onLocationEvtForDevices] non handled SwitchTypeVal ${compareTypeVal} ${it}"
            break
        }	
    }            
}

def onLocationEvtForEveryThing(evt) {

    def response = getResponse(evt)
    def kwh = 0f
    def watt = 0f
    
	if (response?.result == null) return

	TRACE("[onLocationEvtForEveryThing] Domoticz response with Title : ${response.title}, number of items returned ${response.result.size()}")

    response.result.each {
    	
        if (it?.SubType == "Lux") {
        	state.optionsLux[it.idx] = "${it.idx} : ${it.Name}"
        }
        if (it?.SwitchTypeVal == 8) {
        	state.optionsMotion[it.idx] = "${it.idx} : ${it.Name}"
        }	
        if (it?.Temp) {
        	state.optionsTemperature[it.idx] = "${it.idx} : ${it.Name}"
        }
        if (it?.SwitchTypeVal == 5) {
        	state.optionsCarbon1[it.idx] = "${it.idx} : ${it.Name}"
        	state.optionsCarbon2[it.idx] = "${it.idx} : ${it.Name}"
        }
        if (it?.SubType == "kWh") {	
        	state.optionsPower[it.idx] = "${it.idx} : ${it.Name}" 
            kwh = kwh + Float.parseFloat(it.Data.split(" ")[0])
            watt = watt + Float.parseFloat(it.Usage.split(" ")[0])
            //add idxPower to real device by matching the ID
			def ID = it?.ID
			def stateDevice = state.devices.find {key, item -> 
		    	item.deviceId == ID
    		}
            
            if (!stateDevice) { // XIAOMI try
            	ID = "${it?.ID.substring(6)}${it?.ID.substring(0,7)}"                
                stateDevice = state.devices.find {key, item -> 
                    item.deviceId == ID
                }
            }
            
            def IDX = it.idx
            if (stateDevice) {
            	state.devices[stateDevice.key].idxPower = IDX
            }
		}

    }
    if (kwh > 0) {
        getChildDevice(state.devReportPower).sendEvent(name:"powerTotal", value:"${kwh.round(3)}")
        getChildDevice(state.devReportPower).sendEvent(name:"power", value:"${watt.round(2)}")
   	}
}

def onLocationForCounters(evt) {
    def response = getResponse(evt)
	TRACE("[onLocationForCounters]")
	if (response.result == null) return
    
    TRACE("[onLocationForCounters] ${response.result}")
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
 
 	if (passedDomoticzStatus instanceof java.util.Map) {
    	 
    	if (passedDomoticzStatus?.ID != null) {
        	deviceId = passedDomoticzStatus.ID
        }
        
        subType = passedDomoticzStatus?.SubType
        
    	if (passedDomoticzStatus?.SwitchTypeVal != null) {
        	switchTypeVal = passedDomoticzStatus.SwitchTypeVal
            deviceType = "switch"
        }
        else 	if (passedFile == "domoticzSensor") {
                	deviceType = "sensor"
        		}
    }

    if (getChildDevice(newdni)) {      
        TRACE("[addSwitch] Updating child device ${addr}, ${passedFile}, ${passedName}, ${passedStatus}")
        
        if (!state.devices[addr].deviceId) state.devices[addr].deviceId = deviceId
        def existingDev = getChildDevice(newdni)
        if (passedName != existingDev.name) {
        	existingDev.label = passedName
            existingDev.name = passedName
        }
        
        def attributeList = createAttributes(getChildDevice(newdni), passedDomoticzStatus, addr)
        if (passedType == "RFY") {attributeList.put('somfySupported', true)}

        generateEvent(getChildDevice(newdni), attributeList)
    }
    else if ((state.listOfRoomPlanDevices?.contains(addr) && settings.domoticzRoomPlans == true) || settings.domoticzRoomPlans == false) {
        
        try {
            TRACE("[addSwitch] Creating child device ${addr}, ${passedFile}, ${passedName}, ${passedStatus}, ${passedDomoticzStatus}")
            def dev = addChildDevice("verbem", passedFile, newdni, getHubID(), [name:passedName, label:passedName, completedSetup:!state.domoticzRefresh])
            pause 5
            def attributeList = createAttributes(dev, passedDomoticzStatus, addr)
            if (passedType == "RFY") {attributeList.put('somfySupported', true)}
            
            generateEvent(dev, attributeList)
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
       	TRACE("[createAttributes] ${domoticzDevice.getSupportedAttributes()} PASSED NOT A MAP : RETURNING")
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
				//if (domoticzStatus?.LevelInt == 0 && domoticzDevice.hasAttribute("level")) {
                //	attributeList.put('level', v)
                //	}
				if (domoticzStatus?.LevelInt > 0 && v == 0 && domoticzDevice.hasAttribute("level")) attributeList.put('level',domoticzStatus?.LevelInt)
                else if (domoticzDevice.hasAttribute("level")) attributeList.put('level', v)
                	
                if (domoticzStatus?.LevelNames) {
                	def ix = v / 10
                    def status = domoticzStatus?.LevelNames.tokenize('|')
                    log.trace status + ix.toInteger() + status[ix.toInteger()]
                	attributeList.put('selectorState', status[ix.toInteger()])
                    }
            	break;
            case "Temp":
            	double vd = v               
				if (domoticzDevice.hasAttribute("temperature")) attributeList.put('temperature', vd.round(1))
            	break;
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
       }
    
    }
	return attributeList
}

private def addReportDevices() {

	if (settings.domoticzReportPower) {
        TRACE("[addReportDevices]")

        def passedName = "Power Reporting Device"
        def newdni = app.id + ":Power Reporting Device:" + 10000
		def dev = getChildDevice(newdni)
        
        if (!dev) {      
            try {
                    dev = addChildDevice("verbem", "domoticzPowerReport", newdni, getHubID(), [name:passedName, label:passedName, completedSetup:!state.domoticzRefresh])
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


private def addNefitEasy(addrNefitDevice) {
/*-----------------------------------------------------------------------------------------*/
/*		Execute the real add or status update of the Nefit Easy device
/*-----------------------------------------------------------------------------------------*/
    TRACE("[addNefitEasy]")

    def passedName = "Nefit Easy " + addrNefitDevice
    def passedDomoticzStatus = [:]    
    def newdni = app.id + ":${addrNefitDevice}:" + 10000
    
    if (!getChildDevice(newdni)) {      
        try {
                TRACE("[addSwitch] Creating child device ${params}")
                def dev = addChildDevice("verbem", "domoticzNefitEasy", newdni, getHubID(), [name:passedName, label:passedName, completedSetup:!state.domoticzRefresh])
                pause 5
            } 
        catch (e) 
            {
                log.error "[addSwitch] Cannot create child device. ${newdni} Error: ${e}"
                return 
            }
        }

    state.devices[addrNefitDevice] = [
        'dni'   : newdni,
        'ip' : settings.nefitEasyIpAddress,
        'port' : settings.nefitEasyTcpPort,
        'idx' : 10000,
        'type'  : "thermostat",
        'deviceType' : "domoticzNefitEasy",
        'subType' : "switch",
        'switchTypeVal' : 999
    ]
	pause 1
}

/*-----------------------------------------------------------------------------------------*/
/*		Purge devices that were removed from Domoticz
/*-----------------------------------------------------------------------------------------*/
private def updateDeviceList() {
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
    
    if(temprspDevices) log.trace temprspDevices?.size() + " Devices in response : " + temprspDevices?.collect {it.idx as int}.sort()
    
    if (temprspGroups) log.trace temprspGroups?.size() + " Groups in response : " + temprspGroups?.collect {it.idx as int}.sort()
    
    TRACE("${tempStateDevices?.size()} ${state Devices} : ${tempStateDevices?.collect {it.value.idx as int}.sort()}")
       
    allChildren.each { child ->
    	findrspDevice = temprspDevices.find {item -> item.idx == child.deviceNetworkId.split(":")[2] }
    	findrspGroup = temprspGroups.find {item -> item.idx == child.deviceNetworkId.split(":")[2] }
        idx10k = child.deviceNetworkId.split(":")[2]

        if (idx10k != "10000") {   // special devices that should not be deleted automatically have idx = 10000
            if (!findrspDevice && !findrspGroup) {
                TRACE("[updateDeviceList] NOT FOUND ${child.name} delete childDevice")
                deleteChildDevice(child.deviceNetworkId)
            }
      	}
    }
    
    tempStateDevices.each { k,v ->
        inStatusrsp = false
        
        if (temprspDevices) findrspDevice = temprspDevices.find {dev ->	dev.idx == k }
        if (tmprspGroups) findrspGroup = temprspGroups.find {group -> group.idx == k }

        if (findrspDevice || findrspGroup) inStatusrsp = true
        
        if (v.hasProperty("deviceType")) {if (v.deviceType == "domoticzNefitEasy") inStatusrsp = true} // Nefit Easy Devices do not clean up yet.
        
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
        if (v.type == type) {
        	def dev = getChildDevice(v.dni)
            
            if (!dev) TRACE("[getDeviceListAsText] ${v.dni} NOT FOUND")
            
            if (type == "thermostat") {
            	TRACE("[getDeviceListAsText] ${dev?.displayName} thermostat found ")
            	s += "${dev?.displayName}\n"
                }
            else {s += "${k.padLeft(4)} - ${dev?.displayName} - ${v.deviceType}\n"}          	
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
    if (xLevel.toInteger() == 0) {socketSend([request : "off", idx : nid])}
    else	if (state.devices[nid].subType == "RFY") 
    		{
            	socketSend([request : "stop", idx : nid])
            } 
    		else 
            {
               	socketSend([request : "setlevel", idx : nid, level : xLevel])
                //socketSend([request : "on", idx : nid])
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

/*-----------------------------------------------------------------------------------------*/
/*		Excecute The real request via the local HUB
/*-----------------------------------------------------------------------------------------*/
private def socketSend(passed) {
    //TRACE("[socketSend] => ${passed}")
    
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
            break;
        case "sceneoff":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchscene&idx=${passed.idx}&switchcmd=Off"  // "SwitchScene"
            break;            
		case "status":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Status%20for%20${passed.idx}"
			rooPath = "/json.htm?type=devices&rid=${passed.idx}" 									// "Devices"
			break;
		case "alive":
        	rooLog = ""
			rooPath = "/json.htm?type=devices&rid=0" 									// "Devices"
            hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: aliveResponse] )
			break;
        case "off":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Off%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=Off"	// "SwitchLight"
            break;
        case "on":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=On"		// "SwitchLight"
            break;
        case "stop":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Stop%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=Stop"		// "SwitchLight"
            break;
        case "setlevel":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Level%20${passed.level}%20for%20${passed.idx}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${passed.idx}&switchcmd=Set%20Level&level=${passed.level}"  // "SwitchLight"
            break;
        case "setcolor":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ColorHex%20${passed.hex}%20for%20${passed.idx}%20brightness=${passed.brightness}%saturation=${passed.saturation}"
        	rooPath = "/json.htm?type=command&param=setcolbrightnessvalue&idx=${passed.idx}&hex=${passed.hex}&iswhite=false&brightness=${passed.brightness}&saturation=${passed.saturation}" // "SetColBrightnessValue"
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
         	rooPath = "/json.htm?type=graph&sensor=counter&idx=${passed.idx}&range=${passed.range}&method=2"
			hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocationForCounters] )
            break;
        default:
        	return
            break;
           
	}

    if (hubAction == null) hubAction = new physicalgraph.device.HubAction(method: "GET", path: rooPath, headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"], null, [callback: onLocation] )
        
	def mSeconds = 0
    
    sendHubCommand(hubAction)

	if (mSeconds > 0 ) {pause(mSeconds)}
    else {
        if (rooLog != "") {
            def hubActionLog = new physicalgraph.device.HubAction(
                method: "GET",
                path: rooLog,
                headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"],
                null,
                [callback: callbackLog])

            sendHubCommand(hubActionLog)
            }
		}
    return null
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

/*-----------------------------------------------------------------------------------------*/
/*	CALLBACK HANDLERS
/*-----------------------------------------------------------------------------------------*/
void callbackNefit(evt) {
	def response = getResponse(evt)
    TRACE("[onLocation] Nefit Server response")  

    if (response.type == "refEnum") {
        response.references.each { 
            nefitEasySend("dumpReferences", it.id)
        }
    }
    else {        	
        switch (response.id) 
        {
            case "/system/appliance/serialnumber":
                addNefitEasy(response.value)
                break;
            case null : // response from API/STATUS, that response does not contain an response.ID!!
                def nefit = getChildDevices()?.find {it.typeName == "domoticzNefitEasy"}
                def event = [:]
                log.debug response["in house temp"]
                event << ["temperature": response["in house temp"]]
                log.debug response["user mode"]
                log.debug response["clock program"]
                nefit.switchMode()
                
                log.debug response["hot water active"]
                log.debug response["holiday mode"]
                nefit.generateEvent(event)
                
                break;
            default:
                log.debug response
            	break;
        }
    }
    return
}

void callbackList(evt) {

	TRACE("[callbackList]")
    def response = getResponse(evt)
    state.statusrsp = response.result  
    onLocationEvtForDevices(response)    
    return
}

void callbackLog(evt) {
	// dummy handler for addlogmessages, it prevents these responses from going into "normal" response processing
	return
}

void aliveResponse(evt) {
	state.alive = true
    state.aliveCount = 0
    if (state.aliveAgain == false) {
    	state.aliveAgain = true
    	TRACE("Domoticz server is alive again")
        if (state.devicesOffline) devicesOnline()
        socketList()
    	}
}

void aliveChecker(evt) {
	if (state.alive == false && state.aliveCount > 1) {
    	state.aliveAgain = false
    	TRACE("Domoticz server is not responding")
        if (!state.devicesOffline) devicesOffline()
        //return
    	}
    
    if (state.aliveCount) state.aliveCount = state.aliveCount + 1
    else state.aliveCount = 1
    
    socketSend([request : "alive"])
    state.alive = false
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
/*	NEFIT COMMAND HANDLERS
/*-----------------------------------------------------------------------------------------*/

def nefitEasy_poll() {
	TRACE("[nefitEasy Poll]")
    nefitEasySend("status", 0)
}

def nefitEasy_setHold(heatingValue, deviceId, sendHoldType) {
	TRACE("[nefitEasy setHold]")
}

def nefitEasy_resumeProgram(deviceId) {
	TRACE("[nefitEasy resumeProgram]")
}

def nefitEasy_availableModes(modes) {
	TRACE("[nefitEasy availableModes]")
	return ["auto", "eco", "holiday", "manual"]
}

def nefitEasy_setMode(action, deviceId) {
	TRACE("[nefitEasy setMode] ${action}")
	return true
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute The real Nefitrequest via the local HUB
/*-----------------------------------------------------------------------------------------*/
private def nefitEasySend(message, addrNefitEasy) {
    TRACE("[nefitEasySend] ${message} to ${addrNefitEasy}") 
	def path = ""
    
    switch (message) {
		case "serialnumber":
        	path = "/bridge/system/appliance/serialnumber"   
 			break;
        case "bridge":
        	path = "/bridge"
            break;
        case "dumpReferences":
        	path = "/bridge${addrNefitEasy}"
            break;
		case "status":
        	path = "/api/status"   
 			break;
        }
            
    def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: path,
        headers: [HOST: "${settings.nefitEasyIpAddress}:${settings.nefitEasyTcpPort}"],
        null,
        [callback: callbackNefit])
	  
    sendHubCommand(hubAction)
    return null
}

/*-----------------------------------------------------------------------------------------*/
/*		
/*-----------------------------------------------------------------------------------------*/
void refreshDevicesFromDomoticz() {

	TRACE("[refreshDevicesFromDomoticz] Entering routine")
    socketSend([request : "roomplans"])
    pause 5

    
	state.domoticzRefresh = true
	state.listOfRoomPlanDevices = []
    settings.domoticzPlans.each { v -> 
    	log.trace v
        state.statusPlansRsp.each {
        	log.trace it
            if (v == it.Name) {
                socketSend([request : "roomplan", idx : it.idx])
                pause 10
            	}
            pause 2
        	}
    	}

	updateDeviceList()
      
	socketList()
    pause 10
    socketSend([request : "scenes"])
	pause 10
    state.domoticzRefresh = false

}

/*-----------------------------------------------------------------------------------------*/
/*		Domoticz will send an event message to ST for all devices THAT HAVE BEEN SELECTED to do that
/*-----------------------------------------------------------------------------------------*/
def eventDomoticz() {
	TRACE("[eventDomoticz] " + params.message)
    def existingChild = false
    
    if (params.message.contains(" ALARM")) {
        def parts = params.message.split(" ALARM")
        def devName = parts[0]
        def children = getChildDevices()?.find {it.name == devName || it.label == devName }

        children.each {
            existingChild = true
            def idx = it.deviceNetworkId.split(":")[2]
            socketSend([request : "status", idx : idx])
        }
    }	
    else if (params.message.contains(" movement")) {
        def parts = params.message.split(" movement")
        def devName = parts[0]
        //ef children = getChildDevices()
        def children = getChildDevices()?.find {it.name == devName || it.label == devName }

		children.each { 
            existingChild = true
            def idx = it.deviceNetworkId.split(":")[2]
            socketSend([request : "status", idx : idx])
        }
    }
	else if (params.message.contains(" Closed") || params.message.contains(" Open")) {
    	def devName = ""
        if (params.message.contains(" Closed")) devName = params.message.replace(" Closed", "")
        if (params.message.contains(" Open")) devName = params.message.replace(" Open", "")
        //def children = getChildDevices()
        def children = getChildDevices()?.find {it.name == devName || it.label == devName }
		
		children.each { 
            existingChild = true
            def idx = it.deviceNetworkId.split(":")[2]
            socketSend([request : "status", idx : idx])
        }
    }        
    else if (params.message.contains(" >> ")) {
        def parts = params.message.split(" >> ")
        def devName = parts[0]
        //def children = getChildDevices()
        def children = getChildDevices()?.find {it.name == devName || it.label == devName }
        
		children.each {     	
            existingChild = true
            def idx = it.deviceNetworkId.split(":")[2]
            socketSend([request : "status", idx : idx])
         }        
    }
    if (existingChild == false) socketList()		// get the unknown device defined in ST (if part of selected types)
    return null
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
    return "Copyright (c) 2017 Martin Verbeek"
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