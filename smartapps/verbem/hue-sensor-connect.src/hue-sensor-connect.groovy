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
 *
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
private def runningVersion() 	{ "1.00"}

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
	
	if (z_Bridges) {
    	def canPoll = false
    	z_Bridges.each { dev ->
        	def sN = dev.currentValue("serialNumber")
            if ("z_BridgesUsernameAPI_${sN}") canPoll = true        	
        }
        if (canPoll) poll1Minute()
    }

    def inputBridges= [
            name			: "z_Bridges",
            type			: "capability.bridge",
            title			: "Select Hue Bridges",
            multiple		: true,
            submitOnChange	: true,
            required		: false
        ] 
	
		dynamicPage(name: "pageBridges", title: "Bridges found by Super Lan Connect", uninstall: true, install:true) {
            section("Please select Hue Bridges that contain sensors") {
                input inputBridges          
            }
            if (z_Bridges) {
                z_Bridges.each { dev ->
                    def serialNumber = dev.currentValue("serialNumber")
                    def networkAddress = dev.currentValue("networkAddress")
					
                    
                    section("Bridge ${dev}, Serial:${serialNumber}, IP:${networkAddress}, username for API is in device in IDE", hideable:true) {
                    	href(name: "${dev.id}", title: "IDE Bridge device",required: false, style: "external", url: "${getApiServerUrl()}/device/show/${dev.id}", description: "tap to view device in IDE")
                        input "z_BridgesUsernameAPI_${serialNumber}", "text", required:true, title:"Username for API", submitOnChange:true
                    }
                    
                }	
                if (state.devices) {
                    section("Associate a motion sensor with a Hue Sensor for boosted polling during motion, sensor will be checked 10 times with inbetween pause ") {
                    input "z_pollTime", "enum", required:true, title:"Pause between poll in msec > 300ms", default:300, options:[300, 400, 500, 600, 700, 800, 900, 1000]
                    state.devices.each { item, sdev ->
                        input "z_motionSensor_${sdev.dni}", "capability.motionSensor", required:false, title:"Associated motion sensor ${sdev.name} "
                        }	
                    }
                }
                else section("No sensors found yet, wait a minute or so, or tap Done and reenter App")
			}
		}
}

def installed() {
	log.trace "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.trace "Updated with settings: ${settings}"
	unsubscribe()
	unschedule()
	initialize()
}

def uninstalled() {
	// Remove bridgedevice connection to allow uninstall of smartapp even though bridge is listed
	// as user of smartapp
    
	log.trace "Uninstall"
	unschedule()
    state.devices.each { key, dev -> deleteChildDevice(dev.dni)}
}

def initialize() {
	log.debug "Initializing"
	setupDeviceWatch()
    
    schedule("2015-01-09T12:00:00.000-0600", notifyNewVersion)
	
    if (settings.z_Bridges) {
    	subscribeToMotionEvents()
        runEvery1Minute("poll1Minute")
	}
}

def notifyNewVersion() {

	if (appVerInfo().split()[1] != runningVersion()) {
    	sendNotificationEvent("Hue Sensor App has a newer version, please visit IDE to update app/devices")
    }
}

def poll1Minute() {

    settings.z_Bridges.each { dev ->

		def serialNumber = dev.currentValue("serialNumber")
        def networkAddress = dev.currentValue("networkAddress")

		if (settings."z_BridgesUsernameAPI_${serialNumber}") {
        	poll(networkAddress, settings."z_BridgesUsernameAPI_${serialNumber}")
    		}
        
		}
}

def pollBurst(evt) {
	// Looking for the last changed Hue Sensor button every defined ms!! for 10 times
    // only happens if you defined a normal motion sensor associated with a Hue sensor and motion has been detected.


	settings.each { key, value ->
    	if (value.toString() == evt.displayName.toString()) {
            def splits = key.split('/')
            def sensor = splits[2].toString()
            splits = splits[0].split('_')
            def mac = splits[2].toString()
            def usernameAPI = settings."z_BridgesUsernameAPI_${mac}"

			settings.z_Bridges.each { dev ->
				def mac2 = dev.currentValue("serialNumber").toString()           	
                if (mac == mac2) {
                	def hostIP = dev.currentValue("networkAddress")
                    log.info "Turbo for ${value} defined in ${key}, mac ${mac}, hostIP ${hostIP}, username ${usernameAPI}"
					def i = 0
                    //first set of polls
                    for (i = 0; i <10; i++) {
                        pollSensor(hostIP, usernameAPI, sensor)    	
                        pause settings.z_pollTime.toInteger()
                    }
                    //afterburner
                    /*for (i = 0; i <5; i++) {
                        pollSensor(hostIP, usernameAPI, sensor)    	
                        pause 1000
                    }*/

                }
            }
        }
    }   
}

def subscribeToMotionEvents() {

	state.devices.each { dni, sensor -> 
    	if (settings."z_motionSensor_${dni}") {
        	def motionSensor = settings."z_motionSensor_${dni}"
        	log.info "subscribe to motion for ${motionSensor} defined for ${sensor.name}"
            subscribe(motionSensor, "motion.active", pollBurst)
        }
    }
}

private setupDeviceWatch() {
	def hub = location.hubs[0]
	// Make sure that all child devices are enrolled in device watch
	getChildDevices().each {
		it.sendEvent(name: "DeviceWatch-Enroll", value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${hub?.hub?.hardwareID}\"}")
	}
}

private updateSensorState(messageBody, mac) {
	def sensors = [:]
	// create sensor list of this bridge mac
    state.devices.each { key, sensor ->
    	if (sensor.mac == mac) {
        	sensors[sensor.item] = sensor
        }
    }
    

	// Copy of sensors used to locate old sensors in state that are no longer on bridge
	def toRemove = [:]
	toRemove << sensors

	messageBody.each { k, v ->

		if (v instanceof Map) {
			if (sensors[k] == null) {
				sensors[k] = [:]
			}
			toRemove.remove(k)
		}
	}

	// Remove sensors from state that are no longer discovered
	toRemove.each { k, v ->
    	
		log.warn "${sensors[k].name} no longer exists on bridge ${mac}, removing dni ${sensors[k].dni}"
        def dni = sensors[k].dni
        deleteChildDevice(dni)
		state.devices.remove(dni)
	}
}

def parse(childDevice, description) {
	log.warn "[Parse] entered ${childDevice} ${description}"
}

def handleSensorCall(physicalgraph.device.HubResponse hubResponse) {

    def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)
    if (parsedEvent.body.contains("error")) log.error "Error in ${mac} unauth user?"
    def body = hubResponse.json

    body.each { item, sensor ->
        if (sensor.type == "ZGPSwitch") {
            def dni = mac + "/sensor/" + item
            def sensorDev = getChildDevice(dni)
            if (!sensorDev) {
            	log.info "Add Sensor ${dni}"
            	sensorDev = addChildDevice("verbem", "Hue Tap", dni, null, [name:sensor.name, label:sensor.name, completedSetup:true])
                state.devices[dni] = [
                	'lastUpdated'	: sensor.state.lastupdated, 
                    'mac'			: mac, 
                    'item'			: item, 
                    'dni'			: dni,
                    'name'			: sensor.name,
                    'uniqueId'		: sensor.uniqueid,
                    'type'			: sensor.type
                    ]
                }
            else {
            	if (state.devices[dni].name != sensor.name) {
                	state.devices[dni].name = sensor.name
                    sensorDev.name = sensor.name
                    sensorDev.label = sensor.name
                }
                
                if (state.devices[dni].lastUpdated != sensor.state.lastupdated) {
                	state.devices[dni].lastUpdated = sensor.state.lastupdated
            		log.info "Buttonpress Sensor ${dni} ${sensor.state.buttonevent}"
                    switch (sensor.state.buttonevent) {
                        case "34":
                            sensorDev.buttonEvent(1)
                            break
                        case "16":
                            sensorDev.buttonEvent(2)
                            break
                        case "17":
                            sensorDev.buttonEvent(3)
                            break
                        case "18":
                            sensorDev.buttonEvent(4)
                            break
                        }
                    }
            	}
            }
        }
        
    updateSensorState(body, mac)
}

def handleSensorBurst(physicalgraph.device.HubResponse hubResponse) {

    def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)
    def body = hubResponse.json

	state.devices.each { key, sensor ->
        if (sensor.uniqueId == body.uniqueid) {
            def dni = sensor.dni
            def sensorDev = getChildDevice(dni)
            if (!sensorDev) {
				log.error "Sensor ${dni} not found for update"
                }
            else {
                if (state.devices[dni].lastUpdated != body.state.lastupdated) {
                	state.devices[dni].lastUpdated = body.state.lastupdated
            		log.info "Buttonpress Sensor ${dni} ${body.state.buttonevent}"
                    switch (body.state.buttonevent) {
                        case "34":
                            sensorDev.buttonEvent(1)
                            break
                        case "16":
                            sensorDev.buttonEvent(2)
                            break
                        case "17":
                            sensorDev.buttonEvent(3)
                            break
                        case "18":
                            sensorDev.buttonEvent(4)
                            break
                        }
                    }
            	}
            }
        }
}

private poll(hostIP, usernameAPI) {
	def uri = "/api/${usernameAPI}/sensors/"
	sendHubCommand(new physicalgraph.device.HubAction("GET ${uri} HTTP/1.1\r\n" + "HOST: ${hostIP}\r\n\r\n", physicalgraph.device.Protocol.LAN, null, [callback:handleSensorCall]))
}

private pollSensor(hostIP, usernameAPI, sensor) {
	def uri = "/api/${usernameAPI}/sensors/${sensor}"
	sendHubCommand(new physicalgraph.device.HubAction("GET ${uri} HTTP/1.1\r\n" + "HOST: ${hostIP}\r\n\r\n", physicalgraph.device.Protocol.LAN, null, [callback:handleSensorBurst]))
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

def getWebData(params, desc, text=true) {
	try {
		httpGet(params) { resp ->
			if(resp.data) {
				if(text) {
					return resp?.data?.text.toString()
				} else { return resp?.data }
			}
		}
	}
	catch (ex) {
		if(ex instanceof groovyx.net.http.HttpResponseException) {
			log.error "${desc} file not found"
		} else {
			log.error "getWebData(params: $params, desc: $desc, text: $text) Exception:", ex
		}
		//sendExceptionData(ex, "getWebData")
		return "${label} info not found"
	}
}


private def appVerInfo()		{ return getWebData([uri: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/HueSensorData", contentType: "text/plain; charset=UTF-8"], "changelog") }