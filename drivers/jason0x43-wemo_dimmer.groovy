/**
 * WeMo Dimmer driver
 *
 * Author: Jason Cheatham
 * Last updated: 2019-06-09, 22:48:33-0400
 *
 * Based on the original Wemo Switch driver by Juan Risso at SmartThings,
 * 2015-10-11.
 *
 * Copyright 2015 SmartThings
 *
 * Dimmer-specific information is from kris2k's wemo-dimmer-light-switch
 * driver:
 *
 * https://github.com/kris2k2/SmartThingsPublic/blob/master/devicetypes/kris2k2/wemo-dimmer-light-switch.src/wemo-dimmer-light-switch.groovy
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

metadata {
    definition(
        name: 'Wemo Dimmer',
        namespace: 'jason0x43',
        author: 'Jason Cheatham'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Switch Level'
        capability 'Polling'
        capability 'Refresh'
        capability 'Sensor'

        command 'subscribe'
        command 'unsubscribe'
        command 'resubscribe'
    }
}

def getDriverVersion() {
    2
}

def on() {
    log.info('Turning on')
    parent.childSetBinaryState(device, 1)
}

def off() {
    log.info('Turning off')
    parent.childSetBinaryState(device, 0)
}

def setLevel(value) {
    log.info("Setting level to $value")
    def binaryState = value > 0 ? 1 : 0;
    parent.childSetBinaryState(device, binaryState, value)
}

def parse(description) {
    debugLog('parse: received message')

    // A message was received, so the device isn't offline
    unschedule('setOffline')

    def msg = parseLanMessage(description)
    parent.childUpdateSubscription(msg, device)

    def result = []
    def bodyString = msg.body
    if (bodyString) {
        def body = new XmlSlurper().parseText(bodyString)

        if (body?.property?.TimeSyncRequest?.text()) {
            debugLog('parse: Got TimeSyncRequest')
            result << syncTime()
        } else if (body?.Body?.SetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.SetBinaryStateResponse.BinaryState.text()
            debugLog("parse: Got SetBinaryStateResponse = ${rawValue}")
            result << createBinaryStateEvent(rawValue)
        } else if (body?.property?.BinaryState?.text()) {
            def rawValue = body.property.BinaryState.text()
            debugLog("parse: Notify: BinaryState = ${rawValue}")
            result << createBinaryStateEvent(rawValue)

            if (body.property.brightness?.text()) {
                rawValue = body.property.brightness?.text()
                debugLog("parse: Notify: brightness = ${rawValue}")
                result << createLevelEvent(rawValue)
            }
        } else if (body?.property?.LongPress?.text()) {
            def rawValue = body.property.LongPress.text()
            debugLog("parse: Notify: LongPress = ${rawValue}")
            result << createLongPressEvent(rawValue)
            }
        } else if (body?.property?.TimeZoneNotification?.text()) {
            debugLog("parse: Notify: TimeZoneNotification = ${body.property.TimeZoneNotification.text()}")
        } else if (body?.Body?.GetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.GetBinaryStateResponse.BinaryState.text()
            debugLog("parse: GetBinaryResponse: BinaryState = ${rawValue}")
            result << createBinaryStateEvent(rawValue)

            if (body.Body.GetBinaryStateResponse.brightness?.text()) {
                rawValue = body.Body.GetBinaryStateResponse.brightness?.text()
                debugLog("parse: GetBinaryResponse: brightness = ${rawValue}")
                result << createLevelEvent(rawValue)
            }
        }
    }

    result
}

def poll() {
    log.info('Polling')

    // Schedule a call to flag the device offline if no new message is received
    if (device.currentValue('switch') != 'offline') {
        runIn(10, setOffline)
    }

    parent.childGetBinaryState(device)
}

def refresh() {
    log.info('Refreshing')
    [
        resubscribe(),
        syncTime(),
        poll()
    ]
}

def resubscribe() {
    log.info('Resubscribing')

    // Schedule a subscribe check that will run after the resubscription should
    // have completed
    runIn(10, subscribeIfNecessary)

    parent.childResubscribe(device)
}

def setOffline() {
    sendEvent(
        name: 'switch',
        value: 'offline',
        descriptionText: 'The device is offline'
    )
}

def subscribe() {
    log.info('Subscribing')
    parent.childSubscribe(device)
}

def subscribeIfNecessary() {
    parent.childSubscribeIfNecessary(device)
}

def unsubscribe() {
    log.info('Unsubscribing')
    parent.childUnsubscribe(device)
}

def updated() {
    log.info('Updated')
    refresh()
}

private createBinaryStateEvent(rawValue) {
    def value = rawValue == '0' ? 'off' : 'on'
    createEvent(
        name: 'switch',
        value: value,
        descriptionText: "Switch is ${value}"
    )
}
private createLongPressEvent(rawValue) {
    def value = rawValue 
    if (value == '1') {
        createEvent(
        name: 'momentary',
        descriptionText: "Long press occured"
    )
    }
}
private createLevelEvent(rawValue) {
    def value = "$rawValue".toInteger()
    createEvent(
        name: 'level',
        value: value,
        descriptionText: "Level is ${value}"
    )
}

private debugLog(message) {
    if (parent.debugLogging) {
        log.debug(message)
    }
}

private syncTime() {
    parent.childSyncTime(device)
}
