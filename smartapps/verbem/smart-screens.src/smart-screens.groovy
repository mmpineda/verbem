/**
 *  Smart Screens
 *
 *  Copyright 2015 Martin Verbeek
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
 *	App is using forecast.io to get conditions for your location, you need to get an APIKEY
 * 	from developer.forecast.io and your longitude and latitude is being pulled from the location object
 *  position of the sun is calculated in this app, thanks to the formaula´s of Mourner at suncalc github
 *
 *	Select the blinds you want to configure (it will use commands Open/Stop/Close) so they need to be on the device.
 *  Select the position they are facing (North-East-South-West) or multiple...these are the positions that they will need protection from wind or sun
 *  WindForce protection Select condition to Close or to Open (Shutters you may want to close when lots of wind, sunscreens you may want to Open
 *  cloudCover percentage is the condition to Close (Sun is shining into your room)
 *  Select interval to check conditions
 * 
 */

import groovy.json.*
import java.Math.*

definition(
    name: "Smart Screens",
    namespace: "verbem",
    author: "Martin Verbeek",
    description: "Automate Up and Down of Sun Screens, Blinds and Shutters based on Weather Conditions",
    category: "Convenience",
    oauth: true,
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

{
	appSetting "clientId"
	appSetting "clientSecret"
    appSetting "serverUrl"
}


mappings {
    path("/oauth/initialize") {action: [GET: "getNetatmoAuth"]}
    path("/oauth/callback") {action: [GET: "getNetatmoCallback"]}
}

preferences {
    page name:"pageSetupForecastIO"
    page name:"pageConfigureBlinds"
    page name:"pageForecastIO"
	page(name:"auth",title: "Netatmo Authentication", content: "pageAuth", nextPage: "pageSetupForecastIO", install: false)
}

def pageSetupForecastIO() {
    TRACE("pageSetupForecastIO()")

    def pageSetupLatitude = location.latitude.toString()
    def pageSetupLongitude = location.longitude.toString()
	
    def pageSetupAPI = [
        name:       "pageSetupAPI",
        type:       "string",
        title:      "Darksky.net API key",
        multiple:   false,
        required:   true
    	]
              
    def inputBlinds = [
        name:       "z_blinds",
        type:       "capability.switch",
        title:      "Which blinds/screens/shutters?",
        multiple:   true,
        required:   false
    ] 
    
    def pageProperties = [
        name:       "pageSetupForecastIO",
        //title:      "Status",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]

    def inputWindForceMetric = [
        name:       "z_windForceMetric",
        type:       "enum",
        options:	["mph", "km/h"],
        title:      "Select wind metric system",
        multiple:   false,
        required:   true
    ]
    
        def inputTRACE = [
        name:       "z_TRACE",
        type:       "bool",
        default:	false,
        title:      "Put out trace log",
        multiple:   false,
        required:   true
    ]
    
    return dynamicPage(pageProperties) {

        section("Darksky.net API and API Website") {
            input pageSetupAPI

        	href(name: "hrefNotRequired",
             title: "Darksky.net APi page",
             required: false,
             style: "external",
             url: "https://darksky.net/dev/",
             description: "tap to view Darksky website in mobile browser")
        }
        
        section("Netatmo Interface") {  
            href "auth", title:"Connect to Netatmo", description:"Tap to enter credentials"
		}
        
        section("Setup Menu") {
            input inputBlinds

            if(inputBlinds) {
                    href "pageConfigureBlinds", title:"Configure Blinds", description:"Tap to open"
                    input inputWindForceMetric
                }
        }
        
        section("Info Page") {

              href "pageForecastIO", title:"Environment Info", description:"Tap to open"
              
        }

        section([title:"Options", mobileOnly:true]) {
            label title:"Assign a name", required:false
            input inputTRACE
        }
    }
}

/*-----------------------------------------------------------------------*/
// Show Configure Blinds Page
/*-----------------------------------------------------------------------*/

def pageConfigureBlinds() {
    TRACE("pageConfigureBlinds()")

def pageProperties = [
        name:       "pageConfigureBlinds",
        title:      "Configure Blinds",
        nextPage:   "pageSetupForecastIO",
        uninstall:  false
    ]

    return dynamicPage(pageProperties) {
        z_blinds.each {
        	def devId = it.id
            section(it.name) 
            	{           	
                input 	"z_blindsOrientation_${devId}", "enum", options:["N", "NW", "W", "SW", "S", "SE", "E", "NE"],title:"Select Relevent Orientation",multiple:true,required:true  
                input	"z_windForceCloseMax_${devId}","number",title:"Allow Operation below under which windforce ${z_windForceMetric}",multiple:false,required:false,default:0                 
                input	"z_closeMaxAction_${devId}","enum",title: "What Operation?", options: ["Down","Up","Stop"], default:"Stop" 
                input 	"z_cloudCover_${devId}","enum",title:"Go Down under cloudcover% (0=clear sky)", options:	["10","20","30","40","50","60","70","80","90","100"],multiple:false,required:false,default:30                
                input 	"z_windForceCloseMin_${devId}","number",title:"Force Operation below above which windforce ${z_windForceMetric}",multiple:false,required:false,default:999                     
                input	"z_closeMinAction_${devId}","enum",title: "What Operation?", options: ["Down","Up","Stop"], default:"Down"
                }
        }
    }
}

/*-----------------------------------------------------------------------*/
// Show Sun/Wind ForecastIO API last output Page
/*-----------------------------------------------------------------------*/

def pageForecastIO() {
    TRACE("pageForecastIO()")
    state.sunBearing = getSunpath()
    def sc = sunCalc()

def pageProperties = [
        name:       "pageForecastIO",
        title:      "Current Sun and Wind Info",
        nextPage:   "pageSetupForecastIO",
        refreshInterval: 10,
        uninstall:  false
    ]    
   
   	    return dynamicPage(pageProperties) {

        section("Wind") {
        	paragraph "Speed ${state.windSpeed}" 
        	paragraph "Direction ${state.windBearing}"
		}
        
        section("Sun") {
            paragraph "cloud Cover ${state.cloudCover}"
            paragraph "Direction ${state.sunBearing}"
		}
        
        section("SunCalc") {
        	paragraph "Latitude ${state.lat}"
        	paragraph "Longitude ${state.lng}"
            paragraph "Suncoord ${state.c}"
            paragraph "Azimuth ${sc.azimuth}"
            paragraph "Altitude ${sc.altitude}"
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
	initialize()
}

def initialize() {
	
    state.sunBearing = ""
    state.windBearing = ""
    state.windSpeed = 0
    state.cloudCover = 100
        
    subscribe(location, "sunset", stopSunpath,  [filterEvents:true])
    subscribe(location, "sunrise", startSunpath,  [filterEvents:true])
    
    checkForWind()
    checkForSun()
    
    runEvery30Minutes(checkForSun)
    runEvery3Hours(checkForClouds)
    runEvery10Minutes(checkForWind)
}

def handlerLocation(evt) {
	TRACE("handlerLocation")

}


/*-----------------------------------------------------------------------------------------*/
/*	This routine will get information relating to the weather at location and current time
/*-----------------------------------------------------------------------------------------*/
def getForecastIO() {

    TRACE("getForecastIO for Lon:${location.longitude} Lat:${location.latitude}")
	TRACE("/forecast/${pageSetupAPI}/${location.latitude},${location.longitude}")
    def units = "si"
    if (settings.z_windForceMetric == "mph") {units = "us"}
    def params = [
        uri: "https://api.darksky.net",
        path: "/forecast/${pageSetupAPI}/${location.latitude},${location.longitude}",
        entType: "application/json", 
        query: ["units" : units, "exclude" : "minutely,hourly,daily,flags"]
    ]

    try {
        httpGet(params) { resp ->
       		if(resp.data.alerts?.title == !null ) {
            	log.info "Weather alert : " + resp.data.alerts.title
            	}
            TRACE(resp.data.timezone)
            TRACE(resp.data.currently)
            return ["windBearing" : calcBearing(resp.data.currently.windBearing), "windSpeed" : resp.data.currently.windSpeed, "cloudCover" : resp.data.currently.cloudCover]
        	}
    	} 
        catch (e) {
        	log.error "something went wrong: $e"
            return ["windBearing" : "not found", "windSpeed" : "not found", "cloudCover" : "not found"]
    	}
}

/*-----------------------------------------------------------------------------------------*/
/*	This routine will get information relating to the SUN´s position
/*-----------------------------------------------------------------------------------------*/
def getSunpath() {

    TRACE("getSunpath")
    def sp = sunCalc()
    return calcBearing(sp.azimuth)
   
}

/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get latest SUN related info on position
/*	and will check the blinds that provide sun protection if they need to be closed or opened
/*-----------------------------------------------------------------------------------------*/
def checkForSun(evt) {

TRACE("SUN checkForSun")
state.sunBearing = getSunpath()

settings.z_blinds.each {

	String findID = it.id
    def blindParams = [:]
    
    /*-----------------------------------------------------------------------------------------*/
    /*	SUN Fill the blindParams MAP object 
	/*-----------------------------------------------------------------------------------------*/    
    settings.each {
            if(it.key == "z_blindsOrientation_${findID}") { blindParams.blindsOrientation = it.value }
            if(it.key == "z_windForceCloseMax_${findID}") { blindParams.windForceCloseMax = it.value }
            if(it.key == "z_windForceCloseMin_${findID}") { blindParams.windForceCloseMin = it.value }
            if(it.key == "z_cloudCover_${findID}") { blindParams.cloudCover = it.value }
            if(it.key == "z_closeMinAction_${findID}") { blindParams.closeMinAction = it.value }        
            if(it.key == "z_closeMaxAction_${findID}") { blindParams.closeMaxAction = it.value }        
            }
            
	/*-----------------------------------------------------------------------------------------*/
    /*	SUN determine if we need to close or open again if cloudcover above defined
    /*		Only if SUN is in position to shine onto the blinds
    /*		Only if windspeed is below defined point (above it may damage sun screens)
    /*			this is only needed if direction of wind is on the screens
    /*	 
    /*-----------------------------------------------------------------------------------------*/                 
    if(blindParams.blindsOrientation.contains(state.sunBearing)) 
    {
        TRACE("SUN Relevent Sunbearing ${state.sunBearing} for ${it.name} orientation ${blindParams.blindsOrientation} ")
        if(state.cloudCover.toInteger() < blindParams.cloudCover.toInteger()) 
        {
            TRACE("SUN Sunny enough, cloudcover is ${state.cloudCover.toInteger()}% for ${it.name} defined cloudcover ${blindParams.cloudCover}%")
            
            if(state.windSpeed.toInteger() < blindParams.windForceCloseMax.toInteger() && blindParams.blindsOrientation.contains(state.windBearing)) 
            {
                TRACE("SUN Screens allowed to go ${blindParams.closeMaxAction} for windspeed(${state.windSpeed.toInteger()} ${settings.z_windForceMetric}), Blind ${it.name}")
                if (blindParams.closeMaxAction == "Down") 	{it.close()}
                if (blindParams.closeMaxAction == "Up") 	{it.open()}
                if (blindParams.closeMaxAction == "Stop") 	{it.stop()}
            }
            else 
            {
                TRACE("SUN Screens allowed to go ${blindParams.closeMaxAction} no relevant windbearing(${state.windBearing}), Blind ${it.name}")
                if (blindParams.closeMaxAction == "Down") 	{it.close()}
                if (blindParams.closeMaxAction == "Up") 	{it.open()}
                if (blindParams.closeMaxAction == "Stop") 	{it.stop()}
            }
        }
    }
    else
    {
    	it.open()
    }
}

return null
}

/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get latest SUN related info on position
/*	and will check the blinds that provide sun protection if they need to be opened
/*-----------------------------------------------------------------------------------------*/
def checkForClouds(evt) {

TRACE("SUN checkForClouds")
state.sunBearing = getSunpath()

settings.z_blinds.each {

	String findID = it.id
    def blindParams = [:]
    
    /*-----------------------------------------------------------------------------------------*/
    /*	SUN Fill the blindParams MAP object 
	/*-----------------------------------------------------------------------------------------*/    
    settings.each {
            if(it.key == "z_blindsOrientation_${findID}") { blindParams.blindsOrientation = it.value }
            if(it.key == "z_windForceCloseMax_${findID}") { blindParams.windForceCloseMax = it.value }
            if(it.key == "z_windForceCloseMin_${findID}") { blindParams.windForceCloseMin = it.value }
            if(it.key == "z_cloudCover_${findID}") { blindParams.cloudCover = it.value }
            if(it.key == "z_closeMinAction_${findID}") { blindParams.closeMinAction = it.value }        
            if(it.key == "z_closeMaxAction_${findID}") { blindParams.closeMaxAction = it.value }        
            }
            
	/*-----------------------------------------------------------------------------------------*/
    /*	SUN determine if we need to close or open again if cloudcover above defined
    /*		Only if SUN is in position to shine onto the blinds
    /*		Only if windspeed is below defined point (above it may damage sun screens)
    /*			this is only needed if direction of wind is on the screens
    /*	 
    /*-----------------------------------------------------------------------------------------*/                 
    if(blindParams.blindsOrientation.contains(state.sunBearing)) 
    {
        TRACE("SUN Relevent Sunbearing ${state.sunBearing} for ${it.name} defined ${blindParams.blindsOrientation} ")
        if(state.cloudCover.toInteger() > blindParams.cloudCover.toInteger()) 
        {
            TRACE("SUN NOT Sunny enough, cloudcover ${state.cloudCover.toInteger()}% for ${it.name} defined cloudcover ${blindParams.cloudCover} ")
            it.open()
        }
    }
}

return null
}
/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get latest WIND related info on position
/*	and will check the blinds if they need to be closed or can be opened
/*-----------------------------------------------------------------------------------------*/
def checkForWind(evt) {

def windParms = getForecastIO()
state.windBearing = windParms.windBearing
state.windSpeed = windParms.windSpeed
if (settings.z_windForceMetric == "km/h") {state.windSpeed = windParms.windSpeed * 3.6}
state.cloudCover = windParms.cloudCover * 100

TRACE("WIND checkForWind Bearing: ${state.windBearing} Speed: ${state.windSpeed} ${settings.z_windForceMetric} Cloudcover: ${state.cloudCover} ")

settings.z_blinds.each {

	String findID = it.id
    def blindParams = [:]
    
    /*-----------------------------------------------------------------------------------------*/
    /*	WIND Fill the blindParams MAP object 
	/*-----------------------------------------------------------------------------------------*/          
    settings.each {
            if(it.key == "z_blindsOrientation_${findID}") { blindParams.blindsOrientation = it.value }
            if(it.key == "z_windForceCloseMax_${findID}") { blindParams.windForceCloseMax = it.value }
            if(it.key == "z_windForceCloseMin_${findID}") { blindParams.windForceCloseMin = it.value }
            if(it.key == "z_cloudCover_${findID}") { blindParams.cloudCover = it.value }
            if(it.key == "z_closeMinAction_${findID}") { blindParams.closeMinAction = it.value }        
            if(it.key == "z_closeMaxAction_${findID}") { blindParams.closeMaxAction = it.value }        
            }
            
	/*-----------------------------------------------------------------------------------------*/
    /*	WIND determine if we need to close (or OPEN if wind speed is above allowed max for blind)
    /*-----------------------------------------------------------------------------------------*/      
    if(blindParams.blindsOrientation.contains(state.windBearing)) {
        TRACE("WIND Relevent Windbearing ${state.windBearing} for ${it.name} ")
        if(state.windSpeed.toInteger() > blindParams.windForceCloseMin.toInteger()) {
            TRACE("WIND Shutters High wind force down windSpeed(${state.windSpeed.toInteger()} ${settings.z_windForceMetric}), closing ${it.name}")
            if (blindParams.closeMinAction == "Down") it.close()
            if (blindParams.closeMinAction == "Up") it.open()
            if (blindParams.closeMinAction == "Stop") it.stop()
        	}
        if(state.windSpeed.toInteger() > blindParams.windForceCloseMax.toInteger()) {
            TRACE("WIND Screens High wind force ${blindParams.closeMaxAction} windSpeed(${state.windSpeed.toInteger()} ${settings.z_windForceMetric}), opening ${it.name}")
            if (blindParams.closeMinAction == "Down") it.close()
            if (blindParams.closeMinAction == "Up") it.open()
            if (blindParams.closeMinAction == "Stop") it.stop()
            }
    	}
    }

return null
}

/*-----------------------------------------------------------------------------------------*/
/*	this will stop the scheduling of events called at SUNSET
/*-----------------------------------------------------------------------------------------*/
def stopSunpath(evt) {
	TRACE("Stop Scheduling")
	unschedule(checkForSun)
    unschedule(checkForClouds)
    unschedule(checkForWind)
	return null
}

/*-----------------------------------------------------------------------------------------*/
/*	this will start the scheduling of events called at SUNRISE
/*-----------------------------------------------------------------------------------------*/
def startSunpath(evt) {
	TRACE("start Scheduling")
	runEvery30Minutes(checkForSun)
    runEvery3Hours(checkForClouds)
    runEvery10Minutes(checkForWind)
	return null
}

/*-----------------------------------------------------------------------------------------*/
/*	this routine will return the wind or sun direction
/*-----------------------------------------------------------------------------------------*/
private def calcBearing(degree) {
		
        switch (degree.toInteger()) {
        case 0..23:
            return "N"
            break;
        case 23..68:
            return "NE"
            break;
        case 68..113:
            return "E"
            break;
        case 113..158:
            return "SE"
            break;
        case 158..203:
            return "S"
            break;
        case 203..248:
            return "SW"
            break;
        case 248..293:
            return "W"
            break;
        case 293..338:
            return "NW"
            break;
        case 338..360:
            return "N"
            break;
		default :
        	return "not found"
        	break;
        } 
 
}
private def TRACE(message) {

if(settings.z_TRACE) {log.trace message}

}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Azimuth and Alitude for current Time
/*-----------------------------------------------------------------------------------------*/
def sunCalc() {

	def lng = location.longitude
   	def lat = location.latitude

    state.lat = lat
    state.lng = lng
    state.julian = toJulian()
    
    def lw  = rad() * -lng
    state.lw = lw
    
    def phi = rad() * lat
    state.phi = phi
    
    def d   = toDays()
    state.d = d

    def c  = sunCoords(d)
    state.c = c
    log.trace "sunCoords c:" + c + " c.ra: " + c.ra + " c.dec: " + c.dec
    
    def H  = siderealTime(d, lw) - c.ra
    state.H = H
     
    def az = azimuth(H, phi, c.dec)
    az = (az*180/Math.PI)+180
    def al = altitude(H, phi, c.dec)
    al = al*180/Math.PI
    
    return [
        azimuth: az,
        altitude: al
    ]
}
/*-----------------------------------------------------------------------------------------*/
/*	Return the Julian date 
/*-----------------------------------------------------------------------------------------*/
def toJulian() { 
    def date = new Date()
    date = date.getTime() / dayMs() - 0.5 + J1970() // ms time/ms in a day = days - 0.5 + number of days 1970.... 
    return date   
}
/*-----------------------------------------------------------------------------------------*/
/*	Return the number of days since J2000
/*-----------------------------------------------------------------------------------------*/
def toDays(){ return toJulian() - J2000()}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun RA
/*-----------------------------------------------------------------------------------------*/
def rightAscension(l, b) { 
	return Math.atan2(Math.sin(l) * Math.cos(e()) - Math.tan(b) * Math.sin(e()), Math.cos(l)) 
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Declination
/*-----------------------------------------------------------------------------------------*/
def declination(l, b)    { return Math.asin(Math.sin(b) * Math.cos(e()) + Math.cos(b) * Math.sin(e()) * Math.sin(l)) } 
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Azimuth
/*-----------------------------------------------------------------------------------------*/
def azimuth(H, phi, dec)  { return Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi)) }
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Altitude
/*-----------------------------------------------------------------------------------------*/
def altitude(H, phi, dec) { return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H)) }
/*-----------------------------------------------------------------------------------------*/
/*	compute sidereal time (One sidereal day corresponds to the time taken for the Earth to rotate once with respect to the stars and lasts approximately 23 h 56 min.
/*-----------------------------------------------------------------------------------------*/
def siderealTime(d, lw) { return rad() * (280.16 + 360.9856235 * d) - lw }
/*-----------------------------------------------------------------------------------------*/
/*	Compute Sun Mean Anomaly
/*-----------------------------------------------------------------------------------------*/
def solarMeanAnomaly(d) { return rad() * (357.5291 + 0.98560028 * d) }
/*-----------------------------------------------------------------------------------------*/
/*	Compute Sun Ecliptic Longitude
/*-----------------------------------------------------------------------------------------*/
def eclipticLongitude(M) {

	def C = rad() * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M))
	def P = rad() * 102.9372 // perihelion of the Earth

    return M + C + P + Math.PI 
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Coordinates
/*-----------------------------------------------------------------------------------------*/
def sunCoords(d) {

    def M = solarMeanAnomaly(d)
    def L = eclipticLongitude(M)

	return [dec: declination(L, 0), ra: rightAscension(L, 0)]
}
/*-----------------------------------------------------------------------------------------*/
/*	Some auxilliary routines for readabulity in the code
/*-----------------------------------------------------------------------------------------*/
def dayMs() { return 1000 * 60 * 60 * 24 }
def J1970() { return 2440588}
def J2000() { return 2451545}
def rad() { return  Math.PI / 180}
def e() { return  rad() * 23.4397}

/*-----------------------------------------------------------------------------------------*/
/*	Netatmo interaction
/*-----------------------------------------------------------------------------------------*/
def getNetatmoCallback() {
	log.debug "getNetatmoCallback()>> params: $params, params.code ${params.code}"

	def code = params.code
	def oauthState = params.state

	if (oauthState == state.oauthInitState) {

		def tokenParams = [
			client_secret: getClientSecret(),
			client_id : getClientId(),
			grant_type: "authorization_code",
			redirect_uri: getCallbackUrl(),
			code: code,
			scope: "read_station"
		]

		// log.debug "TOKEN URL: ${getVendorTokenPath() + toQueryString(tokenParams)}"

		def tokenUrl = getVendorTokenPath()
		def params = [
			uri: tokenUrl,
			contentType: 'application/x-www-form-urlencoded',
			body: tokenParams
		]

		// log.debug "PARAMS: ${params}"

	try {
		httpPost(params) { resp ->

			def slurper = new JsonSlurper()

			resp.data.each { key, value ->
				def data = slurper.parseText(key)

				state.refreshToken = data.refresh_token
				state.authToken = data.access_token
				state.tokenExpires = now() + (data.expires_in * 1000)
				log.debug "swapped token: $resp.data"
			}
		}
        }
        catch (e) {
    			log.error e
        	}

		// Handle success and failure here, and render stuff accordingly
		if (state.authToken) {
			success()
		} else {
			fail()
		}

	} else {
		log.error "callback() failed oauthState != state.oauthInitState"
	}

}

// Example success method
def success() {
        def message = """
                <p>Your account is now connected to SmartThings!</p>
                <p>Click 'Done' to finish setup.</p>
        """
        displayMessageAsHtml(message)
}

// Example fail method
def fail() {
    def message = """
        <p>There was an error connecting your account with SmartThings</p>
        <p>Please try again.</p>
    """
    displayMessageAsHtml(message)
}

def displayMessageAsHtml(message) {
    def html = """
        <!DOCTYPE html>
        <html>
            <head>
            </head>
            <body>
                <div>
                    ${message}
                </div>
            </body>
        </html>
    """
    render contentType: 'text/html', data: html
}

def getNetatmoAuth() {
	TRACE("getNetatmoAuth")

    // Generate a random ID to use as a our state value. This value will be used to verify the response we get back from the 3rd party service.
	state.oauthInitState = UUID.randomUUID().toString()

	def oauthParams = [
		response_type: "code",
		client_id: getClientId(),
		client_secret: getClientSecret(),
		state: state.oauthInitState,
		redirect_uri: getCallbackUrl(),
		scope: "read_station"
	]

	// log.debug "REDIRECT URL: ${getVendorAuthPath() + toQueryString(oauthParams)}"

	redirect (location: getVendorAuthPath() + toQueryString(oauthParams))
}

// The toQueryString implementation simply gathers everything in the passed in map and converts them to a string joined with the "&" character.
String toQueryString(Map m) {
        return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def pageAuth() {
	TRACE("pageAuth")
    // Check to see if our SmartApp has it's own access token and create one if not.
    if(!state.accessToken) {
    	TRACE("pageAuth create Access Token")
        // the createAccessToken() method will store the access token in state.accessToken
        createAccessToken()
    }

    def redirectUrl = "https://graph.api.smartthings.com/oauth/initialize?appId=${app.id}&access_token=${state.accessToken}&apiServerUrl=${getApiServerUrl()}"
    // Check to see if we already have an access token from Netatmo.
    if(!state.authToken) {
    	TRACE("pageAuth get page out")
        return dynamicPage(name: "auth") {
            section() {
                paragraph "tap below to log in to Netatmo and authorize SmartThings access"
                href url: redirectUrl, style: "embedded", required: true, title: "Netatmo", description: "Click to enter credentials"
            }
        }
    } else {
        // We have the token, so we can just call NetAtmo to list PWS-es that are near to me and select PWS-es to install.
        TRACE("pageAuth state.authToken present, get stationdata")

		def stationUrl = getVendorStationPath()
		def params = [
			uri: "https://api.netatmo.com/api/getstationsdata",
			query: ["access_token": state.authToken, "get_favorites": true]
			]
            
        httpGet(params) { response ->
				log.debug response
            	}
    }
}

def refreshToken() {
	log.debug "In refreshToken"

	def oauthParams = [
		client_secret: getClientSecret(),
		client_id: getClientId(),
		grant_type: "refresh_token",
		refresh_token: state.refreshToken
	]

	def tokenUrl = getVendorTokenPath()
	def params = [
		uri: tokenUrl,
		contentType: 'application/x-www-form-urlencoded',
		body: oauthParams,
	]

	// OAuth Step 2: Request access token with our client Secret and OAuth "Code"
	try {
		httpPost(params) { response ->
			def slurper = new JsonSlurper();

			response.data.each {key, value ->
				def data = slurper.parseText(key);
				// log.debug "Data: $data"

				state.refreshToken = data.refresh_token
				state.accessToken = data.access_token
				state.tokenExpires = now() + (data.expires_in * 1000)
				return true
			}

		}
	} catch (Exception e) {
		log.debug "Error: $e"
	}

	// We didn't get an access token
	if ( !state.accessToken ) {
		return false
	}
}

def apiGet(String path, Map query, Closure callback) {
	if(now() >= state.tokenExpires) {
		refreshToken();
	}

	query['access_token'] = state.accessToken
	def params = [
		uri: getApiUrl(),
		path: path,
		'query': query
	]
	// log.debug "API Get: $params"

	try {
		httpGet(params)	{ response ->
			callback.call(response)
		}
	} catch (Exception e) {
		// This is most likely due to an invalid token. Try to refresh it and try again.
		log.debug "apiGet: Call failed $e"
		if(refreshToken()) {
			log.debug "apiGet: Trying again after refreshing token"
			httpGet(params)	{ response ->
				callback.call(response)
			}
		}
	}
}

def apiGet(String path, Closure callback) {
	apiGet(path, [:], callback);
}

private getApiUrl()				{ "https://api.netatmo.com" }
private getVendorName()			{ "netatmo" }
private getVendorAuthPath()		{ "${apiUrl}/oauth2/authorize?" }
private getVendorTokenPath()	{ "${apiUrl}/oauth2/token" }
private getVendorStationPath()	{ "${apiUrl}/getstationsdata" }
private getVendorIcon()			{ "https://s3.amazonaws.com/smartapp-icons/Partner/netamo-icon-1%402x.png" }
private getClientId()			{ appSettings.clientId }
private getClientSecret()		{ appSettings.clientSecret }
private getServerUrl() 			{ appSettings.serverUrl }
private getShardUrl()			{ return getApiServerUrl() }
private getCallbackUrl()		{ "${serverUrl}/oauth/callback" }
private getBuildRedirectUrl() 	{ "${serverUrl}/oauth/initialize?appId=${app.id}&access_token=${state.accessToken}&apiServerUrl=${shardUrl}" }