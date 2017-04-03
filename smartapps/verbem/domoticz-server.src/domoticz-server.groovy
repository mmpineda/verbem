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
	WIP V4.00	Include new Nefit Easy thermostat (use Robert Klep Nefit Easy HTTP Server that (can) run on raspberry)
    V3.12	Add (virtual) sensors
    V3.11	Fix in Domoticz_Stop function
	V3.10	Change to singleinstance App and enabled oauth
 	V3.09	Changes to IP/Port calls to Domoticz
 	V3.08	Add support for inverted blinds (SwitchTypeVal 6 and 16)
	V3.07	Catch error when Oauth is not enabled
	V3.06	Clean up code
	V3.05	Corrected typo in routine that selects which types to add
	V3.03	Corrected event processing for Motion and Contact
	V3.02	Implemented a refresh for devices in Domoticz every hour, these will be shown as "not completed" devices
	V3.01	Implemented batterylevel as info that can be passed as an event to the devices
 	V3.00	Add more granularity in adding devices from DZ to ST, Select on SwitchTypeVal and Room Plan
 			complete restructure of the onLocation Event to have more clear event processing
            Added support for Smoke Detector, Motion Sensor and Contact Sensor
 */
 
import groovy.json.*
import java.Math.*

private def textVersion() { return "Version 3.12"}

definition(
    name: "Domoticz Server",
    namespace: "verbem",
    author: "Martin Verbeek",
    description: "Connects to local Domoticz server and define Domoticz devices in ST",
    category: "My Apps",
    singleInstance: true,
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
    subscribe(location, null, onLocation, [filterEvents:false])
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
        title       : "Local Nefit Easy IP Address",
        defaultValue: "0.0.0.0"
    ]

    def inputNEPort = [
        name        : "nefitEasyTcpPort",
        type        : "number",
        title       : "Local Nefit Easy TCP Port",
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
/*		SET Up Configure Domoticz PAGE
/*-----------------------------------------------------------------------------------------*/
private def setupDomoticz() {
    TRACE("[setupDomoticz]")

	if (settings.containsKey('domoticzIpAddress')) {
    	socketSend("roomplans",0,0,0,0)
		pause 10
    	}
    
    def textPara1 =
        "Enter IP address and TCP port of your Domoticz Server, then tap " +
        "Next to continue."

    def inputIpAddress = [
        name        : "domoticzIpAddress",
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
        options	    : ["Window Coverings", "On/Off/Dimmers/RGB", "Smoke Detectors", "Contact Sensors", "Motion Sensors", "(Virtual) Sensors"],
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
            input inputRoomPlans
            if (domoticzRoomPlans && settings.containsKey('domoticzIpAddress')) input inputPlans
            input inputGroup
            input inputScene
//            href "setupNefitEasy", title:"Configure Nefit Easy Thermostat", description:"Tap to open"
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
    if (domoticzRoomPlans) textHelp = textHelp + "Devices in Rooms ${domoticzPlans} with the above types \n\n"
    if (domoticzScene) textHelp = textHelp + "Scenes will be added. \n\n"
    if (domoticzGroup) textHelp = textHelp + "Groups will be added. \n\n"

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
        domoticzPlans.each { v ->       	
        	state.statusPlansRsp.each {
            if (v == it.Name) {
            	socketSend("roomplan", it.idx, 0,0,0)
                pause 10
                }
        	}
          }
        }

	socketSend("list",0,0,0,0)
    pause 10
    socketSend("scenes",0,0,0,0)
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

def installed() {
    TRACE("[installed]")

    initialize()
}

def updated() {
    TRACE("[updated]")

    unsubscribe()
    initialize()
    runEvery1Hour(refreshDevicesFromDomoticz)
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

	// Subscribe to location events with filter disabled
    TRACE ("[Initialize] Subcribe to Location")
    subscribe(location, null, onLocation, [filterEvents:false])

    if (state.accessToken) {
        state.urlCustomActionHttp = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"
	}
    
	state.setStatusrsp = false
    state.setup.installed = true
    state.networkId = settings.domoticzIpAddress + ":" + settings.domoticzTcpPort
    updateDeviceList()

}
/*-----------------------------------------------------------------------------------------*/
/*		Debugging DeleteChilds command
/*-----------------------------------------------------------------------------------------*/
def deleteChilds() {
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
/*		List the child devices in the SMARTAPP
/*-----------------------------------------------------------------------------------------*/
private def setupListDevices() {
    TRACE("[setupListDevices]")
	updateDeviceList()
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
    return dynamicPage(pageProperties) {
        section("Switch types") {
            paragraph switches
        	}
        section("Sensors") {
        	paragraph sensors
        	}
    }
}

/*-----------------------------------------------------------------------------------------*/
/*		Handle the location events that are being triggered from sendHubCommand response
/*-----------------------------------------------------------------------------------------*/
def onLocation(evt) {
//	log.debug evt.json
    def description = evt.description
    def hMap = stringToMap(description)

	try {
        def header = new String(hMap.headers.decodeBase64())
    } catch (e) {
        return
    }
    
    def body = new String(hMap.body.decodeBase64())
    def response = new JsonSlurper().parseText(body)   
    def statusrsp = response.result
    
    if (statusrsp == null) return

    TRACE("[onLocation evt] Title : ${response.title}") 

    switch (response.title) 
    	{
        case "GetPlanDevices":
        		onLocationEvtForRoom(statusrsp)
        		break;
        case "Plans":
    			onLocationEvtForPlans(statusrsp)
		        break;
        case "Devices":
				onLocationEvtForDevices(statusrsp)    			
		        break;
        case "Scenes":
    			onLocationEvtForScenes(statusrsp)
		        break;
    	}	

return

}
/*-----------------------------------------------------------------------------------------*/
def scheduledSensorRefresh() {

    if (domoticzTypes.contains("(Virtual) Sensors")) {
        socketSend("listsensors",0,0,0,0)
        pause 10
    }

return
}

/*-----------------------------------------------------------------------------------------*/
/*		Build the idx list for Devices that are part of the selected room plans
/*-----------------------------------------------------------------------------------------*/
private def onLocationEvtForRoom(statusrsp) {

	statusrsp.each {
        TRACE("[onLocationEvtForRoom] Device ${it.Name} with idx ${it.devidx}")
        state.listOfRoomPlanDevices.add(it.devidx)
    }

}

/*-----------------------------------------------------------------------------------------*/
/*		Get Room Plans defined into Selectables for setupDomoticz
/*-----------------------------------------------------------------------------------------*/
private def onLocationEvtForPlans(statusrsp) {

state.statusPlansRsp = statusrsp
state.listPlans = []

	statusrsp.each {
        TRACE("[onLocationEvtForPlans] ${it.Devices} devices in room plan ${it.Name} with idx ${it.idx}")
        state.listPlans.add(it.Name)
    }

}

/*-----------------------------------------------------------------------------------------*/
/*		proces for adding and updating status for Scenes and Groups
/*-----------------------------------------------------------------------------------------*/
private def onLocationEvtForScenes(statusrsp) {

state.statusGrpRsp = statusrsp

	statusrsp.each 
    	{
        TRACE("[onLocationEvtForScenes] ${it.Type} ${it.Name} ${it.Status} ${it.Type}")
        switch (it.Type) 
        	{
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

   if (state.setStatusrsp == true ) {
       state.setStatusrsp = false
       pause 5
       state.statusrsp = statusrsp
       pause 5
       }

	def switchTypeVal

	statusrsp.each { 
        if ((state.listOfRoomPlanDevices?.contains(it.idx) && domoticzRoomPlans == true) || domoticzRoomPlans == false) {
        	TRACE("[onLocationEvtForDevices] it.SwitchTypeVal present ${it?.SwitchTypeVal} Temp present ${it?.Temp} ")
            switchTypeVal = null
            if (it?.SwitchTypeVal != null) switchTypeVal = it.SwitchTypeVal
            if (it?.Temp != null) switchTypeVal = 99
            switch (switchTypeVal) 
            	{
                case [3, 13, 6, 16]:		//	Window Coverings 6 & 16 are inverted
                    if (domoticzTypes.contains('Window Coverings')) addSwitch(it.idx, "domoticzBlinds", it.Name, it.Status, it.Type, it)
                    break
                case [0, 7]:		// 	Lamps OnOff, Dimmers and RGB
                    if (domoticzTypes.contains('On/Off/Dimmers/RGB')) addSwitch(it.idx, "domoticzOnOff", it.Name, it.Status, it.Type, it)
                    break
                case 2:				//	Contact 
                    if (domoticzTypes.contains('Contact Sensors')) addSwitch(it.idx, "domoticzContact", it.Name, it.Status, it.Type, it)
                    break
                case 5:				//	Smoke Detector
                    if (domoticzTypes.contains('Smoke Detectors')) addSwitch(it.idx, "domoticzSmokeDetector", it.Name, it.Status, it.Type, it)
                    break
                case 8:				//	Motion Sensors
                    if (domoticzTypes.contains('Motion Sensors')) addSwitch(it.idx, "domoticzMotion", it.Name, it.Status, it.Type, it)
                    break
                case 99:
                    if (domoticzTypes.contains("(Virtual) Sensors")) addSwitch(it.idx, "domoticzSensor", it.Name, "Active", it.Type, it)
                	break
                default:
                	log.error "[onLocationEvtForDevices] non handled SwitchTypeVal ${switchTypeVal}"
                    break
                }	
        	}
		}            
}

/*-----------------------------------------------------------------------------------------*/
/*		Create a status-attribute coversion list that will be passed to generateevent status
/*-----------------------------------------------------------------------------------------*/
private def createAttributes(domoticzDevice, domoticzStatus, addr) {

	if (domoticzStatus instanceof java.util.Map) {TRACE("[createAttributes] ${addr} ${domoticzDevice.getSupportedAttributes()} Passed Status ${domoticzStatus}")}
    	else {
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
				if (domoticzDevice.hasAttribute("level")) attributeList.put('level', v)
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
            	if (domoticzDevice.hasAttribute("switch")) attributeList.put('switch', v)                  
            break;
       }
    
    }
	return attributeList
}

/*-----------------------------------------------------------------------------------------*/
/*		Execute the real add or status update of the child device
/*-----------------------------------------------------------------------------------------*/
private def addSwitch(addr, passedFile, passedName, passedStatus, passedType, passedDomoticzStatus) {
    TRACE("[addSwitch] ${addr}, ${passedFile}, ${passedName}, ${passedStatus}, ${passedDomoticzStatus}")

    def newdni = app.id + ":IDX:" + addr
	def switchTypeVal = ""
    def deviceType = ""
    
    if (passedDomoticzStatus instanceof java.util.Map) {
    	if (passedDomoticzStatus?.SwitchTypeVal != null) {
        	switchTypeVal = passedDomoticzStatus.SwitchTypeVal
            deviceType = "switch"
            }
        else if (passedFile == "domoticzSensor") {
            deviceType = "sensor"
        	}
    	}

    if (getChildDevice(newdni)) {      
        def attributeList = createAttributes(getChildDevice(newdni), passedDomoticzStatus, addr)
        getChildDevice(newdni).generateEvent(attributeList)
    	}
    else {
        try {
                TRACE("[addSwitch] Creating child device ${params}")
                def dev = addChildDevice("verbem", passedFile, newdni, getHubID(), [name:passedName, label:passedName, completedSetup:!state.domoticzRefresh])
                pause 10
                def attributeList = createAttributes(dev, passedDomoticzStatus, addr)
                dev.generateEvent(attributeList)
            } 
        catch (e) 
            {
                log.error "[addSwitch] Cannot create child device. ${devParam} Error: ${e}"
                return 
            }
        }

    state.devices[addr] = [
        'dni'   : newdni,
        'ip' : settings.domoticzIpAddress,
        'port' : settings.domoticzTcpPort,
        'idx' : addr,
        'type'  : deviceType,
        'deviceType' : passedFile,
        'subType' : passedType,
        'switchTypeVal' : switchTypeVal
    ]
	pause 5
}
/*-----------------------------------------------------------------------------------------*/
/*		Purge devices that were removed from Domoticz
/*-----------------------------------------------------------------------------------------*/
private def updateDeviceList() {
    TRACE("[updateDeviceList]")

    def deletedDevices = new ArrayList()

    state.devices.each { k,v ->
        def inStatusrsp = false
        state.statusrsp.each {
        	if (k == it.idx) inStatusrsp = true
        }
        state.statusGrpRsp.each {
        	if (k == it.idx) inStatusrsp = true
        }
        state.statusSensorRsp.each {
        	if (k == it.idx) inStatusrsp = true
        }
            
        if (inStatusrsp == false ) {
            deletedDevices.add(k)
            try {
            	TRACE("[updateDeviceList] Removing deleted device ${v.dni} from state and from childDevices ${k}")
            	deleteChildDevice(v.dni)
            } 	catch (e) {
            	log.error "[updateDeviceList] Cannot delete device ${v.dni}. Error: ${e}"
            }
        }
    }
    
	deletedDevices.each { k ->
    	state.devices.remove(k)
    }
}

private def getDeviceMap() {
    def devices = [:]
    state.devices.each { k,v ->
        if (!devices.containsKey(v.type)) {
            devices[v.type] = []
        }
        devices[v.type] << k
    }

    return devices
}

private def getDeviceListAsText(type) {
    String s = ""
    state.devices.sort().each { k,v ->
        if (v.type == type) {
        	def dev = getChildDevice(v.dni)
           	s += "${k.padLeft(4)} - ${dev?.displayName} - ${v.deviceType}\n"
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
/*		Excecute 'poll' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_poll(nid) {
	TRACE("[domoticz poll/status] (${nid})")
    socketSend("status", nid, 0, 0, 0)
}
/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'scenepoll' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_scenepoll(nid) {
	TRACE("[domoticz scenepoll/status] (${nid})")
    socketSend("scenes", nid, 0, 0, 0)
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'off' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_off(nid) {
    TRACE("[domoticz off] (${nid})")
    socketSend("off", nid, 0, 0, 0)
}
/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'sceneoff' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_sceneoff(nid) {
	TRACE("[domoticz scene off] (${nid})")
    // find out if it is a scene or a group, scenes do only ON commands
    if (state.devices[nid].subType == "Scene") 
    	{socketSend("sceneon", nid, 0, 0, 0)}
    else 
    	{socketSend("sceneoff", nid, 0, 0, 0)}
}
/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'on' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_on(nid) {
    TRACE("[domoticz on] (${nid})")
    socketSend("on", nid, 16, 0, 0)
}
/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'sceneon' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_sceneon(nid) {
	TRACE("[domoticz scene on] (${nid})")
    socketSend("sceneon", nid, 0, 0, 0)
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'stop' command on behalf of child device
/*-----------------------------------------------------------------------------------------*/
def domoticz_stop(nid) {
	TRACE("[domoticz stop] (${nid})")
    socketSend("stop", nid, 0, 0, 0)
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'setlevel' command on behalf of child device , RFY = Somfy based devices
/*-----------------------------------------------------------------------------------------*/
def domoticz_setlevel(nid, xLevel) {
	TRACE("[domoticz setlevel] ( ${xLevel} for ${nid})")
    if (xLevel.toInteger() == 0) {socketSend("off", nid, 0, 0, 0)}
    else 	if (state.devices[nid].subType == "RFY") 
    			{socketSend("stop", nid, 0, 0, 0)} 
    		else 
            	{socketSend("setlevel", nid, xLevel, 0, 0)}
}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute 'setcolor' command on behalf of child device 
/*-----------------------------------------------------------------------------------------*/
def domoticz_setcolor(nid, xHex, xSat, xBri) {
	TRACE("[domoticz setcolor] (${nid} Hex ${xHex} Sat ${xSat} Bri ${xBri})")
    socketSend("setcolor", nid, xHex, xSat, xBri)

}

/*-----------------------------------------------------------------------------------------*/
/*		Excecute The real request via the local HUB
/*-----------------------------------------------------------------------------------------*/
private def socketSend(message, addr, level, xSat, xBri) {
    TRACE("[socketSend] ${message} to IDX = ${addr}") 
	def rooPath = ""
    def rooLog = ""
    
    switch (message) {
		case "list":
        	state.setStatusrsp = true
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ListDevices"
        	rooPath = "/json.htm?type=devices&filter=all&used=true&order=Name"   // "Devices"
 			break;
		case "listsensors":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ListSensors"
        	rooPath = "/json.htm?type=devices&filter=temp&used=true&order=Name"   // "Devices"
 			break;
		case "scenes":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20ListScenes"
        	rooPath = "/json.htm?type=scenes"										// "Scenes"
 			break;
		case "roomplans":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Room%20Plans"
        	rooPath = "/json.htm?type=plans&order=name&used=true"					// "Plans"
 			break;
		case "roomplan":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Roomplan%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=getplandevices&idx=${addr}"		// "GetPlanDevices"
 			break;
        case "sceneon":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchscene&idx=${addr}&switchcmd=On" // "SwitchScene"
            break;
        case "sceneoff":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchscene&idx=${addr}&switchcmd=Off"  // "SwitchScene"
            break;
		case "status":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Status%20for%20${addr}"
			rooPath = "/json.htm?type=devices&rid=${addr}" 									// "Devices"
			break;
        case "off":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Off%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=Off"	// "SwitchLight"
            break;
        case "on":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20On%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=On"		// "SwitchLight"
            break;
        case "stop":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Stop%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=Stop"		// "SwitchLight"
            break;
        case "setlevel":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Level%20${level}%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=switchlight&idx=${addr}&switchcmd=Set%20Level&level=${level}"  // "SwitchLight"
            break;
        case "setcolor":
        	rooLog = "/json.htm?type=command&param=addlogmessage&message=SmartThings%20Color%20${level}%20for%20${addr}"
        	rooPath = "/json.htm?type=command&param=setcolbrightnessvalue&idx=${addr}&hex=${level}&iswhite=false&brightness=${xBri}" // "SetColBrightnessValue"
            break;
            
	}
    
	
    def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: rooPath,
        headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"])

	def mSeconds = 0
    
    sendHubCommand(hubAction)
	if (mSeconds > 0 ) {pause(mSeconds)}
    else {
        def hubActionLog = new physicalgraph.device.HubAction(
            method: "GET",
            path: rooLog,
            headers: [HOST: "${settings.domoticzIpAddress}:${settings.domoticzTcpPort}"])

        sendHubCommand(hubActionLog)
        }
	
    return null
}

/*-----------------------------------------------------------------------------------------*/
/*		
/*-----------------------------------------------------------------------------------------*/
def refreshDevicesFromDomoticz() {

	TRACE("[refreshDevicesFromDomoticz] Entering routine")

	updateDeviceList()
    
	state.domoticzRefresh = true
	state.listOfRoomPlanDevices = []
    if (domoticzRoomPlans)
    	{
        domoticzPlans.each { v ->       	
        	state.statusPlansRsp.each {
            if (v == it.Name) {
            	socketSend("roomplan", it.idx, 0,0,0)
                pause 10
                }
        	}
          }
        }

	socketSend("list",0,0,0,0)
    pause 10
    socketSend("scenes",0,0,0,0)
	pause 10
    state.domoticzRefresh = false

}

/*-----------------------------------------------------------------------------------------*/
/*		Domoticz will send an event message to ST for all devices THAT HAVE BEEN SELECTED to do that
/*-----------------------------------------------------------------------------------------*/
def eventDomoticz() {
	TRACE("[eventDomoticz] " + params.message)
    
    if (params.message.contains(" ALARM")) {
        def parts = params.message.split(" ALARM")
        def devName = parts[0]
        def children = getChildDevices()

        children.each { 
            if (it.name == devName) {
      			TRACE("[eventDomoticz] ALARM " + devName)
                def idx = it.deviceNetworkId.split(":")[2]
                socketSend("status", idx, 0, 0, 0)
            	}
        	}
        return null
    	}

    if (params.message.contains(" movement")) {
        def parts = params.message.split(" movement")
        def devName = parts[0]
        def children = getChildDevices()
		
		children.each { 
            if (it.name == devName) {
      			TRACE("[eventDomoticz] movement " + devName)
               	def idx = it.deviceNetworkId.split(":")[2]
               	socketSend("status", idx, 0, 0, 0)
            	}
        	}
        return null
        }

	if (params.message.contains(" Closed") || params.message.contains(" Open")) {
    	def devName = ""
        if (params.message.contains(" Closed")) devName = params.message.replace(" Closed", "")
        if (params.message.contains(" Open")) devName = params.message.replace(" Open", "")
        def children = getChildDevices()
		
		children.each { 
            if (it.name == devName) {
      			TRACE("[eventDomoticz] closed/open " + devName)
               	def idx = it.deviceNetworkId.split(":")[2]
               	socketSend("status", idx, 0, 0, 0)
            	}
        	}    	
        return null
        }
        
    if (params.message.contains(" >> ")) {
        def parts = params.message.split(" >> ")
        def devName = parts[0]
        def children = getChildDevices()
		
		children.each {     	
            if (it.name == devName) {
      			TRACE("[eventDomoticz] >> " + devName)
               	def idx = it.deviceNetworkId.split(":")[2]
            	socketSend("status", idx, 0, 0, 0)
            }
        }
    }
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
        catch (e) 
        	{
			log.error "[initRestApi] did you enable OAuth in the IDE for this APP?"
			}
    	}
    state.urlCustomActionHttp = getApiServerUrl() - ":443" + "/api/smartapps/installations/${app.id}/" + "EventDomoticz?access_token=" + state.accessToken + "&message=#MESSAGE"

}
//-----------------------------------------------------------

def getHubID(){
    TRACE("[getHubID]")
    def hubID
    def hubs = location.hubs.findAll{ it.type == physicalgraph.device.HubType.PHYSICAL } 
    TRACE("[getHubID] hub count: ${hubs.size()}")
    if (hubs.size() == 1) hubID = hubs[0].id 
    TRACE("[getHubID] hubID: ${hubID}")
    return hubID
}

private def textCopyright() {
    return "Copyright (c) 2017 Martin Verbeek"
}