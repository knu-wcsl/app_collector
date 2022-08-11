## Android application COLLECTOR

This is an Android studio project designing an application to collect Wi-Fi signal and various types of sensor readings.
At this moment, the application collects the following data, which are necessary for our study. 
- Sensor readings (accelerometer, gyroscope, magnetometer, rotation vector, game rotation vector, barometer)
- Wi-Fi measurement (received signal strength, round trip time)


## Installation
1. Open this repo from Android studio
2. Install apk to Android devices 


## Data collection
Data collection is initiated if the start button is clicked.
All the measurement data will be store in a single file, which name is 'sensor_[device]\_[date]\_[time].txt'
Use matlab function to parse each type of data.


## Contact
Jeongsik Choi (jeongsik.choi@knu.ac.kr)
