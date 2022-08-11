## Android application COLLECTOR

This is an Android studio project designing an application to collect Wi-Fi signal and various types of sensor readings.

At this moment, the application collects the following data, which are necessary for our study. 

- Sensor readings (accelerometer, gyroscope, magnetometer, rotation vector, game rotation vector, barometer)
- Wi-Fi measurement (received signal strength, round trip time)


## Installation
1. Open this repo from Android studio

2. Install apk to Android devices (make sure developer mode is enabled)


## Data collection
Data collection is initiated with the start button.

All the measurement data will be stored in a single file, which name is 'sensor_[device]\_[date]\_[time].txt'.

Matlab functions are used to parse each type of data.


## Contact
Jeongsik Choi (jeongsik.choi@knu.ac.kr)
