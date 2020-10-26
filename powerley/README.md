
This package contains drivers for Powerley devices.

Powerley makes energy monitoring and control services & solutions for utility companies.

They market a ZWave & Zigbee hub they call an Energy Bridge, they also market a ZWave thermostat and an app (available for both Android and Apple) to control said devices.

The thermostat can be controlled by Hubitat using the generic ZWave thermostat driver but there is no way to change any of its settings, not even manually at the thermostat itself, so the thermostat driver in this package allows the device to be properly configured from the Hubitat device page (settings such as HVAC equipment configuration, swing setting, tempurature offset, display brightness, etc.).

The energy monitor driver emulates a device with both "Power Monitoring" and "Energy Monitoring" capabilities using the MQTT broker on the local LAN hosted by the Energy Bridge, specifically its 'event/metering/instantaneous_demand' and 'event/metering/summation/minute' endpoints, respectively.

The need for these drivers came about recently, specifically for AEP Ohio customers, as AEP lost regulatory approval to continue the services after November 30, 2020.

Not wanting to buy new ZWave thermostats and also not wanting to buy a separate energy monitor when there's already a perfectly working smart meter on the side of the house, I wrote these drivers after a lot of research into existing community thermostat [1] & MQTT [2] drivers, forum threads [3], [4] on the Powerley equipment and documentation [5] on the thermostat itself.

Drivers come with NO WARRANTY whatsoever and are Apache 2.0 licensed.

Do with as you please & enjoy!

[1]: https://github.com/djdizzyd/hubitat/blob/master/Drivers/Honeywell/Advanced-Honeywell-T6-Pro.groovy
[2]: https://github.com/mydevbox/hubitat-mqtt-link/tree/master/drivers/hubitat-mqtt-link-driver.groovy
[3]: https://github.com/home-assistant/core/issues/20170
[4]: https://www.reddit.com/r/homeassistant/comments/j7ykh6/for_any_aep_ohio_customers_with_the_powerley/
[5]: https://opensmarthouse.org/zwavedatabase/1149/PowerleyThermostat-Assoc-Parameter.pdf
