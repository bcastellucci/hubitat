
# This package contains drivers for Powerley devices.

### Powerley makes energy monitoring and control services & solutions for utility companies.

### They market a ZWave & Zigbee hub they call an Energy Bridge, they also market a ZWave thermostat and an app (available for both Android and Apple) to control said devices.

* The thermostat can be controlled by Hubitat using the generic ZWave thermostat driver but there is no way to change any of its settings, not even manually at the thermostat itself, so the thermostat driver in this package allows the device to be properly configured from the Hubitat device page (settings such as HVAC equipment configuration, swing setting, tempurature offset, display brightness, etc.).

* The energy monitor driver emulates a device with both "Power Monitoring" and "Energy Monitoring" capabilities using the MQTT broker on the local LAN hosted by the Energy Bridge, specifically its 'event/metering/instantaneous_demand' and 'event/metering/summation/minute' endpoints, respectively.

* Note - the Powerley Energy Bridge needs to be on the same local LAN as the Hubitat and it needs to be served a static IP from your router (easiest way is to tie it to its MAC address), then there's a configuration setting on the device page to enter that IP.

* Both of the MQTT broker endpoints offer POWER usage information, not energy, per-se. The instantaneous_demand endpoint is updated every three seconds with the current WATTS being used, the summation/minute endpoint is an average of those three-second reports over one minute, which some may loosely consider a measure of 'energy' although usually energy is kWh, which this is NOT. Nonetheless the summation/minute reporting is exposed as the 'energy' attribute for the 'EnergyMeter' capability and the instantaneous_demand reporting is exposed as the 'power' attribute for the 'PowerMeter' capability.

* As mentioned in the threads noted below, sometimes the instantaneous_demand stops reporting. There is a topic that the Powerley app publishes to when it is opened on a user's device, is_app_open, and that 'kicks' the process and gets it reporting again. I have personally not needed to do this as mine seems to continually report no matter what, but others have experienced it stop after about 5 minutes & need to publish to that topic to get it reporting again. Just to cover all bases this driver will do that when the 'powerStart' command is ran. If this needs done periodically then a Rules Machine rule could be set up to monitor the 'powerState' attribute and, if it goes to 'stopped' then run the 'powerStart' command to start it back up.

The need for these drivers came about recently, specifically for AEP Ohio customers, as AEP lost regulatory approval to continue the services after November 30, 2020.

Not wanting to buy new Z-Wave thermostats and also not wanting to buy a separate energy monitor when there's already a perfectly working smart meter on the side of the house, I wrote these drivers after a lot of research into existing community [thermostat drivers](https://github.com/djdizzyd/hubitat/blob/master/Drivers/Honeywell/Advanced-Honeywell-T6-Pro.groovy) & [MQTT drivers](https://github.com/mydevbox/hubitat-mqtt-link/tree/master/drivers/hubitat-mqtt-link-driver.groovy), forum threads [here](https://github.com/home-assistant/core/issues/20170) and [here](https://www.reddit.com/r/homeassistant/comments/j7ykh6/for_any_aep_ohio_customers_with_the_powerley/) and on the [documentation](https://opensmarthouse.org/zwavedatabase/1149/PowerleyThermostat-Assoc-Parameter.pdf) for the Powerley thermostat itself.

## Many, many thanks to all those who I've linked to above - without their hard work I would not have been able to write these drivers and continue using these devices.

Drivers come with NO WARRANTY whatsoever and are Apache 2.0 licensed.

Do with as you please & enjoy!
