/**
 *  Hue Sensor Manager
 *
 *  Author: Martin Verbeek 
 *
 *  Copyright 2017 Martin Verbeek
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	1.00 Initial Release, thanks to Anthony S for version control
 *	1.01 Support for Hue B bridges or bridges that support username as an attribute
 *	1.02 Support elevated polling or no normal polling depending on mode
 *	1.03 Initial cleaning of code and removing the custom DTH command (replace by normal sendEvent)
 *	1.04 Checking of bridge status online/offline and putting assoiciated devices offline or back online
 *	1.05 Add immediate poll after motion sensed instead of waiting 1 second
 *	1.06 Bug fixes
 *  1.07 checked for Hue Dimmer Switch not Hue Switch Dimmer
 *	1.08 check for config = reachable and on to be true and put device offline if either one is not true, online if again reachable or config on again
 *	1.09 minor fixing and added checking
 *	1.10 adjust mac from Hue B smart to comply
 *	1.11 changed motion detect to always do a active-incative sequence when lastupdated changed on the sensor, motion is not being missed this way
 *	1.12 clean up of scenecycle, moved it to DTH, now independent of Hue scenecycle
 *	1.13 move motion sensor inactive event to runIn 30 seconds handler, to have a more natural motion time
 *	1.14 made code more efficient in the pollsensor for elevated polling and poll more evenly in a minute 
 *	1.15 added the option of autodefine room type groups that exists on the bridges, device is domoticzOnOff. Added TRACE switch
 *	1.16 hyperpoll a single sensor 5 times for 5 seconds to get subsequent pushes faster when an event is detected
 *	1.17 state.pollSensors set to true if not present
 */


definition(
		name: "Hue Sensor (Connect)",
		namespace: "verbem",
		author: "Martin Verbeek",
		description: "Allows you to connect your Philips Hue Sensors with SmartThings and control them from your Things area or Dashboard in the SmartThings Mobile app. It will connect to bridges that are discovered by Super Lan Connect",
		category: "My Apps",
		iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/hue.png",
		iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/hue@2x.png",
		singleInstance: true
)
private def runningVersion() 	{"1.17"}

preferences {
	page(name:pageMain)
    page(name:pageBridges, title:"", refreshInterval:20)
}


def pageMain() {
	state.refreshCount = 0
	if (!state.devices) state.devices = [:]

	pageBridges()
}

def pageBridges() {
    state.refreshCount = state.refreshCount + 1
	def sourceBridge = "Super LAN Connect"
    
	if (z_Bridges) {
    	def canPoll = false
    	z_Bridges.each { dev ->
        	def sN = dev.currentValue("serialNumber")
            if (dev.currentValue("username")) {
            	sN = sN.substring(6)  // Hue B
                TRACE("[pageBridges] Hue B SMART detected for ${sN}")
                sourceBridge = "Hue B Smart"
            }
            
            if ("z_BridgesUsernameAPI_${sN}") canPoll = true        	
        }
        if (canPoll) {
            TRACE("[pageBridges] bridges present canPoll!}")
            //if (!state.pollSensors) state.state.pollSensors = true
        	pollTheSensors(data:[elevatedPolling:false])
       	}
    }

    def inputBridges= [
            name			: "z_Bridges",
            type			: "capability.bridge",
            title			: "Select Hue Bridges",
            multiple		: true,
            submitOnChange	: true,
            required		: false
        ] 

	def inputSensors= [
            name			: "z_Sensors",
            type			: "enum",
            title			: "Select Sensor Types",
            multiple		: true,
            submitOnChange	: false,
            options			: ["Hue Rooms","Hue Motion","Hue Switch Dimmer","Hue Tap"],
            required		: true
        ] 

    def inputTrace = [
            name        : "z_Trace",
            type        : "bool",
            title       : "Debug trace output in IDE log",
            defaultValue: true
        ]

		dynamicPage(name: "pageBridges", title: "Bridges found by ${sourceBridge} version ${runningVersion()}", uninstall: true, install:true) {
            section("Please select Hue Bridges that contain sensors and types to add") {
 
 				input inputTrace
                input inputSensors
                input inputBridges          
            }
            if (z_Bridges) {
                z_Bridges.each { dev ->
                    def serialNumber = dev.currentValue("serialNumber")
                    def networkAddress = dev.currentValue("networkAddress")
                    def username = dev.currentValue("username") // HUE B Attribute  
                    if (username) {
                    	serialNumber = serialNumber.substring(6) // HUE B Attribute 
                    }
                    
                    section("Bridge ${dev}, Serial:${serialNumber}, IP:${networkAddress}, username for API is in device in IDE", hideable:true) {
                    	if (!username) {
                        	href(name: "${dev.id}", title: "IDE Bridge device",required: false, style: "external", url: "${getApiServerUrl()}/device/show/${dev.id}", description: "tap to view device in IDE")
                            input "z_BridgesUsernameAPI_${serialNumber}", "text", required:true, title:"Username for API", submitOnChange:true
                        }
                        else {                         
                        	paragraph username
                        	input "z_BridgesUsernameAPI_${serialNumber}", "text", required:true, title:"Username for API", submitOnChange:true, description:username
                        }                        
                    }
                    
                }
                if (z_Sensors) {
                    if (state.devices) {
                        section("Elevated and or No polling during selected modes") {
                            input "z_modes", "mode", title: "select elevated mode(s)", multiple: true, required: false
                            input "z_noPollModes", "mode", title: "select no polling mode(s)", multiple: true, required: false
                        }
                        section("Associate a ST motion sensor with a Hue Sensor for monitoring during motion, sensor will be checked during motion") {
                        state.devices.each { item, sdev ->
                            input "z_motionSensor_${sdev.dni}", "capability.motionSensor", required:false, title:"Associated motion sensor ${sdev.name} "
                            }	
                        }
                    }
                    else section("No sensors found yet, wait a minute or so, or tap Done and reenter App")
                }
			}
		}
}

def installed() {
	TRACE("Installed with settings: ${settings}")
	initialize()
}

def updated() {
	TRACE("Updated with settings: ${settings}")
	unsubscribe()
	unschedule()
	initialize()
}

def uninstalled() {
	// Remove bridgedevice connection to allow uninstall of smartapp even though bridge is listed
	// as user of smartapp
	
    unsubscribe()
    unschedule()
	TRACE("Uninstall")

    removeChildDevices(getChildDevices())

}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

def initialize() {
	TRACE("Initializing")
    
    schedule("2015-01-09T12:00:00.000-0600", notifyNewVersion)
	
    if (settings.z_Bridges) {
    	subscribeToMotionEvents()
        subscribe(location, "mode", handleChangeMode)
        subscribe(location, "alarmSystemStatus", handleAlarmStatus)
        subscribe(z_Bridges, "status", handleBridges)
        handleChangeMode(null)
        runEvery10Minutes("checkDevices")
    }
}

def handleBridges(evt) {
	TRACE("[handleBridges] ${evt.value} ${evt.device}")
    checkBridges()
}

def checkBridges() {

	settings.z_Bridges.each { bridge ->
    	def mac = bridge.currentValue("serialNumber")
        if (bridge.currentValue("username")) {
        	mac = mac.substring(6) // Hue B
        }
        
        state.devices.each { key, sensor -> 
        	if (sensor.mac == mac) {
            	def devSensor = getChildDevice(sensor.dni)
                if (!devSensor?.currentValue("DeviceWatch-Enroll")) {
	               	TRACE("[checkBridges] Enroll ${devSensor}")
            		devSensor.sendEvent(name: "DeviceWatch-Enroll", value: groovy.json.JsonOutput.toJson([protocol: "LAN", scheme:"untracked"]), displayed: false)
                }
            }
        }
       
    	if (bridge.currentValue("status") != "Online") {
        	TRACE("Bridge ${bridge} is OFFLINE")
        	// set devices belonging to this bridge OFFLINE
            state.devices.each { key, sensor -> 
            	if (sensor.mac == mac) {
                	def devSensor = getChildDevice(sensor.dni)

                	if (devSensor?.currentValue("DeviceWatch-DeviceStatus") == "online") {
                		TRACE("[checkBridges] Put ${devSensor} OFFLINE") 
                        devSensor.sendEvent(name: "DeviceWatch-DeviceStatus", value: "offline", displayed: false, isStateChange: true)
                    }
                }
            }
        }
        else {
        	// if devices where offline for this bridge, get them ONLINE again
            state.devices.each { key, sensor -> 
            	if (sensor.mac == mac) {
                	def devSensor = getChildDevice(sensor.dni)
                	if (devSensor?.currentValue("DeviceWatch-DeviceStatus") != "online") {
                		TRACE("[checkBridges] Put ${devSensor} ONLINE")
                        devSensor.sendEvent(name: "DeviceWatch-DeviceStatus", value: "online", displayed: false, isStateChange: true)
                    }
                }
            }
        }
    }
}

def notifyNewVersion() {

	if (appVerInfo().split()[1] != runningVersion()) {
    	sendNotificationEvent("Hue Sensor App has a newer version, ${appVerInfo().split()[1]}, please visit IDE to update app/devices")
    }
}

def elevatedDeviceCall(deviceId) {

    if (deviceId.indexOf("/sensor/") == -1) return
    
    def mac = deviceId.split("/")[0]
    def sensor = deviceId.split("/")[2]
	def usernameAPI = settings."z_BridgesUsernameAPI_${mac}"
    def hostIP
    
    settings.z_Bridges.each { bridge ->
        def match = bridge.currentValue("serialNumber").indexOf(mac)
    	if (match != -1) {
        	hostIP = bridge.currentValue("networkAddress") 
			def i = 0
            for (i = 1; i <10; i++) {
                runIn(i, pollSensor, [data: [hostIP: hostIP, usernameAPI: usernameAPI, sensor: sensor], overwrite: false])
            }         
        }
    }
    
                      
}

def pollTheSensors(data) {
	def bridgecount = 1
    TRACE("[pollTheSensors] entered ")

	def numberBridges = 0
    settings.z_Bridges.each {
    	numberBridges++
    }
    
    def interval = 3
    if (numberBridges >3) interval = numberBridges


    settings.z_Bridges.each { dev ->
		
		def serialNumber = dev.currentValue("serialNumber")
        if (dev.currentValue("username")) {
        	serialNumber = serialNumber.substring(6)	// Hue B
        }
        
        def networkAddress = dev.currentValue("networkAddress")
               
		if (settings."z_BridgesUsernameAPI_${serialNumber}") {
        	pollRooms(networkAddress, settings."z_BridgesUsernameAPI_${serialNumber}")         
            
            if (state.pollSensors) {
                if (!data.elevatedPolling) {
                    state.elevatedPolling = false
                    poll(networkAddress, settings."z_BridgesUsernameAPI_${serialNumber}")
                }
                else {
                    if (data?.dni == null) state.elevatedPolling = true
                    def i = 0
                    for (i = 0; i < 60; i = i + interval) {
                        runIn(i+bridgecount, handleElevatedPoll, [data: [hostIP: networkAddress, usernameAPI: settings."z_BridgesUsernameAPI_${serialNumber}"], overwrite: false]) 
                    }
                }
        	}
            else TRACE("[pollTheSensors] pollSensor is false")
        }
        else {
        	TRACE("[pollTheSensors] no bridge yet mac ${serialNumber} IP ${networkAddress}")
        }
        bridgecount++
    }
}

def monitorSensorStop(evt) {

	TRACE("[monitorSensor] Motion Stopped for ${evt.displayName.toString()}")
}


def monitorSensor(evt) {

	TRACE("[monitorSensor] Motion Started for ${evt.displayName.toString()}")
    
	settings.each { key, value ->
    	if (value.toString() == evt.displayName.toString()) {

			// z_motionSensor_0AF130/sensor/2
            def sensor = key.split('/')[2].toString()
            def mac = key.split('_')[2].split("/")[0].toString()
            def usernameAPI = settings."z_BridgesUsernameAPI_${mac}"
			def dni = key.split('_')[2]

			settings.z_Bridges.each { dev ->
				def mac2 = dev.currentValue("serialNumber").toString() 
                if (dev.currentValue("username"))  {
                	mac2 = mac2.substring(6) // Hue B
                }
                
                if (mac == mac2) {
            		def devSensor = getChildDevice(dni)
                	if (devSensor) {
                        def hostIP = dev.currentValue("networkAddress")
                        def i = 0
						
                        pollSensor(data: [hostIP: hostIP, usernameAPI: usernameAPI, sensor: sensor])
                        
						for (i = 1; i <15; i++) {
                        	runIn(i, pollSensor, [data: [hostIP: hostIP, usernameAPI: usernameAPI, sensor: sensor], overwrite: false])
                        }                       
                	}
                }
            }
        }
    }   
}

def subscribeToMotionEvents() {

	state.devices.each { dni, sensor -> 
    	if (settings."z_motionSensor_${dni}") {
        	def motionSensor = settings."z_motionSensor_${dni}"
        	TRACE("[subscribeToMotionEvents] Subscribe to motion for ${motionSensor} defined for ${sensor.name}")
            subscribe(motionSensor, "motion.active", monitorSensor)
            subscribe(motionSensor, "motion.inactive", monitorSensorStop)
        }
    }
}

private updateSensorState(messageBody, mac) { 

	//build real world sensor list from bridge with mac
    def sensorListBridge = [:]
	messageBody.each { item, sensor ->
		if (sensor instanceof Map) {
            	sensorListBridge[item] = sensor
		}
	}
    
    // delete childdevice with same mac and not in sensorlistbridge
    getAllChildDevices().each { device ->
    	if (device.deviceNetworkId.indexOf("/sensor/") != -1) {
            def macDevice = device.deviceNetworkId.split("/")[0]
            def itemDevice = device.deviceNetworkId.split("/")[2]
            if (mac == macDevice) {
                if (!sensorListBridge[itemDevice]) {
                    TRACE("[updateSensorState] ${macDevice} remove childDevice ${itemDevice} ${device}")
                    deleteChildDevice(device.deviceNetworkId)
                }
            }
   		}
    }

	// build a list of sensors to be removed from state.devices that are no longer discovered
 	def sensorRemoveFromState = [:]
    sensorRemoveFromState << state.devices
    
	state.devices.each { dni, sensor ->
    	
		if (!getChildDevice(dni)) {       	
            TRACE("[updateSensorState] ${dni} no longer exists in childdevices, keep on removeList")
        }
        else {
            sensorRemoveFromState.remove(dni)
        }
	}
    
	// remove from state.devices
    sensorRemoveFromState.each { dni, sensor ->
    	TRACE("[updateSensorState] ${dni} removed from state")
    	state.devices.remove(dni)
        pause 2
    }

}

def parse(childDevice, description) {
	log.warn "[Parse] Entered ${childDevice} ${description}"
}

def handleMotionInactive(data) {
	TRACE("[handleMotionInactive] ${data.sensors}")
    def listSensors = data.sensors
    
    listSensors.each { key, dni ->
    	def dev = getChildDevice(dni)
        if (dev.currentValue("motion") == "active") dev.sendEvent(name: "motion", value: "inactive", descriptionText: "$dev motion stopped", isStateChange: true)
    }
}

def handleElevatedPoll(data) {
	poll(data.hostIP,data.usernameAPI)
}


def handleAlarmStatus(evt) {
    TRACE("[handleAlarmStatus] Alarm status changed to: ${evt.value}")
    if (evt.value == "away") {
    	handleChangeMode([value : "Away"])
    }
    else {
    	handleChangeMode([value : location.mode])
    }
}

def handleChangeMode(evt) {
	def evtMode
    
	if (evt?.value) evtMode = evt.value
    else evtMode = location.mode

    if (!settings?.z_noPollModes?.contains(evtMode)) {
        state.pollSensors = true
        if (settings?.z_modes?.contains(evtMode)) {
            TRACE("[handleChangeMode] Mode is ${evtMode} elevated Polling for sensors")
            runEvery1Minute("pollTheSensors", [data: [elevatedPolling: true]])
        }
        else {
            TRACE("[handleChangeMode] Mode is ${evtMode} run normal 1 minute Polling for sensors")
            runEvery1Minute("pollTheSensors", [data: [elevatedPolling: false]])
        }
    }
    else {
    	TRACE("mode is ${evtMode} No more Polling for sensors")
        state.pollSensors = false
    	runEvery1Minute("pollTheSensors", [data: [elevatedPolling: false]])
    }
}

def handleRooms(physicalgraph.device.HubResponse hubResponse) {
	//TRACE("[handleRooms] entered")
	def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)
  
    if (hubResponse?.json?.error) {
    	log.error "[handleRooms] Error in ${mac} ${hubResponse.json.error}"	
        return
    }

	def body = hubResponse.json
    
    body.each { item, group ->
    	if (group.type == "Room") {
        	def dni = mac + "/group/" + item
            def groupDev = getChildDevice(dni)
            if (!groupDev) {
            	TRACE("[handleRooms] add room ${dni} ${group.name}")
            	groupDev = addChildDevice("verbem", "domoticzOnOff", dni, null, [name:group.name, label:group.name, completedSetup:true])
           	}
            	else {
                	if (group.name != groupDev.name) {
                    	groupDev.name = group.name
                        groupDev.label = group.name
                    }
                }
            //sendEvents...
            
            if (group?.state?.all_on || group?.state?.any_on) groupDev.sendEvent(name: "switch", value: "on")
            	else groupDev.sendEvent(name: "switch", value: "off")
                            
            if (group?.action?.hue) {
            	groupDev.sendEvent(name:"hue", value: group.action.hue*100/65535) 
            }
            if (group?.action?.bri) { 
            	groupDev.sendEvent(name:"level", value: Math.round(group.action.bri*100/254))
            }
            if (group?.action?.sat) {
            	groupDev.sendEvent(name:"saturation", value: Math.round(group.action.sat*100/254))
            }
            //if (group?.action?.ct) {
            //	groupDev.sendEvent(name:"colorTemperature", value: Math.round(1000000/group.action.ct))           
            //}
        }
   	}
}

def handleRoomPut(physicalgraph.device.HubResponse hubResponse) {

	def dev
    def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)
    
	//TRACE("[handleRoomPut] mac ${mac} JSON ${hubResponse.json} ")
    hubResponse.json.each { key ->
    	key.success.each { item, value ->
        	def dni = mac + "/group/" + item.split("/")[2]
            if (!dev) {
            	dev = getChildDevice(dni)
				TRACE("[handleRoomPut] sendEvents to device ${dev}")
            }

            if (item.indexOf("/action/on") != -1) {
                if (value == true)	dev.sendEvent(name:"switch", value:"on")
                if (value == false) dev.sendEvent(name:"switch", value:"off")
           	}
        }
    }
    
}
    
def handleCheckDevices(physicalgraph.device.HubResponse hubResponse) {

    def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)

    if (hubResponse?.json?.error) {
    	log.error "[handleCheckDevices] Error in ${mac} ${hubResponse.json.error}"	
        return
    }
    
    updateSensorState(hubResponse.json, mac)
}

def handlePoll(physicalgraph.device.HubResponse hubResponse) {
	TRACE("[handlePoll] entering")
    // check for encoded body....
    
    def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)
    def motionCount = 0
    def sensorList = [:]
	def usernameAPI = settings."z_BridgesUsernameAPI_${mac}"
    def hostIP
    def i = 0 
    
    settings.z_Bridges.each { bridge ->
    	if (bridge.currentValue("serialNumber").toUpperCase().indexOf(mac) != -1) hostIP = bridge.currentValue("networkAddress")
    }

    if (hubResponse?.json?.error) {
    	log.error "[handlePoll] Error in ${mac} ${hubResponse.json.error}"	
        return
    }

	def body = hubResponse.json

    body.each { item, sensor ->
		//TRACE("[handlePoll] ${item} - ${sensor}")
    	if (sensor.type == "ZLLLightLevel") {
        	if (sensor.state.lightlevel) {
            
            	// hue to lux => lux = 10 ^ ((hue - 1) / 10000)
                
                def float luxf = (10 ** ((sensor.state.lightlevel - 1) / 10000)).round(0)
                def lux = luxf.toInteger()
                def dni  = findStateDeviceWithUniqueId(getMac(sensor.uniqueid))
                if (state.devices[dni]) {
                    def sensorDev = getChildDevice(dni)
                    state.devices[dni].lightLevel = lux

                    if (!state.devices[dni].lightLevelLastupdated) {
                        state.devices[dni].lightLevelLastupdated = sensor.state.lastupdated
                        if (sensorDev) sensorDev.sendEvent(name: "illuminance", value: lux) 
                    }
                    else if (state.devices[dni].lightLevelLastupdated != sensor.state.lastupdated) {
                        state.devices[dni].lightLevelLastupdated = sensor.state.lastupdated
                        if (sensorDev) sensorDev.sendEvent(name: "illuminance", value: lux) 
                    }
				}
            }
		}

		if (sensor.type == "ZLLTemperature") {
        	if (sensor.state.temperature) {
                def temp = (sensor.state.temperature / 100).toInteger()
                def tempScale = location.temperatureScale
                
                if (tempScale == "F") {
                	temp = (temp * 1.8 + 32).toInteger()
                }
                def dni  = findStateDeviceWithUniqueId(getMac(sensor.uniqueid))
                if (state.devices[dni]) {
                    def sensorDev = getChildDevice(dni)
                    state.devices[dni].temperature = temp

                    if (!state.devices[dni].temperatureLastupdated) {
                        state.devices[dni].temperatureLastupdated = sensor.state.lastupdated
                        if (sensorDev) sensorDev.sendEvent(name: "temperature", value: temp)
                    }
                    else if (state.devices[dni].temperatureLastupdated != sensor.state.lastupdated) {
                        state.devices[dni].temperatureLastupdated = sensor.state.lastupdated
                        if (sensorDev) sensorDev.sendEvent(name: "temperature", value: temp)
                    }
            	}
			}
		}
		
        if (settings.z_Sensors) {
            //TRACE("[handlePoll] sensor type ${sensor.type}")

            if ((sensor.type == "ZGPSwitch" && settings.z_Sensors.contains("Hue Tap")) || (sensor.type == "ZLLPresence" && settings.z_Sensors.contains("Hue Motion")) || (sensor.type == "ZLLSwitch"  && settings.z_Sensors.contains("Hue Switch Dimmer")) ) {
                def dni = mac + "/sensor/" + item
                def sensorDev = getChildDevice(dni)
                if (!sensorDev) {
                    def devType
                    devType = null
                    switch (sensor.type) {
                        case "ZGPSwitch":
                            devType = "Hue Tap"
                            break
                        case "ZLLPresence":
                            devType = "Hue Motion"
                            break
                        case "ZLLSwitch":
                            devType = "Hue Switch"
                            break  
                    }

                    if (devType != null) {
                        TRACE("[handlePoll] Add Sensor ${dni} ${sensor.type} ${devType} ${sensor.name} ${getMac(sensor.uniqueid)}")

						try {
                            sensorDev = addChildDevice("verbem", devType, dni, null, [name:sensor.name, label:sensor.name, completedSetup:true])
                     	}
                        catch (ex) {
                        	log.error "[handlePoll] error ${ex} during add of child ${dni},${sensor.name}"
                        }
                        state.devices[dni] = [
                            'lastUpdated'	: sensor.state.lastupdated, 
                            'mac'			: mac, 
                            'item'			: item, 
                            'dni'			: dni,
                            'name'			: sensor.name,
                            'uniqueId'		: getMac(sensor.uniqueid),
                            'type'			: sensor.type,
                            'monitorTap'	: false,	
                            'id'			: sensorDev.id
                        ]
                        pause 2
                	}
                }

                else 
                {
                    TRACE("[handlePoll] sensor child found ${sensorDev}")
                    
                    if ((sensor?.config?.reachable == false || sensor?.config?.on == false) && (sensorDev?.currentValue("DeviceWatch-DeviceStatus") == "online")) sensorDev.sendEvent(name: "DeviceWatch-DeviceStatus", value: "offline", displayed: false, isStateChange: true)
                    else if ((sensor?.config?.reachable == null && sensor?.config?.on == true ) && (sensorDev?.currentValue("DeviceWatch-DeviceStatus") != "online")) sensorDev.sendEvent(name: "DeviceWatch-DeviceStatus", value: "online", displayed: false, isStateChange: true)
                   	else if ((sensor?.config?.reachable == true && sensor?.config?.on == true ) && (sensorDev?.currentValue("DeviceWatch-DeviceStatus") != "online")) sensorDev.sendEvent(name: "DeviceWatch-DeviceStatus", value: "online", displayed: false, isStateChange: true)
                    
                    if (state.devices[dni]?.name != sensor.name) {
                        state.devices[dni].name = sensor.name
                        sensorDev.name = sensor.name
                        sensorDev.label = sensor.name
                    }

                    if (state.devices[dni]?.lastUpdated != sensor.state.lastupdated) {
                        state.devices[dni].lastUpdated = sensor.state.lastupdated
                        switch (state.devices[dni].type) {
                            case "ZGPSwitch":
                                TRACE("[handlePoll] Buttonpress Tap ${dni} ${sensor.state.buttonevent}")
                                sensorDev.buttonEvent(sensor.state.buttonevent)                          
                                break
                            case "ZLLPresence":
                            	motionCount++
                                sensorList[motionCount] = dni
                                TRACE("[handlePoll] Motion Sensor ${dni} ${sensor.state.presence} DTH Status is ${sensorDev.currentValue("motion")}")
                               
                                if (sensorDev.currentValue("motion") == "inactive" || sensorDev.currentValue("motion") == null ) sensorDev.sendEvent(name: "motion", value: "active", descriptionText: "$sensorDev motion started", isStateChange: true)
                                
                                sensorDev.sendEvent(name: "battery", value: sensor.config.battery)
                                break
                            case "ZLLSwitch":
                                TRACE("[handlePoll] Dimmer Switch ${dni} ${sensor.state.buttonevent}")
                                sensorDev.sendEvent(name: "battery", value: sensor.config.battery)
                                sensorDev.buttonEvent(sensor.state.buttonevent)
                                break                    
                        }
                        for (i = 1; i < 6; i= i + 1) {
                            runIn(i, pollSensor, [data: [hostIP: hostIP, usernameAPI: usernameAPI, sensor: item], overwrite: false])
                        }         
                    }
                }
            }
		}
	}
    if (sensorList.size() > 0) {
        runIn(30, handleMotionInactive, [data: [sensors: sensorList], overwrite: false])
    }
}

def handlePollSensor(physicalgraph.device.HubResponse hubResponse) {

	
	def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)
    def body = hubResponse.json

    if (hubResponse.json.error) {
    	log.error "[handlePollSensor] Error in ${mac} ${hubResponse.json.error}"	
        return
    }
	
    def dni  = findStateDeviceWithUniqueId(getMac(body.uniqueid))
    def sensorDev = getChildDevice(dni)

	if (!sensorDev) {
        log.error "[handlePollSensor] Sensor ${dni} not found for update ${body.name} ${body.uniqueid}"
        return
    }

	if (state.devices[dni].lastUpdated != body.state.lastupdated) {
        state.devices[dni].lastUpdated = body.state.lastupdated
        switch (state.devices[dni].type) {
        case "ZGPSwitch":
            sensorDev.buttonEvent(body.state.buttonevent)                          
            break
        case "ZLLPresence":
            if (body.state.presence) 	sensorDev.sendEvent(name: "motion", value: "active", descriptionText: "$sensorDev motion detected", isStateChange: true)
            else 						sensorDev.sendEvent(name: "motion", value: "inactive", descriptionText: "$sensorDev motion detected", isStateChange: true)
            break
        case "ZLLSwitch":
            TRACE("[handlePoll] Dimmer Switch ${dni} ${body.state.buttonevent}")
            sensorDev.buttonEvent(body.state.buttonevent)
            break                    
        default:
        	break
        }
    }
}

private poll(hostIP, usernameAPI) {

	if(hostIP.indexOf(":") == -1) hostIP = hostIP + ":80"

    def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/${usernameAPI.trim()}/sensors/",
        headers: [HOST: "${hostIP}"],
        null,
        [callback: handlePoll] )
	TRACE("${hubAction}")
    
    sendHubCommand(hubAction)
}

def checkDevices() {

    settings.z_Bridges.each { dev ->
		
		def serialNumber = dev.currentValue("serialNumber")
		
        if (dev.currentValue("username")) {
        	serialNumber = serialNumber.substring(6)	// Hue B
        }
        
        def hostIP = dev.currentValue("networkAddress")
		if(hostIP.indexOf(":") == -1) hostIP = hostIP + ":80"	// Hue B

        def hubAction = new physicalgraph.device.HubAction(
            method: "GET",
            path: "/api/" + settings."z_BridgesUsernameAPI_${serialNumber}".trim() + "/sensors/",
            headers: [HOST: "${hostIP}"],
            null,
            [callback: handleCheckDevices] )

        sendHubCommand(hubAction)
	}
}

def pollSensor(data) {
	
    if(data?.hostIP == null) return

	if(data?.hostIP?.indexOf(":") == -1) data.hostIP = data.hostIP + ":80"

    def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/${data.usernameAPI.trim()}/sensors/${data.sensor}",
        headers: [HOST: "${data.hostIP}"],
        null,
        [callback: handlePollSensor] )

    sendHubCommand(hubAction)
}

private pollRooms(hostIP, usernameAPI) {
	if (settings.z_Sensors.indexOf("Hue Rooms") == -1) return

	if(hostIP.indexOf(":") == -1) hostIP = hostIP + ":80"

	def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/${usernameAPI.trim()}/groups/",
        headers: [HOST: "${hostIP}"],
        null,
        [callback: handleRooms] )

    sendHubCommand(hubAction)
}

private findStateDeviceWithUniqueId(uniqueId) {
	def stateDevice = state.devices.find {key, dev -> 
    	dev.uniqueId == uniqueId
    }
    return stateDevice.toString().split("=")[0]
}

private getMac(uniqueId) {
	def mac = uniqueId.split("-")[0]
	return mac
}

/*	-----------------------------------------------------------------------
	device type command handlers for rooms, uses the standard domoticzOnOff
    -----------------------------------------------------------------------
*/
void groupCommand(attr) {

	def apiGroupActionBody = "invalid"
    def hostIP
    def group = attr.dni.split("/")[2]
    def mac = attr.dni.split("/")[0]
    def usernameAPI = settings."z_BridgesUsernameAPI_${mac}"
 
 	settings.z_Bridges.each { bridge ->
        def match = bridge.currentValue("serialNumber").indexOf(mac)
    	if (match != -1) {
        	hostIP = bridge.currentValue("networkAddress") 
            if(hostIP.indexOf(":") == -1) hostIP = hostIP + ":80" // Hue B
    	}
    }

	switch(attr.command) {
    case "on":
    	apiGroupActionBody = ["on": true]
        break
    case "off":
    	apiGroupActionBody = ["on": false]
        break
    case "level":
    	def int level = Math.round((attr.level / 100 * 254))
        apiGroupActionBody = ["on": true, "bri": level]
		break
    case "hue":
    	def int hue = Math.round(attr.hue / 100 * 65535)
    	def int level = Math.round(attr.level / 100 * 254)
        def int sat = Math.round(attr.sat / 100 * 254)
        apiGroupActionBody = ["on": true, "hue": hue, "bri": level, "sat": sat]
    	break
    case "white":
    	def int hue = Math.round(attr.hue / 100 * 65535)
    	def int level = Math.round(attr.level / 100 * 254)
        def int sat = Math.round(attr.sat / 100 * 254)
        apiGroupActionBody = ["on": true, "hue": hue, "bri": level, "sat": sat]
    	break
    case "poll":
		pollRooms(hostIP, usernameAPI)    	
    	break
    default:
    	apiGroupActionBody = null
    }
     

    
    if (apiGroupActionBody == null || apiGroupActionBody == "invalid" ) return
    if (usernameAPI == null) return
    if (group == null) return
    if (group == hostIP) return
       
    def hubAction = new physicalgraph.device.HubAction(
        method: "PUT",
        path: "/api/${usernameAPI.trim()}/groups/${group}/action",
        headers: [HOST: "${hostIP}"],
        null,
        body: apiGroupActionBody,
        [callback: handleRoomPut] )

    sendHubCommand(hubAction)

}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex, 16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]), convertHexToInt(hex[2..3]), convertHexToInt(hex[4..5]), convertHexToInt(hex[6..7])].join(".")
}

private def parseEventMessage(String description) {
    def event = [:]
    def parts = description.split(',')
    parts.each { part ->
        part = part.trim()
        if (part.startsWith('devicetype:')) {
            def valueString = part.split(":")[1].trim()
            event.devicetype = valueString
        }
        else if (part.startsWith('mac:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.mac = valueString
            }
        }
        else if (part.startsWith('networkAddress:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.ip = valueString
            }
        }
        else if (part.startsWith('deviceAddress:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.port = valueString
            }
        }
        else if (part.startsWith('ssdpPath:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.ssdpPath = valueString
            }
        }
        else if (part.startsWith('ssdpUSN:')) {
            part -= "ssdpUSN:"
            def valueString = part.trim()
            if (valueString) {
                event.ssdpUSN = valueString
            }
        }
        else if (part.startsWith('ssdpTerm:')) {
            part -= "ssdpTerm:"
            def valueString = part.trim()
            if (valueString) {
                event.ssdpTerm = valueString
            }
        }
        else if (part.startsWith('headers')) {
            part -= "headers:"
            def valueString = part.trim()
            if (valueString) {
                event.headers = valueString
            }
        }
        else if (part.startsWith('body')) {
            part -= "body:"
            def valueString = part.trim()
            if (valueString) {
                event.body = valueString
            }
        }
    }

    event
}

private def TRACE(message) {
    if(z_Trace) {log.trace message}
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

private def appVerInfo()		{ return getWebData([uri: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/HueSensorData", contentType: "text/plain; charset=UTF-8"], "changelog") }