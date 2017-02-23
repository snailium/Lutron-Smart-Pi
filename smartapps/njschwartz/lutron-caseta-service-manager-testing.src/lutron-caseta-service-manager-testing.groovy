/**
 *  Lutron Caseta Service Manager 1
 *
 *  Copyright 2016 Nate Schwartz
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
import groovy.json.JsonSlurper

definition(
		name: "Lutron Caseta Service Manager Testing",
		namespace: "njschwartz",
		author: "Nate Schwartz",
		description: "This smartapp is used in conjunction with server code to provide an interface to a Lutron SmartBridge",
		category: "SmartThings Labs",
		iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
		iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
		iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    page(name:"mainPage", title:"Configuration", content:"mainPage")
    page(name:"piDiscovery", title:"Raspberry Pi Discover", content:"piDiscovery")
    page(name:"switchDiscovery", title:"Lutron Device Setup", content:"switchDiscovery")
    page(name:"sceneDiscovery", title:"Lutron Scene Setup", content:"sceneDiscovery")
}

def mainPage() {
	//Check to see if the Raspberry Pi already exists if not load pi discovery and if so load device discovery
	def rpi = getDevices()
	if (rpi) {
		return switchDiscovery()
	} else {
		return piDiscovery()
	}
}

//Preferences page to add raspberry pi devices
def piDiscovery() {
    def refreshInterval = 5
    if(!state.subscribe) {
        log.debug('Subscribing to updates')
        // subscribe to M-SEARCH answers from hub
        subscribe(location, null, ssdpHandler, [filterEvents:false])
        state.subscribe = true
    }
    // Perform M-SEARCH
    log.debug('Performing discovery')
    //sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:RPi_Lutron_Caseta:", physicalgraph.device.Protocol.LAN))
	ssdpDiscover()
    
    //Populate the preferences page with found devices
    def devicesForDialog = getDevicesForDialog()
    if (devicesForDialog != [:]) {
    	refreshInterval = 100
    }
    
    return dynamicPage(name:"piDiscovery", title:"RPi Discovery", nextPage:"switchDiscovery", refreshInterval: refreshInterval, uninstall: true) {
        section("Select your Raspberry Pi/Server") {
            input "selectedRPi", "enum", required:false, title:"Select Raspberry Pi \n(${devicesForDialog.size() ?: 0} found)", multiple:false, options:devicesForDialog
        } 
        section("Is this a Pro Hub?") {
        // name is "temperature1", type is "number"
        input "proHubBool", "bool", title: "ProHub"
    }
  }
}

void ssdpDiscover() {
	 sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:RPi_Lutron_Caseta:", physicalgraph.device.Protocol.LAN))
}

//Preferences page to add Lutron Caseta Devices
def switchDiscovery() {
    def refreshInterval = 5
    
	//Populate the preferences page with found devices
    def switchOptions = switchesDiscovered()
    def picoOptions = picosDiscovered()
    discoverLutronDevices()
    if (switchOptions != [:]) {
    	refreshInterval = 100
    }
    
    if (proHubBool) {
        return dynamicPage(name:"switchDiscovery", title:"switchDiscovery", nextPage:"sceneDiscovery", refreshInterval: refreshInterval, uninstall: true) {
            section("Switches") {
                input "selectedSwitches", "enum", required:false, title:"Select Switches \n(${switchOptions.size() ?: 0} found)", multiple:true, options:switchOptions
            }
            section("Pico's") {
                input "selectedPicos", "enum", required:false, title:"Select Pico's \n(${switchOptions.size() ?: 0} found)", multiple:true, options:picoOptions
            }
        }
    } else { 
    	return dynamicPage(name:"switchDiscovery", title:"switchDiscovery", nextPage:"sceneDiscovery", refreshInterval: refreshInterval, uninstall: true) {
            section("Switches") {
                input "selectedSwitches", "enum", required:false, title:"Select Switches \n(${switchOptions.size() ?: 0} found)", multiple:true, options:switchOptions
            }
        }
    }
}

def sceneDiscovery() {
    def refreshInterval = 5
    
	//Populate the preferences page with found devices
    def sceneOptions = scenesDiscovered()
    discoverScenes()
    if (sceneOptions != [:]) {
    	refreshInterval = 100
    }
    return dynamicPage(name:"sceneDiscovery", title:"sceneDiscovery", nextPage:"", refreshInterval: refreshInterval, install: true, uninstall: true) {
        section("") {
            input "selectedScenes", "enum", required:false, title:"Select Scenes \n(${sceneOptions.size() ?: 0} found)", multiple:true, options:sceneOptions
        }
    }
}

/* Callback when an M-SEARCH answer is received */
def ssdpHandler(evt) {
    if(evt.name == "ping") {
        return ""
    }
    
    def description = evt.description
    def hub = evt?.hubId
    def parsedEvent = parseDiscoveryMessage(description)
  
    parsedEvent << ["hub":hub]
    
    if (parsedEvent?.ssdpTerm?.contains("schemas-upnp-org:device:RPi_Lutron_Caseta:")) {
        def devices = getDevices()

        if (!(devices."${parsedEvent.mac}")) { //if it doesn't already exist
            devices << ["${parsedEvent.mac}":parsedEvent]
        } else { // just update the values
            def d = devices."${parsedEvent.mac}"
            boolean deviceChangedValues = false
            if(d.ip != parsedEvent.ip || d.port != parsedEvent.port) {
                d.ip = parsedEvent.ip
                d.port = parsedEvent.port
                deviceChangedValues = true
                def child = getChildDevice(parsedEvent.mac)
				if (child) {
					child.sync(parsedEvent.ip, parsedEvent.port)
                }
            }
        }
    }
}

//Creates a map to populate the switches pref page
Map switchesDiscovered() {
	def switches = getSwitches()
	def devicemap = [:]
	if (switches instanceof java.util.Map) {
		switches.each {
			def value = "${it.value.name}"
			def key = it.value.id
			devicemap["${key}"] = value
		}
	}
	return devicemap
}

//Creates a map to populate the picos pref page
Map picosDiscovered() {
	def picos = getPicos()
	def devicemap = [:]
	if (picos instanceof java.util.Map) {
		picos.each {
			def value = "${it.value.name}"
			def key = it.value.id
			devicemap["${key}"] = value
		}
	}
	return devicemap
}

//Creates a map to populate the scenes pref page
Map scenesDiscovered() {
	def scenes = getScenes()
	def devicemap = [:]
	if (scenes instanceof java.util.Map) {
		scenes.each {
			def value = "${it.value.name}"
			def key = it.value.id
			devicemap["${key}"] = value
		}
	}
	return devicemap
}


//Returns all found switches added to app.state
def getSwitches() {
	if (!state.switches) { 
    	state.switches = [:] 
    }
    state.switches
}

def getScenes() {
    if (!state.scenes) { 
    	state.scenes = [:] 
    }
    state.scenes
}

def getPicos() {
	if (!state.picos) { 
    	state.picos = [:] 
    }
    state.picos
}

//Request device list from raspberry pi device
private discoverLutronDevices() {
	log.debug "Discovering your Lutron Devices"
    def devices = getDevices()
    def ip
    def port
    devices.each {
    	ip = it.value.ip
        port = it.value.port
    }
   
   //Get swtiches and picos and add to state
	sendHubCommand(new physicalgraph.device.HubAction([
		method: "GET",
		path: "/status",
		headers: [
			HOST: ip + ":" + port
		]], "${selectedRPi}", [callback: lutronHandler]))
        

}

def discoverScenes() {
   
   log.debug "Discovering your Scenes"
   def devices = getDevices()
   def ip
   def port
   devices.each {
       ip = it.value.ip
       port = it.value.port
    }
   
   //Get scenes and add to state
   sendHubCommand(new physicalgraph.device.HubAction([
		method: "GET",
		path: "/scenes",
		headers: [
			HOST: ip + ":" + port
		]], "${selectedRPi}", [callback: sceneHandler]))   
}


def sceneHandler(physicalgraph.device.HubResponse hubResponse) {
	def body = hubResponse.json
    if (body != null) {
        def scenes = getScenes()
        def sceneList = body['Body']['VirtualButtons']
        
        sceneList.each { k ->
        	def virtButtonNum 
            if(k.IsProgrammed == true) {
            	virtButtonNum = k.href.substring(15)
            	scenes[k.href] = [id: k.href, name: k.Name, virtualButton: virtButtonNum, dni: "", hub: hubResponse.hubId]
            }
        }
    }
}

//Handle device list request response from raspberry pi
def lutronHandler(physicalgraph.device.HubResponse hubResponse) {
    def body = hubResponse.json
    if (body != null) {
        log.debug body
        def switches = getSwitches()
        log.debug "Adding switches to state!"
        def deviceList = body['Body']['Devices']
		
        deviceList.each { k ->
            def zone
            def device
            
            if(k.LocalZones && k.DeviceType == "WallDimmer") {
                zone = k.LocalZones[0].href.substring(6)
                log.debug zone
                switches[k.SerialNumber] = [id: k.SerialNumber, name: k.Name , zone: zone, dni: "", hub: hubResponse.hubId]
            } else if (k.DeviceType == "Pico3ButtonRaiseLower") {
            	device = k.href.substring(8)
            	picos[k.SerialNumber] = [id: k.SerialNumber, name: k.Name , device: device , dni: "", hub: hubResponse.hubId]
            }
        }
    }
}

/* Generate the list of devices for the preferences dialog */
def getDevicesForDialog() {
    def devices = getDevices()
    def map = [:]
    devices.each {
        def value = convertHexToIP(it.value.ip) + ':' + convertHexToInt(it.value.port)
        //def key = it.value.ssdpUSN.toString()
        def key = it.value.mac
        map["${key}"] = value
    }
    map
}

/* Get map containing discovered devices. Maps USN to parsed event. */
def getDevices() {
    if (!state.devices) { state.devices = [:] }
    log.debug("There are ${state.devices.size()} found")
    state.devices
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"


	unsubscribe()
	initialize()
}

def initialize() {
	unschedule()
    unsubscribe()
    
    log.debug ('Initializing')
    
    def selectedDevices = selectedRPi
    if (selectedSwitches != null) {
    	selectedDevices += selectedSwitches 
    }
    if (selectedPicos != null) {
    	selectedDevices += selectedPicos 
    }
    if (selectedScenes != null) {
    	selectedDevices += selectedScenes 
    }
    
    log.debug "All selected devices are: " + selectedDevices
    
    def deleteDevices = (selectedDevices) ? (getChildDevices().findAll { !selectedDevices.contains(it.deviceNetworkId) }) : getAllChildDevices()
    //log.debug "selected switches are: " + settings.selectedSwitches
    //log.debug "child devices are: " + getChildDevices()
    log.debug "DEvices to delete are: " + deleteDevices
    deleteDevices.each { deleteChildDevice(it.deviceNetworkId) } 

    //If a raspberry pi was actually selected add the child device pi and child device switches
    if (selectedRPi) {
    	addBridge()
        addSwitches()
        addPicos()
        addScenes() 
    }
   
      
    
    runEvery5Minutes("ssdpDiscover")
}

def addBridge() {
    /*
    //for each of the raspberry pi's selected add as a child device
	selectedRPi.each { ssdpUSN ->
    	
        // Make the dni the MAC followed by the index from the USN
 		def dni = devices[ssdpUSN].mac + ':' + ssdpUSN.split(':').last()
        if (ssdpUSN.endsWith(":1")) {
            dni = devices[ssdpUSN].mac
        }
*/
		log.debug "the selected rpi is " + selectedRPi
		//selectedRPi.each { mac ->
		def dni = selectedRPi
        // Check if child already exists
        def d = getAllChildDevices()?.find {
            it.device.deviceNetworkId == dni
        }

        //Add the Raspberry Pi
        if (!d) {
            def ip = devices[selectedRPi].ip
            def port = devices[selectedRPi].port
            log.debug("Adding ${dni} for ${selectedRPi} / ${ip}:${port}")
            d = addChildDevice("njschwartz", "Raspberry Pi Lutron Caseta", dni, devices[selectedRPi].hub, [
                "label": "PI/Caseta at: " + convertHexToIP(ip) + ':' + convertHexToInt(port),
                "data": [
                    "ip": ip,
                    "port": port,
                    "ssdpUSN": selectedRPi,
                    "ssdpPath": devices[selectedRPi].ssdpPath
                ]
            ])
            d.sendEvent(name: "networkAddress", value: "${ip}:${port}")
        }
        
}

def addSwitches() {

    
	selectedSwitches.each { id ->
    	def allSwitches = getSwitches()
        def name = allSwitches[id].name
        def zone = allSwitches[id].zone
  
        // Make the dni the appId + the Lutron device serial number
 		//def dni = app.id + "/" + id
        def dni = id
        //add the dni to the switch state variable for future lookup
        allSwitches[id].dni = dni

        // Check if child already exists
        def d = getAllChildDevices()?.find {
            it.device.deviceNetworkId == dni
        }
		def hubId = switches[id].hub


        if (!d) {
            log.debug("Adding ${dni} for ${id}")
            d = addChildDevice("njschwartz", "Lutron Virtual Dimmer", dni, hubId, [
                "label": "${name}",
                "data": [
                	"dni": "${dni}",
                    "zone": "${zone}" 
                ]
            ])
        }
        log.debug "child devices are: " + getChildDevices()
        
        //Call refresh on the new device to set the initial state
        d.refresh()
    }
}

def addPicos() {
	def allPicos = getPicos()
	selectedPicos.each { id ->
    	
        def name = allPicos[id].name
        def device = allPicos[id].device
  
        // Make the dni the appId + the Lutron device serial number
 		def dni = app.id + "/" + id
        
        //add the dni to the switch state variable for future lookup
        allPicos[id].dni = dni

        // Check if child already exists
        def d = getAllChildDevices()?.find {
            it.device.deviceNetworkId == dni
        }
		def hubId = picos[id].hub


        if (!d) {
            log.debug("Adding ${dni} for ${id}")
            //d = addChildDevice("njschwartz", "Lutron Pico", dni, hubId, [
            d = addChildDevice("stephack", "Lutron Pico", dni, hubId, [
                "label": "${name}",
                "data": [
                	"dni": dni,
                    "device": device 
                ]
            ])
        }
        
        //Call refresh on the new device to set the initial state
        d.refresh()
    }
}

def addScenes() {
	def allScenes = getScenes()
    

	selectedScenes.each { id ->
    	
        log.debug allScenes
        def name = allScenes[id].name
        def virtButton = allScenes[id].virtualButton
  
        // Make the dni the appId + virtubutton + the Lutron device virtual button number
 		def dni = app.id + "/virtualbutton" + id
        
        //add the dni to the switch state variable for future lookup
        allScenes[id].dni = dni

        // Check if child already exists
        def d = getAllChildDevices()?.find {
            it.device.deviceNetworkId == dni
        }
		def hubId = scenes[id].hub


        if (!d) {
            log.debug("Adding ${dni} for ${id}")
            d = addChildDevice("njschwartz", "Lutron Scene", dni, hubId, [
                "label": "${name}",
                "data": [
                	"dni": dni,
                    "virtualButton": virtButton 
                ]
            ])
        }
        
        //Call refresh on the new device to set the initial state
        d.refresh()
    }
}

////////////////////////////////////////////////////////////////////////////
//						CHILD DEVICE FUNCTIONS							 //
///////////////////////////////////////////////////////////////////////////

//Parse the data from raspberry pi. This is called by the Raspberry Pi device type parse method because that device recieves all the updates sent from the pi back to the hub
def parse(description) {
	log.debug description
    def dni
    def children = getAllChildDevices()
    
    if(description['Body']['Device']) {
    	log.debug "Telnet Response"
        def action = description['Body']['Action'].trim()
        log.debug "Action is " + action
        if (action == 4 || action == "4") {
        	log.debug "action was 4"
            return ""
        }
        def button = description['Body']['Button']
        def device = description['Body']['Device']
        log.debug "Device name is: " + device
        
        children.each { child ->
        	if (child.getDataValue("device".toString()) == device) {
        		dni = child.getDataValue("dni".toString())
     		}	
        }
        log.debug dni
        if (dni != Null) {
        	log.debug dni
       	    sendEvent(dni, [name: "button", value: "pushed", data: [buttonNumber: button], descriptionText: "button $button was pushed", isStateChange: true])
        }
        
        return ""
    }
    
    if(description['Body']['Command'])
    	return ""
        
    //Get the zone and light level from the recieved message
     def zone = description['Body']['ZoneStatus']['Zone'].href.substring(6)
     def level = description['Body']['ZoneStatus'].Level
    

    
    //Match the zone to a child device and grab its DNI in order to send event to appropriate device
     children.each { child ->
        if (child.getDataValue("zone".toString()) == zone) {
        	dni = child.getDataValue("dni".toString())
     	}
    }
    
	if (level > 0) { 
    	sendEvent(dni, [name: "switch", value: "on"])
        sendEvent(dni, [name: "level", value: level])
    } else {
    	sendEvent(dni, [name: "switch", value: "off"])
        //sendEvent(dni, [name: "level", value: level])
    }
         
/* Example of the response coming from Lutron Caseta through pi device type and to this method
[Body:[ZoneStatus:[Level:100, Zone:[href:/zone/5]]], Header:[StatusCode:200 OK, MessageBodyType:OneZoneStatus, Url:/zone/5/status/level], CommuniqueType:ReadResponse]      		
*/

}

//Send request to turn light on (on assumes level of 100)
def on(childDevice) {

    def switches = getSwitches()
    
    def split = childDevice.device.deviceNetworkId.split("/")
    put("/on", switches[split[1]].zone, '100')
}

//Send refresh request to pi to get current status
def refresh(childDevice) {

    def switches = getSwitches()
    //def split = childDevice.device.deviceNetworkId.split("/")
    //put("/status", switches[split[1]].zone, "")
    put("/status", switches[childDevice.device.deviceNetworkId].zone, "")
}

//Send request to turn light off (level 0)
def off(childDevice) {
	log.debug childDevice.device.label
    def switches = getSwitches()
    //def split = childDevice.device.deviceNetworkId.split("/")
    //put("/off", switches[split[1]].zone, '0')
    put("/off", switches[childDevice.device.deviceNetworkId].zone, '0')
}

//Send request to set device to a specific level
def setLevel(childDevice, level) {
    log.debug childDevice.data
    def switches = getSwitches()
    //def split = childDevice.device.deviceNetworkId.split("/")
    //put("/setLevel", switches[split[1]].zone, level)
    put("/setLevel", switches[childDevice.device.deviceNetworkId].zone, level)
}

def runScene(childDevice) {
	def scenes = getScenes()
    def buttonNum = childDevice.device.deviceNetworkId.split("/")[3]
    put("/scene", buttonNum)
}

//Function to send the request to pi
private put(path, body, level = "") {
	
    def devices = getDevices()
    def ip
    def port
    devices.each {
    	ip = it.value.ip
        port = it.value.port
    }
    def hostHex = ip + ":" + port
	def content
    log.debug hostHex
    //If no level then this is just a refresh request
    if (level != "") {
    	content = body + ":" + level
    } else {
    	content = body
    }
    
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: path,
        body: content,
        headers: [
            HOST: hostHex
        ]
    )

	sendHubCommand(result)
}



private String makeNetworkId(ipaddr, port) { 
     String hexIp = ipaddr.tokenize('.').collect { 
     String.format('%02X', it.toInteger()) 
     }.join() 
     String hexPort = String.format('%04X', port.toInteger()) 
     log.debug "${hexIp}:${hexPort}" 
     return "${hexIp}:${hexPort}" 
}

def subscribeToDevices() {
    log.debug "subscribeToDevices() called"
    def devices = getAllChildDevices()
    devices.each { d ->
        //log.debug('Call subscribe on '+d.id)
        d.subscribe()
    }
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private def parseDiscoveryMessage(String description) {
    def device = [:]
    def parts = description.split(',')
    parts.each { part ->
        part = part.trim()
        if (part.startsWith('devicetype:')) {
            def valueString = part.split(":")[1].trim()
            device.devicetype = valueString
        } else if (part.startsWith('mac:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                device.mac = valueString
            }
        } else if (part.startsWith('networkAddress:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                device.ip = valueString
            }
        } else if (part.startsWith('deviceAddress:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                device.port = valueString
            }
        } else if (part.startsWith('ssdpPath:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                device.ssdpPath = valueString
            }
        } else if (part.startsWith('ssdpUSN:')) {
            part -= "ssdpUSN:"
            def valueString = part.trim()
            if (valueString) {
                device.ssdpUSN = valueString
            }
        } else if (part.startsWith('ssdpTerm:')) {
            part -= "ssdpTerm:"
            def valueString = part.trim()
            if (valueString) {
                device.ssdpTerm = valueString
            }
        } else if (part.startsWith('headers')) {
            part -= "headers:"
            def valueString = part.trim()
            if (valueString) {
                device.headers = valueString
            }
        } else if (part.startsWith('body')) {
            part -= "body:"
            def valueString = part.trim()
            if (valueString) {
                device.body = valueString
            }
        }
    }

    device
}
