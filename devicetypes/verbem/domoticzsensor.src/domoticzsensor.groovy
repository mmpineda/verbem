/**
 *  domoticzSensor
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
	definition (name: "domoticzSensor", namespace: "verbem", author: "SmartThings") {
		capability "Temperature Measurement"
		capability "Sensor"
		capability "Battery"
		capability "Actuator"
		capability "Refresh"
        capability "Signal Strength"
        capability "Relative Humidity Measurement"
		capability "Health Check"
		capability "Illuminance Measurement"
		capability "Power Meter"
        capability "Motion Sensor"
        
        attribute "pressure", "number"
        }

	tiles(scale: 2) {
        
		standardTile("battery", "device.battery", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/battery.png"
		}
        
		standardTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2, canChangeIcon: true) {
			state "temparature", label:'${currentValue} C', unit:"", icon:"st.Weather.weather2",
							backgroundColors:[
							// Celsius
							[value: 0, color: "#153591"],
							[value: 7, color: "#1e9cbb"],
							[value: 15, color: "#90d2a7"],
							[value: 23, color: "#44b621"],
							[value: 28, color: "#f1d801"],
							[value: 35, color: "#d04e00"],
							[value: 37, color: "#bc2323"],
							// Fahrenheit
							[value: 40, color: "#153591"],
							[value: 44, color: "#1e9cbb"],
							[value: 59, color: "#90d2a7"],
							[value: 74, color: "#44b621"],
							[value: 84, color: "#f1d801"],
							[value: 95, color: "#d04e00"],
							[value: 96, color: "#bc2323"]
					]		
            state "Error", label:"Install Error", backgroundColor: "#bc2323"
        }

		standardTile("humidity", "device.humidity", inactiveLabel: false, width: 2, height: 2) {
			state "humidity", label:'${currentValue}% humidity', unit:"", icon:"st.Weather.weather12"
		}

		standardTile("motion", "device.motion", inactiveLabel: false, width: 2, height: 2) {
			state "motion", label:'${currentValue}', unit:"", icon:""
		}
        
 		standardTile("power", "device.power",  inactiveLabel: false, width: 2, height: 2) {
			state "power", label:'${currentValue}', unit:"", icon:""
		}
        
 		standardTile("illuminance", "device.illuminance",  inactiveLabel: false, width: 2, height: 2) {
			state "illuminance", label:'${currentValue} Lux', unit:"", icon:""
		}
                
 		standardTile("pressure", "device.pressure",  inactiveLabel: false, width: 2, height: 2) {
			state "pressure", label:'${currentValue} hPa', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/barometer-icon-png-5.png"
		}

		standardTile("rssi", "device.rssi", inactiveLabel: false,  width: 2, height: 2) {
            state "rssi", label:'Signal ${currentValue}', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/network-signal.png"
        }

		standardTile("refresh", "device.refresh", decoration: "flat", inactiveLabel: false,  width: 2, height: 2) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		
        htmlTile(name:"graphHTML",
			action: "getGraphHTML",
			refreshInterval: 1,
			width: 6,
			height: 4,
			whitelist: ["www.gstatic.com"])

		main "temperature"
		details(["temperature", "humidity", "pressure", "power", "illuminance", "motion", "battery", "rssi", "refresh"])
	}
}

mappings {
	path("/getGraphHTML") {action: [GET: "getGraphHTML"]}
}


// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'motion' attribute

}

def refresh() {
	log.debug "Executing 'refresh'"

    if (parent) {
        parent.domoticz_poll(getIDXAddress())
    }

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

/*----------------------------------------------------*/
/*			execute event can be called from the service manager!!!
/*----------------------------------------------------*/
def generateEvent (Map results) {
    results.each { name, value ->
    	def v = value         
        log.info "generateEvent " + name + " " + v
        sendEvent(name:"${name}", value:"${v}")
        }
        return null
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
        sendEvent(name: "temperature", value: "Error", descriptionText: "$device.displayName You cannot use this DTH without the related SmartAPP Domoticz Server", isStateChange: true)
    }
}

def getStartTime() {
	def startTime = 24

	return startTime
}

String getDataString(Integer seriesIndex) {
	/*def dataString = ""
	def dataTable = []
	switch (seriesIndex) {
		case 1:
			dataTable = state.energyTableYesterday
			break
		case 2:
			dataTable = state.powerTableYesterday
			break
		case 3:
			dataTable = state.energyTable
			break
		case 4:
			dataTable = state.powerTable
			break
	}
	dataTable.each() {
		def dataArray = [[it[0],it[1],0],null,null,null,null]
		dataArray[seriesIndex] = it[2]
		dataString += dataArray.toString() + ","
	} */
    def dataString = "2,3,5,7,10"
	return dataString
}

def getGraphHTML() {
		def smokeImg = getSmokeImg()
		def carbonImg = getCarbonImg()
		def html = """
		<!DOCTYPE html>
		<html>
			<head>
				<meta http-equiv="cache-control" content="max-age=0"/>
				<meta http-equiv="cache-control" content="no-cache"/>
				<meta http-equiv="expires" content="0"/>
				<meta http-equiv="expires" content="Tue, 01 Jan 1980 1:00:00 GMT"/>
				<meta http-equiv="pragma" content="no-cache"/>
				<meta name="viewport" content="width = device-width, user-scalable=no, initial-scale=1.0">
				<style>
				</style>
			</head>
			<body>
			  <div style="padding: 10px;">
				  <section class="sectionBg">
					  <h3>Alarm Status</h3>
					  <table class="devInfo">
					    <col width="48%">
					    <col width="48%">
					    <thead>
						  <th>Smoke Detector</th>
						  <th>Carbon Monoxide</th>
					    </thead>
					    <tbody>
						  <tr>
						    <td>
								<img class='alarmImg' src="${smokeImg?.img}">
								<span class="${smokeImg?.captionClass}">${smokeImg?.caption}</span>
							</td>
						    <td>
								<img class='alarmImg' src="${carbonImg?.img}">
								<span class="${carbonImg?.captionClass}">${carbonImg?.caption}</span>
							</td>
						  </tr>
					    </tbody>
					  </table>
				  </section>
				  <br>
				  <section class="sectionBg">
				  	<h3>Device Info</h3>
					<table class="devInfo">
						<col width="33%">
						<col width="33%">
						<col width="33%">
						<thead>
						  <th>Network Status</th>
						  <th>Power Type</th>
						  <th>API Status</th>
						</thead>
						<tbody>
						  <tr>
						  <td${state?.onlineStatus != "online" ? """ class="redText" """ : ""}>${state?.onlineStatus.toString().capitalize()}</td>
						  <td>${state?.powerSource.toString().capitalize()}</td>
						  <td${state?.apiStatus != "Good" ? """ class="orangeText" """ : ""}>${state?.apiStatus}</td>
						  </tr>
						</tbody>
					</table>
				</section>
				<section class="sectionBg">
					<table class="devInfo">
						<col width="40%">
						<col width="20%">
						<col width="40%">
						<thead>
						  <th>Firmware Version</th>
						  <th>Debug</th>
						  <th>Device Type</th>
						</thead>
						<tbody>
						  <tr>
							<td>v${state?.softwareVer.toString()}</td>
							<td>${state?.debugStatus}</td>
							<td>${state?.devTypeVer.toString()}</td>
						  </tr>
						</tbody>
				  	</table>
				  </section>
				  <section class="sectionBg">
	  				<table class="devInfo">
					  <thead>
						<th>Last Check-In</th>
						<th>Data Last Received</th>
					  </thead>
					  <tbody>
						<tr>
						  <td class="dateTimeText">${state?.lastConnection.toString()}</td>
						  <td class="dateTimeText">${state?.lastUpdatedDt.toString()}</td>
						</tr>
					  </tbody>
					</table>
				  </section>
				</div>
			  <script>
				  function reloadProtPage() {
					  var url = "https://" + window.location.host + "/api/devices/${device?.getId()}/getInfoHtml"
					  window.location = url;
				  }
			  </script>
			  <div class="pageFooterBtn">
				  <button type="button" class="btn btn-info pageFooterBtn" onclick="reloadProtPage()">
					<span>&#10227;</span> Refresh
				  </button>
			  </div>
			</body>
		</html>
		"""
		render contentType: "text/html", data: html, status: 200
}