/**
 *  Smart Screens
 *
 *  Copyright 2018 Martin Verbeek
 
 	App is using forecast.io to get conditions for your location, you need to get an APIKEY
  	from developer.forecast.io and your longitude and latitude is being pulled from the location object
 	position of the sun is calculated in this app, thanks to the formaula´s of Mourner at suncalc github
 
 	Select the blinds you want to configure (it will use commands Open/Stop/Close) so they need to be on the device.
   	Select the position they are facing (North-East-South-West) or multiple...these are the positions that they will need protection from wind or sun
 	WindForce protection Select condition to Close or to Open (Shutters you may want to close when lots of wind, sunscreens you may want to Open
   	cloudCover percentage is the condition to Close (Sun is shining into your room)
   	Select interval to check conditions
  
 
 	V2.00	Add subscription to device refresh, this will execute the latest info back to the devices
 	V3.00	Move to windowShade capability, and issue createchild command to create related component device for smart screens
    V3.10	Add off season definition, change pause setting to pause switch, pause switch prevails off season
    V3.11	Bug in check for wind , send event to non custom dth (domoticzBlinds)
    V3.12	Bug in windcheck, should have replaced dev by it
    V3.13	Add inHouse Screen type, move EOD processing to app from dth
    V4.00	Add outside/inside temperature as an option for screencontrol, also control when Airco is turned on in Cool mode
    		For windspeed/bearing it will look in the next forecasted hour as well to see if bad wether is coming, a setting will
            enable to pick the highest speeds as actor
	V4.01	bug in cool processing, state.devices was reset in EOD processing
    V4.02	Reset SOD a little after midnight
    V4.03 	nulls during clean setup fixed
    V4.04	Small Pause in Operateblinds, when muliple screen it might skip some commands
    V4.05	Bug in SOD execution, was skipped for non screen types
    V4.06	ability to start SoD before sunrise
    V4.07	Proper offset to sunrise, direct events to app, not to device 
 
*/

import groovy.json.*
import java.Math.*
import java.time.LocalDateTime.*
import Calendar.*
import groovy.time.*


private def runningVersion() 	{"4.06"}

definition(
    name: "Smart Screens",
    namespace: "verbem",
    author: "Martin Verbeek",
    description: "Automate Up and Down of Sun Screens, Blinds and Shutters based on Weather Conditions",
    category: "Convenience",
    oauth: true,
    iconUrl: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/RollerShutter.png",
    iconX2Url: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/RollerShutter.png",
    iconX3Url: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/RollerShutter.png")

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
}

	def pageSetupForecastIO() {
    TRACE("pageSetupForecastIO()")
	if (!state.country) getCountry() 
    
    def dni = "SmartScreens Pause Switch"
    def dev = getChildDevice(dni)
    if (!dev) dev = addChildDevice("verbem", "domoticzOnOff", dni, getHubID(), [name:dni, label:dni, completedSetup: true])
    pause 5

    def pageSetupLatitude = location.latitude.toString()
    def pageSetupLongitude = location.longitude.toString()
	
    def pageSetupAPI = [
        name:       "pageSetupAPI",
        type:       "string",
        title:      "API key(darksky), key(WU) or APPID(OWM)",
        multiple:   false,
        required:   true
    	]
   
   	def pageProperties = [
        name:       "pageSetupForecastIO",
        //title:      "Status",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]
    
    def inputEnableTemp = [
        name:       "z_EnableTemp",
        type:       "bool",
        default:	false,
        title:      "",
        multiple:   false,
		submitOnChange: true,
		required:   true
    ]
    
    def inputEnableNextWindSpeed = [
        name:       "z_EnableNextWindSpeed",
        type:       "bool",
        default:	false,
        title:      "",
        multiple:   false,
		submitOnChange: false,
		required:   true
    ]

    
    def inputDayStart28 = [name: "z_inputDayStart", type: "number", title: "Start day", range: "1..28", required:true]
    def inputDayStart30 = [name: "z_inputDayStart", type: "number", title: "Start day", range: "1..30", required:true]
    def inputDayStart31 = [name: "z_inputDayStart", type: "number", title: "Start day", range: "1..31", required:true]
    def inputMonthStart = [name: "z_inputMonthStart", type: "enum", title: "Start month", options: [1:"January", 2:"February", 3:"March", 4:"April", 5:"May", 6:"June", 7:"July", 8:"August", 9:"September", 10:"October", 11:"November", 12:"December" ], required:false, submitOnChange: true]
    
    def inputDayEnd28 = [name: "z_inputDayEnd", type: "number", title: "End Day", range: "1..28", required:true]
    def inputDayEnd30 = [name: "z_inputDayEnd", type: "number", title: "End Day", range: "1..30", required:true]
    def inputDayEnd31 = [name: "z_inputDayEnd", type: "number", title: "End Day", range: "1..31", required:true]
	def inputMonthEnd = [name: "z_inputMonthEnd", type: "enum", title: "End month", options: [1:"January", 2:"February", 3:"March", 4:"April", 5:"May", 6:"June", 7:"July", 8:"August", 9:"September", 10:"October", 11:"November", 12:"December" ], required:true, submitOnChange: true]    
    
    def inputSensors = [
        name:       "z_sensors",
        type:       "capability.sensor",
        title:      "Which NETATMO wind devices?",
        multiple:   true,
        required:   false
    ] 
    
    return dynamicPage(pageProperties) {		
        section("Darksky.net, WeatherUndergound or OpenWeatherMap API Key and Website") {
        
			input "z_weatherAPI", "enum", options:["Darksky", "OpenWeatherMap", "WeatherUnderground", "WeatherUnderground-NoPWS"], title: "Select Weather API",multiple:false, submitOnChange: true, required:true       

            if (z_weatherAPI) {

                if (z_weatherAPI == "Darksky") {
                	paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/DarkSky.png", "DarkSky Interface"
                    input pageSetupAPI

                    href(name: "hrefNotRequired",
                         title: "Darksky.net page",
                         required: false,
                         style: "external",
                         url: "https://darksky.net/dev/",
                         description: "tap to view Darksky website in mobile browser")
                }

                if (z_weatherAPI == "WeatherUnderground" || z_weatherAPI == "WeatherUnderground-NoPWS") {
                	paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/WeatherUnderGround.png", "Weather Underground Interface"
	                input pageSetupAPI

					href(name: "hrefNotRequired",
                         title: "WeatherUnderground page",
                         required: false,
                         style: "external",
                         url: "https://www.wunderground.com/weather/api/d/pricing.html",
                         description: "tap to view WU website in mobile browser")
                }

                if (z_weatherAPI == "OpenWeatherMap") {
                	paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/OpenWeatherMap.png", "Open Weather Map Interface"
	                input pageSetupAPI

					href(name: "hrefNotRequired",
                         title: "OpenWeatherMap page",
                         required: false,
                         style: "external",
                         url: "https://home.openweathermap.org/users/sign_in",
                         description: "tap to view OWM website in mobile browser")
                }
            }            
            paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/LotsOfWind.png", 
            	"Use next hour forecasted windspeed in combination with current speed \nThe highest speed is used"
            	input inputEnableNextWindSpeed
            
	        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Netatmo.png", "Netatmo Interface"
	            input inputSensors
        }
                   
        section("Temperature Control", hideable:true, hidden:true) {
            
            paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Temperature.png", "Enable Temperature Protection (keep it Cool)"
			input inputEnableTemp
            if (settings.z_EnableTemp) {
            	input	"z_defaultExtTemp","number",title:"Act above Outside temperature", required:true
            	input	"z_defaultIntTemp","number",title:"Act above Inside temperature (default)", required:true
            	input	"z_defaultintTempLogic","enum",title:"Act on Inside AND/OR Outside (default)\nAND - both must be higher if Inside is present\nOR - One must be higher",
                	required:true, options:["AND", "OR"]
            	input	"z_defaultTempCloud","number",title:"Cloud cover needs to be equal or below % for action", required:true, options:["10","20","30","40","50","60","70","80","90","100"], multiple:false               
            }
        }
        section("Shades Control", hideable:true) {
                        
			paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/RollerShutter.png", "Select Window Shades"
            input "z_blinds", "capability.windowShade", multiple:true, submitOnChange: true, required:false
            
			paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Settings.png", "Configure Window Shades"
            if (settings.z_blinds) z_blinds.each {href "pageConfigureBlinds", title:"${it.name}", description:"", params: it}
        }   
        
        section("Off Season between below dates", hideable:true, hidden:true) {           
            input inputMonthStart
            if (z_inputMonthStart) {
                if (z_inputMonthStart.toString() == "2") input inputDayStart28
                else if (z_inputMonthStart.toString().matches("1|3|5|7|8|10|12")) input inputDayStart31
                else input inputDayStart30

                input inputMonthEnd
                if (z_inputMonthEnd.toString() == "2") input inputDayEnd28
                else if (z_inputMonthEnd.toString().matches("1|3|5|7|8|10|12")) input inputDayEnd31
                else input inputDayEnd30
           	}
        }
        section("Info Page") {
            href "pageForecastIO", title:"Environment Info", description:"Tap to open"
        }
        section("Options", hideable:true, hidden:true) {
            label title:"Assign a name", required:false
            input "z_TRACE", "bool", default: false, title: "Put out trace log", multiple: false, required: true
            input "z_PauseSwitch", "capability.switch", default:false, title:"Switch that Pauses all scheduling", multiple: false, required:false

        }    
    }
}

/*-----------------------------------------------------------------------*/
//	 Show Configure Blinds Page
/*-----------------------------------------------------------------------*/
def pageConfigureBlinds(dev) {
	
    if (dev?.name != null) state.devName = dev.name
    
    TRACE("pageConfigureBlinds() ${state.devName}")
    
    def pageProperties = [
            name:       "pageConfigureBlinds",
            title:      "Configure for ${state.devName}",
            nextPage:   "pageSetupForecastIO",
            uninstall:  false
        ]

    return dynamicPage(pageProperties) {
        z_blinds.each {
            if (it.name == state.devName) {
                def devId = it.id
                def devType = it.typeName
                def blindOptions = ["Down", "Up"]

                if (it.hasCommand("presetPosition")) blindOptions.add("Preset")
                if (it.hasCommand("stop")) blindOptions.add("Stop")
                def completionTime
                if (it.completionTimeState?.value) completionTime = Date.parseToStringDate(it.completionTimeState.value).format('ss').toInteger()
                if (it.typeName == "domoticzBlinds" && it.hasAttribute("completionTime")) {
                	if (completionTime > 0) {
                        blindOptions.add("Down 25%")
                        blindOptions.add("Down 50%")
                        blindOptions.add("Down 75%")
                    }
				}
                
                def blind = it.currentValue("somfySupported")
                if (blind == 'true') {blind = true}
                    else {blind = false}

                section(it.name) {
                    paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Settings.png", "General Settings"
                    input	"z_blindType_${devId}", "enum", options:["InHouse Screen","Screen","Shutter"], title:"In-house Screen, (sun)Screen or (roller)Shutter", required:true, multiple:false, submitOnChange:true
                    input 	"z_blindsOrientation_${devId}", "enum", options:["N", "NW", "W", "SW", "S", "SE", "E", "NE"],title:"Select Orientation",multiple:true,required:true
                    input 	"z_blindsTest_${devId}", "bool", title: "Test operations, only events are sent", required:true, default:false
                    
                    paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Door.png", "Is there a related Door/Window"
                    input "z_blindsOpenSensor_${devId}", "capability.contactSensor", required:false, multiple:false, title:"No operation when contact is open"

                    if (settings."z_blindType_${devId}" == "InHouse Screen") {
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sun.png", "Sun Protection"
                        input	"z_closeMaxAction_${devId}","enum",title:"Action to take", options: blindOptions, required:false
                        input 	"z_cloudCover_${devId}","enum",title:"Protect until what cloudcover% (0=clear sky)", options:["10","20","30","40","50","60","70","80","90","100"],multiple:false,required:false,default:30                
                    }
                    
                    if (settings."z_blindType_${devId}" == "Screen") {
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sun.png", "Sun Protection"
                        input	"z_closeMaxAction_${devId}","enum",title:"Action to take", options: blindOptions, required:false
                        input 	"z_cloudCover_${devId}","enum",title:"Protect until what cloudcover% (0=clear sky)", options:["10","20","30","40","50","60","70","80","90","100"],multiple:false,required:false,default:30                

                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/NotSoMuchWind.png", "Sun Protection Only Below Windforce"
                        input	"z_windForceCloseMax_${devId}","number",title:"Below Windspeed ${state.unitWind}",multiple:false,required:false,default:0                 
                    } 
                    
                    if (settings."z_blindType_${devId}" == "Shutter") {
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sun.png", "Sun Protection"
                        input	"z_closeMaxAction_${devId}","enum",title:"Action to take", options: blindOptions, required:false
                        input 	"z_cloudCover_${devId}","enum",title:"Protect until what cloudcover% (0=clear sky)", options:["10","20","30","40","50","60","70","80","90","100"],multiple:false,required:false,default:30

                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/LotsOfWind.png", "Wind Protection"
                        input	"z_closeMinAction_${devId}","enum",title:"Action to take", options: blindOptions, required:false
                        input 	"z_windForceCloseMin_${devId}","number",title:"Above windspeed ${state.unitWind}",multiple:false,required:false,default:999                     
                    }
                    
                    if (settings.z_EnableTemp) {
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Airco.png", "When Airco starts cooling"                        
                        input	"z_blindsThermostatMode_${devId}", "capability.thermostatMode", required:false, submitOnChange:true, multiple:false, title:"Select Thermostat with Cool mode"
                        
                        if (settings."z_blindsThermostatMode_${devId}") {
                        	input	"z_thermoStatmodeAction_${devId}","enum",title:"Action to take when cooling", options: blindOptions, required:true
                        }
                        
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Temperature.png", "Keep it Cool ($location.temperatureScale)"                        
                                              
                        input	"z_extTempAction_${devId}","enum",title:"Action to take when outside temp above ${settings.z_defaultExtTemp}", options: blindOptions, required:false                     	                       
                        input 	"z_intTempLogic_${devId}", "enum", required:false, multiple:false, title:"Above inside AND/OR outside temperature", options:["AND","OR"], default: settings.z_defaultintTempLogic, submitOnChange:true

                        if (settings."z_intTempLogic_${devId}") {
                        	input	"z_intTemp_${devId}","number",title:"Above inside temperature", required:false, submitOnChange:true
                            if (settings."z_intTemp_${devId}") {
                                input 	"z_blindsTempSensor_${devId}", "capability.temperatureMeasurement", required:true, multiple:false, title:"Select Inside Temperature Sensor"
                            }
                        }
                    }

					paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sunset.png", "End of Day operation"
                    input	"z_eodAction_${devId}","enum",title:"EOD action", options: blindOptions, required:false
                    input	"z_sunsetOffset_${devId}","number",title:"Sunset +/- offset", multiple:false, required:false, default:0
                    
					paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sunrise.png", "Start of Day operation"
                    input	"z_sunriseTime_${devId}","time",title:"Start Time of operations", multiple:false, required:false
                    input	"z_sunriseOffset_${devId}","number",title:"Sunrise +/- offset (max 100)", multiple:false, required:false, range: "-100..100" 
                    input	"z_sodAction_${devId}","enum",title:"Start of day action", options: blindOptions, required:false
                    
                }
            }
        }
    }
}

/*-----------------------------------------------------------------------*/
// Show Sun/Wind ForecastIO API last output Page 2018-06-22 5:20:44.006 AM CEST
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
        	paragraph "Next hour forecasted windspeed can be used ${settings.z_EnableNextWindSpeed}"
        	paragraph "Speed ${state.windSpeed} from direction ${state.windBearing}" 
            settings.z_sensors.each {
            	if (it.currentValue("WindAngle") && it.currentValue("WindStrength")) paragraph "${it.displayName} speed ${it.currentValue("WindStrength")} from direction ${calcBearing(it.currentValue("WindAngle"))}"
                else paragraph "Invalid data from ${it}"
            }
		}
        
        section("Sun") {
            paragraph "cloud Cover ${state.cloudCover} Sun in direction ${state.sunBearing}"
		}
        
 		if (settings.z_EnableTemp) {       
            section("Temperature") {
            	def blindParams = [:]
                paragraph "Outside Temperature reported ${state.extTemp}"
                settings.z_blinds.each { blind ->
                	blindParams = fillBlindParams(blind.id)
                    if (blindParams.extTempAction) {
                    	if (blindParams.intTempLogic) {
                    		paragraph "${blindParams.blindDev} ${blindParams.extTempAction} above outside ${settings.z_defaultExtTemp}, ${blindParams.intTempLogic} inside temp ${blindParams.devTemp} is above ${blindParams.intTemp}"
                  		}          
                        else {
                    		paragraph "${blindParams.blindDev} ${blindParams.extTempAction} above outside ${settings.z_defaultExtTemp}"
                    	}
                    }
                }
            }
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
	initialize()
	TRACE("Installed with settings: ${settings}")
}

def updated() {
	unsubscribe()
	initialize()
    //resetSOD()
	TRACE("Updated with settings: ${settings}")
}

def initialize() {
    state.sunBearing = ""
    state.windBearing = ""
    state.night = false
    state.windSpeed = 0
    state.cloudCover = 100
    state.netatmo = false
	if (!state.country) getCountry() 
        
	subscribe(location, "sunset", stopSunpath,  [filterEvents:true])
    subscribe(location, "sunrise", startSunpath,  [filterEvents:true])

    def offset
    def sunriseString = location.currentValue("sunriseTime")
    def sunriseTime
    def timeBeforeSunset
    
    offset = 120 * 60 * 1000
    sunriseTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sunriseString)
    timeBeforeSunset = new Date(sunriseTime.time - offset)
    
    schedule(timeBeforeSunset, notifyNewVersion)
  
    subscribe(z_sensors, "WindStrength", eventNetatmo)
    
    settings.z_blinds.each {
    	subscribeToCommand(it, "refresh", eventRefresh)
        }
    
    settings.each { k, v ->
    	if (k.contains("z_blindsOpenSensor")) {
        	subscribe(v, "contact.Closed", eventDoorClosed) 
        }
        if (k.contains("z_blindsThermostatMode")) {
        	subscribe(v, "thermostatMode", eventThermostatMode)
        }
    }

    scheduleEOD()
    
    checkForWind()
    checkForSun()
    checkForClouds()
    
    runEvery30Minutes(checkForSun)
    runEvery1Hour(checkForClouds)
    runEvery10Minutes(checkForWind)
    
    if (z_PauseSwitch) subscribe(z_PauseSwitch, "switch", pauseHandler)
    state.pause = offSeason()
}

private def getCountry() {
    def httpGetCountry = [
        uri: "https://maps.googleapis.com",
        path: "/maps/api/geocode/json",
        contentType: "application/json", 
        query: ["latlng" : "${location.latitude},${location.longitude}", "key" : "AIzaSyCvNfXMaFmrTlIwIqILm7reh_9P-Sx3x2I"]
    ]

	try {
        httpGet(httpGetCountry) { response ->
        	response.data.results.address_components.each {
            	if (it.types[0].contains("country")) state.country = it.short_name[0]
            }
    	}
    }
    catch (e)  {
        log.error "googleApis $e"
    }
    
    if (state.country) {
    	if (state.country.matches("GB|US|LR|MM")) {
        	state.units = "imperial"
        	state.unitWind = "mph"
        }
        else {
        	state.units = "metric"
            state.unitWind = "km/h"
       	}
    }
}

def eventDoorClosed(evt) {
	TRACE("[eventDoorClosed] ${evt.device} has closed") 
 	
    if (state.night == false) checkForSun()
}

def eventThermostatMode(evt) {  
    TRACE("[eventThermostatMode] ${evt.device} ${evt.value} mode event")
    def blindParams = [:]
    def blindID
    
    settings.findAll {it.key.contains("z_blindsThermostatMode_")}.each {
    	if (it.value.toString() == evt.device.toString()) {
        	blindID = it.key.split("z_blindsThermostatMode_")[1]
        	blindParams = fillBlindParams(blindID)
            if (evt.value == "cool") {
                if (blindParams.blindsThermostatMode && blindParams.thermoStatmodeAction) {
                    TRACE("[eventThermostatMode] ${evt.device} ${evt.value} action ${blindParams.thermoStatmodeAction} for event ${blindParams.blindsThermostatMode}")
                    operateBlind([requestor: "Airco", device:blindParams.blindDev, action: blindParams.thermoStatmodeAction, reverse:false])
                    state.devices[blindID].cool = true
                }
            }
            else state.devices[blindID].cool = false
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*	This is an event handler that will be provided with wind events from NETATMO devices
/*-----------------------------------------------------------------------------------------*/
def eventNetatmo(evt) {
	TRACE("[eventNetatmo]")

    def dev = evt.getDevice()
    def windAngle 		= calcBearing(dev.latestValue("WindAngle"))
    def gustStrength 	= dev.latestValue("GustStrength")
    def gustAngle 		= calcBearing(dev.latestValue("GustAngle"))
    def windStrength 	= dev.latestValue("WindStrength")
    def units 			= dev.latestValue("units")

    if (evt.isStateChange()) {
        state.windBearing = windAngle
        state.windSpeed = windStrength
        state.netatmo = true
        checkForWind("NETATMO")
    }
}

def pauseHandler(evt) {

	if (evt.value == "on" && state.pause == false) {
    	state.pause = true
		TRACE("[pauseHandler] ${evt.device} ${evt.value} state.pause -> ${state.pause}") 
    }
    if (evt.value == "off" && state.pause == true) {
    	state.pause = false
		TRACE("[pauseHandler] ${evt.device} ${evt.value} state.pause -> ${state.pause}") 
    }
}
/*-----------------------------------------------------------------------------------------*/
/*	This routine will get information relating to the weather at location and current time
/*-----------------------------------------------------------------------------------------*/
def getForecast() {

	def windBearing
    def windSpeed
    def cloudCover
    def returnList = [:]
    
    TRACE("[getForecast] ${settings.z_weatherAPI} for Lon:${location.longitude} Lat:${location.latitude}")
	
	if (settings.z_weatherAPI == "Darksky") {
    	def units = "auto"
		def httpGetParams = [
            uri: "https://api.darksky.net",
            path: "/forecast/${settings.pageSetupAPI}/${location.latitude},${location.longitude}",
            contentType: "application/json", 
            query: ["units" : units, "exclude" : "minutely,daily,flags"]
        ]
        try {
            httpGet(httpGetParams) { response ->
                returnList.put('windBearing' ,calcBearing(response.data.currently.windBearing))
                returnList.put('windSpeed', Math.round(response.data.currently.windSpeed.toDouble()))
                returnList.put('cloudCover', response.data.currently.cloudCover.toDouble() * 100)
                returnList.put('temperature', response.data.currently.temperature.toDouble())
                returnList.put('nextWindBearing' ,calcBearing(response.data.hourly.data[1].windBearing))
                returnList.put('nextWindSpeed', Math.round(response.data.hourly.data[1].windSpeed.toDouble()))
                }
            } 
            catch (e) {
                log.error "DARKSKY something went wrong: $e"
				returnList = [:]
            }
	}
    
	if (settings.z_weatherAPI == "OpenWeatherMap") {

        def httpGetParams = "http://api.openweathermap.org/data/2.5/weather?lat=${location.latitude}&lon=${location.longitude}&APPID=${settings.pageSetupAPI}&units=${state.units}"
        def httpGetHourly = "http://api.openweathermap.org/data/2.5/forecast?lat=${location.latitude}&lon=${location.longitude}&APPID=${settings.pageSetupAPI}&units=${state.units}"
        try {
            httpGet(httpGetParams) { resp ->
                returnList.put('windBearing',calcBearing(resp.data.wind.deg))
                returnList.put('windSpeed', Math.round(resp.data.wind.speed.toDouble()))
                returnList.put('cloudCover', Math.round(resp.data.clouds.all.toDouble()))
                returnList.put('temperature', resp.data.main.temp)
            	}
            httpGet(httpGetHourly) { resp ->
                returnList.put('nextWindSpeed', Math.round(resp.data.list[0].wind.speed.toDouble()))
                returnList.put('nextWindBearing', calcBearing(Math.round(resp.data.list[0].wind.deg.toInteger())))
            }
        } 
        catch (e) {
            log.error "OWM something went wrong: $e"
            returnList = [:]
        }
    }

	if (settings.z_weatherAPI.contains("WeatherUnderground")) {
		def httpGetParams = "http://api.wunderground.com/api/${settings.pageSetupAPI}/conditions/pws:0/q/${location.latitude},${location.longitude}.json"
        def httpGetHourly = "http://api.wunderground.com/api/${settings.pageSetupAPI}/hourly/pws:0/q/${location.latitude},${location.longitude}.json"

		if (settings.z_weatherAPI.contains("NoPWS") == false) {
            httpGetParams = "http://api.wunderground.com/api/${settings.pageSetupAPI}/conditions/pws:1/q/${location.latitude},${location.longitude}.json"
            httpGetHourly = "http://api.wunderground.com/api/${settings.pageSetupAPI}/hourly/pws:0/q/${location.latitude},${location.longitude}.json"
        	TRACE("[getForecast] Use PWS is true ${httpGetParams}")
            }
        else {
        	TRACE("[getForecast] Use PWS is false ${httpGetParams}")
            }
            
		try {
        	TRACE("[getForecast] Get current conditions")
            httpGet(httpGetParams) { resp ->
                returnList.put('windBearing',calcBearing(resp.data.current_observation.wind_degrees))
                returnList.put('windSpeed', resp.data.current_observation.wind_kph.toDouble())  //all others do m/s if metric, account for this.
                def CC = 100
                switch (resp.data.current_observation.weather) {
                case ["Clear"]:
                    CC = 0
                    break;
                case "Scattered Clouds":
                    CC = 30
                    break;
                case "Partly Cloudy":
                    CC = 50
                    break;
                case ["Mostly Cloudy", "Overcast"]:
                    CC = 80
                    break;
                default:
                    CC = 100
                    break
                	}
                returnList.put('cloudCover', CC.toDouble())
                
                if (location.temperatureScale == "C") returnList.put('temperature', resp.data.current_observation.temp_c.toDouble())
                else returnList.put('temperature', resp.data.current_observation.temp_f.toDouble())
            	}
            TRACE("[getForecast] Get hourly conditions")
            httpGet(httpGetHourly) { resp ->
            	if (resp.data.hourly_forecast.size() > 0) if (state.units == "metric") returnList.put('nextWindSpeed', resp.data.hourly_forecast[0].wspd.metric.toDouble()) else returnList.put('nextWindSpeed', resp.data.hourly_forecast[0].wspd.english.toDouble())
            	if (resp.data.hourly_forecast.size() > 0) returnList.put('nextWindBearing', calcBearing(resp.data.hourly_forecast[0].wdir.degrees))
          	}
        } 
        catch (e) {
                log.error "WU something went wrong: $e"
                log.error "WU ${httpGetParams}"
                log.error "WU ${httpGetHourly}"
				log.error returnList
        } 
    }
	TRACE("[getForecast] ${settings.z_weatherAPI} ${returnList}")
	return returnList
}

/*-----------------------------------------------------------------------------------------*/
/*	This routine will get information relating to the SUN´s position
/*-----------------------------------------------------------------------------------------*/
def getSunpath() {
    TRACE("[getSunpath]")
    def sp = sunCalc()
    return calcBearing(sp.azimuth)  
}

/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get latest SUN related info on position
/*	and will check the blinds that provide sun protection if they need to be closed or opened
/*	Also if temperature control is enabled it will check this first
/*-----------------------------------------------------------------------------------------*/
def checkForSun(evt) {
    TRACE("[checkForSun]")

    settings.z_blinds.each {
        def blindParams = fillBlindParams(it.id)
   
        if (!blindParams.cool && state.sunBearing.matches(blindParams.blindsOrientation)) {
			if (actionTemperature(blindParams) == true) {
                    if (blindParams.blindsType == "Screen") {                    
                        if((state.windSpeed.toInteger() < blindParams.windForceCloseMax && state.windBearing.matches(blindParams.blindsOrientation)) || state.windBearing.matches(blindParams.blindsOrientation) == false ) {
                            operateBlind([requestor: "Temperature from Sun", device:it, action: blindParams.extTempAction, reverse:false])
                        }
                    }
                    if (blindParams.blindsType == "Shutter" || blindParams.blindsType == "inHouse Screen") {
                        operateBlind([requestor: "Temperature from Sun", device:it, action: blindParams.extTempAction, reverse:false])
                    }
            }
            else {
                TRACE("[checkForSun] ${it} Forecast is ${state.cloudCover.toInteger()}% cloud, definition on shade is ${blindParams.cloudCover}%")
                if(state.cloudCover.toInteger() <= blindParams.cloudCover) 
                {                      
                    TRACE("[checkForSun] ${blindParams.blindsType} ${it} Forecasted ${state.windSpeed.toInteger()} < ${blindParams.windForceCloseMax}, wind orientation ${state.windBearing.matches(blindParams.blindsOrientation)}")
                    if (blindParams.blindsType == "Screen") {                    
                        if((state.windSpeed.toInteger() < blindParams.windForceCloseMax && state.windBearing.matches(blindParams.blindsOrientation)) || state.windBearing.matches(blindParams.blindsOrientation) == false ) {
                            operateBlind([requestor: "Sun", device:it, action: blindParams.closeMaxAction, reverse:false])
                            if (!blindParams.firstSunAction) state.devices[it.id].firstSunAction = true
                        }
                    }
                    if (blindParams.blindsType == "Shutter" || blindParams.blindsType == "inHouse Screen") {
                        operateBlind([requestor: "Sun", device:it, action: blindParams.closeMaxAction, reverse:false])
                        if (!blindParams.firstSunAction) state.devices[it.id].firstSunAction = true
                    }
                }
        	}
        }
        // reverse action when Sun not on Window
        if (!blindParams.cool && !state.sunBearing.matches(blindParams.blindsOrientation) && blindParams.firstSunAction ) {
        	TRACE("[checkForSun] Sun not on window reverse action ${blindParams.blindDev}")
            if (blindParams.blindsType == "Screen") {                    
                if((state.windSpeed.toInteger() < blindParams.windForceCloseMax && state.windBearing.matches(blindParams.blindsOrientation)) || state.windBearing.matches(blindParams.blindsOrientation) == false ) {
                    operateBlind([requestor: "Sun", device:it, action: blindParams.closeMaxAction, reverse:true])
                }
            }
            if (blindParams.blindsType == "Shutter" || blindParams.blindsType == "inHouse Screen") {
                operateBlind([requestor: "Sun", device:it, action: blindParams.closeMaxAction, reverse:true])
            }
        }
    }
    return null
}

/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get on cloud coverage
/*	and will check the blinds that provide sun protection if they need to be closed or opened
/*	Also if temperature control is enabled it will check this first
/*-----------------------------------------------------------------------------------------*/
def checkForClouds() {
    TRACE("[checkForClouds] ${params}")

    settings.z_blinds.each {
        def blindParams = fillBlindParams(it.id)
        if (!blindParams.cool && state.sunBearing.matches(blindParams.blindsOrientation)) {
            if (actionTemperature(blindParams) == true) {
                operateBlind([requestor: "Temperature from Clouds", device:it, action: blindParams.extTempAction, reverse:false])
            }
            else if(state.cloudCover.toInteger() > blindParams.cloudCover) {
                operateBlind([requestor: "Clouds", device:it, action: blindParams.closeMaxAction, reverse:true]) 
            }
    	}
    }
	return null
}
def test(evt) {

}
/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get latest WIND related info on position
/*	and will check the blinds if they need to be closed or can be opened
/*-----------------------------------------------------------------------------------------*/
def checkForWind(evt) {
    
    state.sunBearing = getSunpath()
    def windParms = [:]

    if (evt == null) {evt = settings.z_weatherAPI}
        
    windParms = getForecast()
    if (windParms == null) windParms = getForecast() //one retry
    
    if (windParms != null) {
        if (!evt || (evt && evt != "NETATMO" && state?.netatmo == false)) {
            state.windBearing = windParms.windBearing
            state.windSpeed = windParms.windSpeed
            if (settings.z_EnableNextWindSpeed && windParms.nextWindSpeed > windParms.windSpeed) {
                TRACE("[checkForWind] next hour wind info is used!")
                state.windBearing = windParms.nextWindBearing
                state.windSpeed = windParms.nextWindSpeed
            }
        }
		if (state.units == "metric" && settings.z_weatherAPI.contains("WeatherUnderground") == false) {state.windSpeed = windParms.windSpeed * 3.6}
        state.cloudCover = windParms.cloudCover
        state.extTemp = windParms.temperature
    }

    if (state.netatmo) TRACE("[checkForWind] Netatmo data is used!")
    
    settings.z_blinds.each { dev ->
    	if (dev.typeName == "domoticzBlinds") {
            sendEvent(dev, [name:"windBearing", value:state.windBearing])
            sendEvent(dev, [name:"windSpeed", value:state.windSpeed])
            sendEvent(dev, [name:"cloudCover", value:state.cloudCover])
            sendEvent(dev, [name:"sunBearing", value:state.sunBearing])
        }
    }
        
    if (state.pause) return

    TRACE("[checkForWind]")
    def sunriseString = location.currentValue("sunriseTime")
    def offset
    def sunriseTime
    def sunriseMinutes
    Date thisDate = new Date()
    def thisTime = thisDate.format("HH:mm", location.timeZone)
    def thisMinutes = thisTime.split(":")[0].toInteger()*60 + thisTime.split(":")[1].toInteger()
    
	settings.z_blinds.each {
        def blindParams = fillBlindParams(it.id)
        /*-----------------------------------------------------------------------------------------*/
        /* Look for Start of Day times if defined and start performing actions only after that time has passed
        /* This is just a convenient place as it gets done every 10 minutes.
        /* if an offset to sunrise exists it will prevail above sunriseTime
        /*-----------------------------------------------------------------------------------------*/
        if (blindParams?.sunriseOffset != null && blindParams.sodDone == false) {
            offset = blindParams.sunriseOffset * 60 * 1000
            sunriseTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sunriseString)
            blindParams.sunriseTime = new Date(sunriseTime.time + offset) 
        }
        
        if (blindParams.sunriseTime != null) {
            sunriseTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", blindParams.sunriseTime).format("HH:mm", location.timeZone)
            sunriseMinutes = sunriseTime.split(":")[0].toInteger()*60 + sunriseTime.split(":")[1].toInteger()
    		if (thisMinutes >= sunriseMinutes && blindParams.sodDone == false) {
            	TRACE("[checkForWind] Perform actions, reset sodDone for ${it}, check wind for Screen types")
                if(state.windSpeed.toInteger() <= blindParams.windForceCloseMax && blindParams.blindsType == "Screen") {
                    if (blindParams.sodAction) operateBlind([requestor: "SOD", device:it, action: blindParams.sodAction, reverse:false, t:thisMinutes, s:sunriseMinutes])
                } 
                else if (blindParams.sodAction) operateBlind([requestor: "SOD", device:it, action: blindParams.sodAction, reverse:false, t:thisMinutes, s:sunriseMinutes])
                state.devices[it.id].sodDone = true
        	}
        }
        else if (state.devices[it.id].sodDone == false) state.devices[it.id].sodDone = true
        /*-----------------------------------------------------------------------------------------*/
        /*	WIND determine if we need to close (or OPEN if wind speed is above allowed max for blind)
        /*-----------------------------------------------------------------------------------------*/ 
        if (state.windBearing) {
            if(state.windBearing.matches(blindParams.blindsOrientation)) {   
                if(state.windSpeed.toInteger() > blindParams.windForceCloseMin && (blindParams.blindsType == "Shutter" || blindParams.blindsType == "inHouse Screen")) {
                    operateBlind([requestor: "Wind", device:it, action: blindParams.closeMinAction, reverse:false])
                }
                if(state.windSpeed.toInteger() > blindParams.windForceCloseMax && blindParams.blindsType == "Screen") {
                    //reverse the defined MaxAction
                    operateBlind([requestor: "Wind", device:it, action: blindParams.closeMaxAction, reverse:true])
                }
            }
        }
        else TRACE("[checkForWind] Invalid windBearing in State ${windParms}")
    }
 	   
    return null
}

def eventRefresh(evt) {

	if (state.night == true) {
        checkForWind()
    	return
    }
    
    TRACE("[eventRefresh] ${evt.device} Source ${evt.source}")
    checkForClouds()
    checkForSun()
    checkForWind()
}

/*-----------------------------------------------------------------------------------------*/
/*	this will stop the scheduling of events called at SUNSET
/*-----------------------------------------------------------------------------------------*/
def stopSunpath(evt) {
	TRACE("[stopSunpath] Stop Scheduling")
    state.night = true
    pause 5
	unschedule(checkForSun)
    unschedule(checkForClouds)
    //unschedule(checkForWind)
    unsubscribe(eventRefresh)    
	return null
}

/*-----------------------------------------------------------------------------------------*/
/*	this will start the scheduling of events called at SUNRISE
/*-----------------------------------------------------------------------------------------*/
def startSunpath(evt) {
	TRACE("[startSunpath] Start Scheduling")
    state.night = false
    state.pause = offSeason()
    pause 5
	scheduleEOD() 

    //runEvery10Minutes(checkForWind)
    runIn(60, startScheduling)
    
    z_blinds.each {
    	if (it.hasCommand("refresh")) subscribeToCommand(it, "refresh", eventRefresh)
        }
           
	return null
}

def startScheduling() {
    runEvery3Hours(checkForClouds)    
    runEvery30Minutes(checkForSun)
}

private def operateBlind(blind) {
	TRACE("[operateBlind] : ${blind}")
    //protect
    def blindParams = fillBlindParams(blind.device.id)

    if (blind.action == null) {
    	TRACE("[operateBlind] no action defined ${blind}")
        return
    }

    if (state.pause && blind.requestor != "Wind") {
    	TRACE("[operateBlind] PAUSE has been set ${blindParams.openContact} for ${blindParams.blindDev}, no action")
        return
    }

	if (blindParams.openContact != "Closed") {
    	TRACE("[operateBlind] door/window ${blindParams.openContact} for ${blindParams.blindDev}, no action")
        return
    }
    
	if (blindParams.sunriseTime != null && blindParams.sodDone == false && blind.requestor != "SOD") {
    	TRACE("[operateBlind] StartOfDay time not passed yet for ${blindParams.blindDev}, no action")
        return
    }
    
	if (blindParams.eodDone == true) {
    	TRACE("[operateBlind] EndofDay time passed for ${blindParams.blindDev}, no action")
        return
    }
    
	if (!blindParams.test) {        
        if (blind.reverse == true) {
            if (blind.action == "Down") blind.device.open()
            if (blind.action == "Up") blind.device.close()
            if (blind.action == "Preset") blind.device.open()
            if (blind.action == "Stop") blind.device.open()
            if (blind.action == "Down 25%") blind.device.open()
            if (blind.action == "Down 50%") blind.device.open()
            if (blind.action == "Down 75%") blind.device.open()
       }
        else {
            if (blind.action == "Down") blind.device.close()
            if (blind.action == "Up") blind.device.open()
            if (blind.action == "Preset") blind.device.presetPosition()
            if (blind.action == "Stop") blind.device.stop()
            if (blind.action == "Down 25%") blind.device.setLevel(25)
            if (blind.action == "Down 50%") blind.device.setLevel(50)
            if (blind.action == "Down 75%") blind.device.setLevel(75)
        }
    	sendEvent([name: "operateBlind", value: blind])
    }
	else sendEvent([name: "TEST operateBlind", value: blind])
}

private def actionTemperature(blindParams) {
	def rc = false
    
    if (settings.z_EnableTemp != true || blindParams.extTempAction == null) return false
    if (!state.cloudCover || state.cloudCover.toInteger() > settings.z_defaultTempCloud.toInteger()) return false
    
    if (!blindParams.intTempLogic) {    	
    	if (state.extTemp.toInteger() > settings?.z_defaultExtTemp.toInteger()) rc = true
    }
	else {    
        if (blindParams.intTempLogic == "OR") {    	
            if (state.extTemp.toInteger() > settings?.z_defaultExtTemp.toInteger()) rc = true
            if (blindParams.devTemp && blindParams.intTemp && blindParams.devTemp > blindParams.intTemp) rc = true
        }
        if (blindParams.intTempLogic == "AND") {
            if (state.extTemp.toInteger() > settings?.z_defaultExtTemp.toInteger()) rc = true
            if (rc == true && blindParams.devTemp && blindParams.intTemp && blindParams.devTemp > blindParams.intTemp) rc = true else rc = false
        }
    }    
	return rc
}

private def fillBlindParams(findID) {
	def blindParams = [:]
    //blindParams.blindsOrientation 	= settings?."z_blindsOrientation_${findID}"
    if (settings?."z_blindsOrientation_${findID}" == null) blindParams.blindsOrientation = "NA|NA"
    else blindParams.blindsOrientation = settings?."z_blindsOrientation_${findID}".join('|').replaceAll('\"','')
    blindParams.windForceCloseMax 	= settings?."z_windForceCloseMax_${findID}"
    if (blindParams.windForceCloseMax) blindParams.windForceCloseMax = blindParams.windForceCloseMax.toInteger() else blindParams.windForceCloseMax = -1
    blindParams.windForceCloseMin 	= settings?."z_windForceCloseMin_${findID}"
    if (blindParams.windForceCloseMin) blindParams.windForceCloseMin = blindParams.windForceCloseMin.toInteger() else blindParams.windForceCloseMin = 999
    blindParams.cloudCover 			= settings?."z_cloudCover_${findID}"
    if (blindParams.cloudCover) 	blindParams.cloudCover = blindParams.cloudCover.toInteger() else blindParams.cloudCover = -1
    blindParams.closeMinAction 		= settings?."z_closeMinAction_${findID}"    
    blindParams.closeMaxAction 		= settings?."z_closeMaxAction_${findID}"    
    blindParams.blindsType 			= settings?."z_blindType_${findID}"        
	blindParams.blindsOpenSensor 	= settings?."z_blindsOpenSensor_${findID}"
    blindParams.openContact			= "Closed"
	blindParams.sunsetOffset 		= settings?."z_sunsetOffset_${findID}"
    blindParams.eodAction 			= settings?."z_eodAction_${findID}"
    blindParams.sunriseTime			= settings?."z_sunriseOffset_${findID}"
   	blindParams.sunriseTime			= settings?."z_sunriseTime_${findID}"
    blindParams.sodAction 			= settings?."z_sodAction_${findID}"
    blindParams.eodDone 			= state.devices[findID]?.eodDone
    blindParams.sodDone 			= state.devices[findID]?.sodDone
    if (blindParams.sodDone == null) blindParams.sodDone = true
    blindParams.firstSunAction		= state.devices[findID]?.firstSunAction
    if (blindParams.firstSunAction == null) blindParams.firstSunAction = false
    blindParams.firstWindAction		= state.devices[findID]?.firstWindAction
    if (blindParams.firstWindAction == null) blindParams.firstWindAction = false
    blindParams.firstTempAction		= state.devices[findID]?.firstTempAction
    if (blindParams.firstTempAction == null) blindParams.firstTempAction = false
    blindParams.test	 			= settings?."z_blindsTest_${findID}" ?: false
	blindParams.cool				= state.devices[findID]?.cool ?: false 
	blindParams.intTemp 			= settings?."z_intTemp_${findID}"
    blindParams.extTempAction 		= settings?."z_extTempAction_${findID}"
    blindParams.intTempSensor 		= settings?."z_blindsTempSensor_${findID}"
    blindParams.devTemp				= null
    blindParams.intTempLogic 		= settings?."z_intTempLogic_${findID}"
    blindParams.blindsThermostatMode = settings?."z_blindsThermostatMode_${findID}"
    blindParams.thermoStatmodeAction = settings?."z_thermoStatmodeAction_${findID}"
    blindParams.blindDev 			= settings.z_blinds.find {it.id == findID}
   	blindParams.currentValue		= blindParams.blindDev.currentValue("windowShade")
    
    if (settings.z_EnableTemp == true) blindParams.actOnTemp = state.devices[findID]?.actOnTemp ?: false else blindParams.actOnTemp = false
    if (blindParams.intTempSensor) blindParams.devTemp = blindParams.intTempSensor.currentValue("temperature").toDouble().round(0).toInteger()
    if (blindParams.blindsOpenSensor) blindParams.openContact = blindParams.blindsOpenSensor.currentValue("contact") ?: "Closed"
	if (!blindParams.intTemp) blindParams.intTemp = settings?.z_defaultIntTemp ?: 200 else blindParams.intTemp = blindParams.intTemp.toInteger()
    
	return blindParams
}

private def resetSOD() {
	settings.z_blinds.each { blind ->
    	if (state.devices[blind.id].sodDone) state.devices[blind.id].sodDone = false
    }
}

def scheduleEOD() {
    if (!state.devices) state.devices = [:]   
	def blindParams = [:]
    def offset
    def sunsetString = location.currentValue("sunsetTime")
    def eodAction
    def sunsetTime
    def timeBeforeSunset
      
    settings.z_blinds.each { blind ->
    	blindParams = fillBlindParams(blind.id)
       
        if (blindParams.sunsetOffset == "") offset = 0
        else offset = settings."z_sunsetOffset_${blind.id}"

        if (offset == "" || offset == null) {offset = 0}
        offset = offset * 60 * 1000
        sunsetTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sunsetString)
        timeBeforeSunset = new Date(sunsetTime.time + offset)
        runOnce(timeBeforeSunset, eodProcessing, [data: [blindId : blind.id], overwrite:false])
        if (!state.devices[blind.id]) { 
        	state.devices[blind.id] = [:]
            state.devices[blind.id].eodDone = false
            state.devices[blind.id].blindName = blindParams.blindDev.displayName
        }
        else if (!state.night) state.devices[blind.id].eodDone = false
    }	   
}

def eodProcessing(data) {
	def blindParams = [:]
    
	settings.z_blinds.each { blind ->
    	if (blind.id == data.blindId) {
            blindParams = fillBlindParams(blind.id)
            if (!blindParams.cool) operateBlind([requestor: "EOD", device:blind, action: blindParams.eodAction, reverse:false])
       	}
    }
    
	state.devices[data.blindId].eodDone = true 
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

private def offSeason() {
    def pauseReturn = false
    def date = new Date()
    def M = date[Calendar.MONTH]+1
    def D = date[Calendar.DATE]
    def Y = date[Calendar.YEAR]
    def YS = Y
    def YE = Y
    def dS
    def dE
    def justNow = "${D}-${M}-${Y}"    
    def df = "dd-MM-yyyy"
    
    if (settings.z_inputMonthStart) {
        
        if (settings.z_inputMonthEnd.toInteger() < settings.z_inputMonthStart.toInteger() && M.toInteger() >= settings.z_inputMonthStart.toInteger()) YE = Y+1
        if (settings.z_inputMonthEnd.toInteger() < settings.z_inputMonthStart.toInteger() && M.toInteger() <= settings.z_inputMonthEnd.toInteger()) YS = Y-1
        if (settings.z_inputMonthEnd.toInteger() < settings.z_inputMonthStart.toInteger() && M.toInteger() > settings.z_inputMonthEnd.toInteger() && M.toInteger() < settings.z_inputMonthStart.toInteger()) YE = Y+1
		
        dS = "${settings.z_inputDayStart}-${settings.z_inputMonthStart}-${YS}"
        dE = "${settings.z_inputDayEnd}-${settings.z_inputMonthEnd}-${YE}"
        def dateTimeS = new Date().parse(df, dS)
        def dateTimeE = new Date().parse(df, dE)
        def dateTimeN = new Date().parse(df, justNow)
        if (dateTimeN >= dateTimeS && dateTimeN <= dateTimeE) {
        	log.trace "Off SEASON, set pause switch device ON"
            def dev = getChildDevice("Smart Screen Pause Switch")
            if (dev) dev.sendEvent(name: "switch", value: "on")
            pauseReturn = true
        }
    }
    
    return pauseReturn
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

private def getHubID(){
    TRACE("[getHubID]")
    def hubID
    def hubs = location.hubs.findAll{ it.type == physicalgraph.device.HubType.PHYSICAL } 
    if (hubs.size() == 1) hubID = hubs[0].id 
    return hubID
}

/*-----------------------------------------------------------------------------------------*/
/*	Version Control
/*-----------------------------------------------------------------------------------------*/
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

def notifyNewVersion() {

	if (appVerInfo().split()[1] != runningVersion()) {
    	sendNotificationEvent("Hue Sensor App has a newer version, ${appVerInfo().split()[1]}, please visit IDE to update app/devices")
    }
    
    state.devices.each {
        state.devices[it.key].sodDone = false
        state.devices[it.key].firstSunAction = false
        state.devices[it.key].firstWindAction = false
        state.devices[it.key].firstTempAction = false
        pause 2
    }
}

private def appVerInfo()		{ return getWebData([uri: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/SmartScreensData", contentType: "text/plain; charset=UTF-8"], "changelog") }