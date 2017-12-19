/**
 *  domoticzPowerReport
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
 */
metadata {
	definition (name: "domoticzPowerReport", namespace: "verbem", author: "Martin Verbeek") {
		capability "Sensor"
		capability "Actuator"
		capability "Health Check"
		capability "Power Meter"
        capability "Image Capture"
        
        attribute "powerTotal", "number"
        }
        
	tiles(scale: 2) {       
        multiAttributeTile(name:"powerReport", type:"generic", width:6, height:4) {
            tileAttribute("device.power", key: "PRIMARY_CONTROL") {
                attributeState "level", label:'${currentValue}', defaultState: true, action: "take", backgroundColors:[                   
                    [value: 0, color: "#66ff33"],      	//green
                    [value: 100, color: "#ffff00"],    	//yellow
                    [value: 250, color: "#ffcc00"],		//dark yellow	
                    [value: 500, color: "#ff9900"],		//light orange
                    [value: 1000, color: "#cc6600"],	//dark orange
                    [value: 2000, color: "#ff0000"]		//red
                ]
            }
            tileAttribute("device.powerTotal", key: "SECONDARY_CONTROL") {
                attributeState "powerTotal", label:'${currentValue} kWh'

            }
        }
        
        carouselTile("graph", "device.image", width: 6, height: 4)
	}

	main "powerReport"
    details(["powerReport", "graph"])
}

// parse events into attributes
def parse(String description) {
	log.error "Parsing '${description}'"
}

def createChild(idx) {
}

def totalKWH() {
}

def totalNow() {
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
        sendEvent(name: "powerTotal", value: "Error", descriptionText: "$device.displayName You cannot use this DTH without the related SmartAPP Domoticz Server", isStateChange: true)
    }
}

def handleMeasurements(values)
{
	if(values instanceof Collection)
    {
    	// For some reason they show up out of order
    	values.sort{a,b -> a.t <=> b.t}
		state.values = values;
    }
    else
    {
    	sendEvent(name: "power", value: Math.round(values))
    }

    //log.debug("State now contains ${state.toString().length()}/100000 bytes")
}

String getDataString()
{
	def dataString = "['Time', 'Power', {role: 'style'}] , ['00:00', 10, '#66ff33'], ['01:00', 0, '#66ff33'], ['02:00', 0, '#66ff33'], ['03:00', 0, '#66ff33'], ['04:00', 0, '#66ff33'], ['05:00', 0, '#66ff33'], ['06:00', 0, '#66ff33'], ['07:00', 20, '#66ff33'], ['08:00', 50, '#66ff33'], ['09:00', 20, '#66ff33'], ['10:00', 120, '#66ff33'], ['11:00', 1220, '#ff0000'], ['12:00', 1020, '#ff0000'], ['13:00', 920, '#ff0000'], ['14:00', 1720, '#66ff33'], ['15:00', 1020, '#66ff33'], ['16:00', 0, '#66ff33'], ['16:00', 0, '#66ff33'], ['17:00', 210, '#66ff33'], ['18:00', 230, '#66ff33']"
	return dataString
    
}

def take() {
	log.debug "Take()"

    parent.domoticz_counters(device.deviceNetworkId.tokenize(':')[2].toString(),"day")


	def chd = "t:60,40,20.10,5,35,100,12,40,45,13,200"
    //String result = stringList.join(",")
    def chco = "0000FF"
	def params = [uri: "https://image-charts.com/chart?chs=900x600&chd=${chd}&cht=bvs&chds=a&chxt=x,y&chts=0000FF,20&chco=${chco}"]
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