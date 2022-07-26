package com.example.pdr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.opengl.Matrix;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import java.security.Provider;
import java.util.ArrayList;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.SENSOR_SERVICE;
import static android.os.SystemClock.elapsedRealtime;

public class SensorModule extends Service implements SensorEventListener, LocationListener {
    // Sensor
    private boolean flag_is_sensor_running = false;
    private int n_sensor;
    private Sensor s1, s2, s3, s4, s5, s6;
    private SensorManager sensorManager;

    private int sensor_sampling_interval_ms = 10;
    private int pres_sensor_sampling_interval_ms = 500;
    private float actual_sampling_interval_s;
    private long measurement_start_time_ms;

    private float[] last_update_time_s;    // for each sensor
    private int[] count;                // # or sensor measurements for each sensor

    // Array for sensor data
    private float[] accL = new float[]{0, 0, 0, 0};
    private float[] gyroL = new float[]{0, 0, 0, 0};
    private float[] magL = new float[]{0, 0, 0, 0};
    private float[] rot_vec = new float[]{0, 0, 0, 0};
    private float[] game_rot_vec = new float[]{0, 0, 0, 0};
    private float pres = 0;

    // For orientation & step detection
    private float[] accW = new float[]{0, 0, 0, 0};
    private float[] orientation_angle = new float[]{0, 0, 0, 0};

    private float[] rot_mat = new float[16];
    private float[] game_rot_mat = new float[16];
    private float[] rot_mat_openGL = new float[16];     // Matrix defined in OpenGL have different order

    private float gravity_earth = 9.8f;
    private float az_lpf, az_lpf_next, az_lpf_prev;     // z-axis acc (w.r.t WCS)

    private float[] peak_info = new float[2];           // (acc, time_s)
    private float[] valley_info = new float[2];
    private float[] prev_valley_info = new float[2];
    private float heading_at_peak;

    private boolean is_peak_processed;
    private int step_counter;

    private static final float PI = 3.14159265359f;

    // GPS
    private boolean isGPSEnabled = false;
    private LocationManager locationManager;
    private float last_gps_update_time_s;
    private int count_gps;

    // File IO
    private FileModule file;

    // Report result
    private final float sensor_report_interval_s = 0.1f;
    private float last_sensor_report_time_s;
    private Activity mActivity;

    private final String LOG_TAG = "SENSOR";


    SensorModule(Activity activity) {
        mActivity = activity;
        n_sensor = 6;

        // Sensor manager
        sensorManager = (SensorManager) activity.getApplicationContext().getSystemService(SENSOR_SERVICE);

        s1 = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);         // accelerometer
        s2 = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);             // gyroscope
        s3 = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);        // magnetic field
        s4 = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);       // built-in sensor fusion alg. (acc + gyro + mag)
        s5 = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);  // built-in sensor fusion alg. (acc + gyro)
        s6 = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);              // barometer

        last_update_time_s = new float[n_sensor];
        count = new int[n_sensor];

        // GPS
        locationManager = (LocationManager) activity.getApplication().getSystemService(LOCATION_SERVICE);
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        ((MainActivity) mActivity).update_log("Sensors are ready", false);
        if (isGPSEnabled)
            ((MainActivity) mActivity).update_log("GPS is ready", false);
        else
            ((MainActivity) mActivity).update_log("[Warning] Unable to initialize GPS", false);
    }


    private void detect_new_step(float acc_diff, float head_avg) {
        step_counter++;
    }


    private void reset_step_detection_routine() {
        peak_info[0] = last_update_time_s[0];
        peak_info[1] = gravity_earth;

        valley_info[0] = last_update_time_s[0];
        valley_info[1] = gravity_earth;

        is_peak_processed = true;

        az_lpf_prev = gravity_earth;
        az_lpf = gravity_earth;

        step_counter = 0;
    }


    // Step detection routine (called whenever new accelerometer readings are available)
    private void step_detection_routine() {
        float curr_time_s = last_update_time_s[0];

        // Parameters
        float cutoff_freq_lpf = 15.0f;      // Low pass filter for acceleration (e.g., 15 Hz)
        float peak_detection_th = 0.7f;

        float min_step_freq = 1.0f;         // slowest step frequency in Hz
        float max_step_freq = 2.5f;         // fastest step frequency
        float min_step_time = 1.0f / max_step_freq;
        float max_step_time = 1.0f / min_step_freq;

        float close_peak_time = min_step_time / 2.0f;   // half of the fastest step

        float cut_off_alpha = cutoff_freq_lpf * actual_sampling_interval_s;

        // transform accelerometer readings w.r.t world coordinates system
        // accW: acceleration w.r.t world coordinates system (transformed)
        // accL: acceleration w.r.t local coordinates system (raw measurement)
        Matrix.transposeM(rot_mat_openGL, 0, game_rot_mat, 0);
        Matrix.multiplyMV(accW, 0, rot_mat_openGL, 0, accL, 0);

        // update gravity using z-axis component of accW
        gravity_earth = (1 - 0.0001f) * gravity_earth + 0.0001f * accW[2];
        if ((gravity_earth > 10.0f) || (gravity_earth < 9.6f))
            Log.d(LOG_TAG, "Current gravity estimation: " + gravity_earth);

        // get orientation of the device (yaw, pitch, roll)
        sensorManager.getOrientation(game_rot_mat, orientation_angle);

        // peak & valley detection routine
        az_lpf_prev = az_lpf;       // previous acceleration
        az_lpf = az_lpf_next;       // current acceleration
        az_lpf_next = az_lpf * (1 - cut_off_alpha) + accW[2] * cut_off_alpha; // next acceleration

        // peak detection
        if ((az_lpf >= az_lpf_prev) && (az_lpf >= az_lpf_next) && (az_lpf > (gravity_earth + peak_detection_th))) {
            // check if two peaks are too close
            if ((curr_time_s - peak_info[0]) < close_peak_time) {
                if (peak_info[1] < az_lpf) {
                    peak_info[0] = curr_time_s;
                    peak_info[1] = az_lpf;

                    System.arraycopy(prev_valley_info, 0, valley_info, 0, 2);   // recover valley info
                    return;
                }
            }

            // process prev peak
            if (!is_peak_processed) {
                if (valley_info[0] <= peak_info[0]) {
                    Log.d(LOG_TAG, "Warning: no new valley observed after a peak");
                } else {
                    float peak_to_peak_time = curr_time_s - peak_info[0];

                    if (valley_info[1] < (gravity_earth - peak_detection_th)) {
                        if ((peak_to_peak_time <= max_step_time) && (peak_to_peak_time > (min_step_time - close_peak_time))) {
                            float acc_diff = peak_info[1] - valley_info[1];
                            detect_new_step(acc_diff, heading_at_peak);
                        }
                    }
                }
            }

            // new peak
            System.arraycopy(valley_info, 0, prev_valley_info, 0, 2);   // store valley info

            peak_info[0] = curr_time_s;
            peak_info[1] = az_lpf;
            heading_at_peak = orientation_angle[0];

            System.arraycopy(peak_info, 0, valley_info, 0, 2);          // reset valley info
            is_peak_processed = false;
        }

        // valley detection
        if ((az_lpf <= az_lpf_prev) && (az_lpf <= az_lpf_next)) {
            if (az_lpf < valley_info[1]) {
                valley_info[0] = curr_time_s;
                valley_info[1] = az_lpf;
            }

            // refresh valley regularly
            if ((curr_time_s - valley_info[0]) > max_step_time) {
                valley_info[0] = curr_time_s;
                valley_info[1] = az_lpf;

                if (!is_peak_processed) {
                    // process peak (need to implement)
                    // need to process?
                    //detect_new_step(0, 0);
                    is_peak_processed = true;
                }
            }
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        // Sensor data
        float elapsed_app_time_s = (float) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);      // Elapsed time computed using Android OS
        float elapsed_sensor_fw_time_s = (float) (event.timestamp / 1e9 - measurement_start_time_ms / 1e3);  // Elapsed time computed using Sensor Firmware

        if ((elapsed_app_time_s - last_sensor_report_time_s) >= sensor_report_interval_s) {
            // Periodically update measurement status
            last_sensor_report_time_s = sensor_report_interval_s;
            report_sensor_measurement_status();
        }

        if (!flag_is_sensor_running)
            return;

        // Save each sensor data (actual sampling rate is computed with accelerometer readings)
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            actual_sampling_interval_s = 0.99f * actual_sampling_interval_s + 0.01f * (elapsed_app_time_s - last_update_time_s[0]);

            System.arraycopy(event.values, 0, accL, 0, 3);
            step_detection_routine();   // call step detection routine (PDR)

            last_update_time_s[0] = elapsed_app_time_s;
            count[0]++;
            file.save_str_to_file(String.format("ACC, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, accL[0], accL[1], accL[2]));
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroL, 0, 3);
            last_update_time_s[1] = elapsed_app_time_s;
            count[1]++;
            file.save_str_to_file(String.format("GYRO, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, gyroL[0], gyroL[1], gyroL[2]));
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magL, 0, 3);
            last_update_time_s[2] = elapsed_app_time_s;
            count[2]++;
            file.save_str_to_file(String.format("MAG, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, magL[0], magL[1], magL[2]));
        }

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, rot_vec, 0, 4);
            SensorManager.getRotationMatrixFromVector(rot_mat, event.values);
            last_update_time_s[3] = elapsed_app_time_s;
            count[3]++;
            file.save_str_to_file(String.format("ROT_VEC, %f, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, rot_vec[0], rot_vec[1], rot_vec[2], rot_vec[3]));
        }

        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, game_rot_vec, 0, 4);
            SensorManager.getRotationMatrixFromVector(game_rot_mat, event.values);
            last_update_time_s[4] = elapsed_app_time_s;
            count[4]++;
            file.save_str_to_file(String.format("GAME_ROT_VEC, %f, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, game_rot_vec[0], game_rot_vec[1], game_rot_vec[2], game_rot_vec[3]));
        }

        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pres = event.values[0];
            last_update_time_s[5] = elapsed_app_time_s;
            count[5]++;
            file.save_str_to_file(String.format("PRES, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, pres));
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not implemented yet
    }


    @Override
    public void onLocationChanged(@NonNull Location location) {
        // GPS data
        count_gps += 1;
        float elapsed_app_time_s = (float) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);      // Elapsed time computed using Android OS
        float elapsed_fw_time_s = (float) (location.getElapsedRealtimeNanos() / 1e9 - measurement_start_time_ms / 1e3);

        if ((elapsed_app_time_s - last_sensor_report_time_s) >= sensor_report_interval_s) {
            // Periodically update measurement status
            last_sensor_report_time_s = sensor_report_interval_s;
            report_sensor_measurement_status();
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        float accuracy = location.getAccuracy();    // Horizontal accuracy

        double altitude = location.getAltitude();
        float bearing = location.getBearing();
        float speed = location.getSpeed();
        float verticalAccuracy = -1;
        float bearingAccuracy = -1;
        float speedAccuracy = -1;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
             verticalAccuracy = location.getVerticalAccuracyMeters();
             bearingAccuracy = location.getBearingAccuracyDegrees();
             speedAccuracy = location.getSpeedAccuracyMetersPerSecond();
        }

        // GPS
        if (flag_is_sensor_running)
            file.save_str_to_file(String.format("GPS, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_fw_time_s, latitude, longitude, altitude, bearing, speed, accuracy, verticalAccuracy, bearingAccuracy, speedAccuracy));
    }


    private void report_sensor_measurement_status() {
        String sensor_str = String.format("Sampling rate: %.2f Hz, Step counter: %d\n", 1.0f / actual_sampling_interval_s, (int) step_counter);
        sensor_str += String.format("    Azimuth: %d deg, Pitch: %d deg, Roll: %d deg\n", (int)(orientation_angle[0] * 180. / PI), (int)(orientation_angle[1] * 180. / PI), (int)(orientation_angle[2] * 180. / PI));
        for (int k = 0; k < n_sensor; k++) {
            if (k == 0)
                sensor_str += "    # data: Acc";
            else if (k == 1)
                sensor_str += ", Gyro";
            else if (k == 2)
                sensor_str += ", Mag";
            else if (k == 3)
                sensor_str += "\n    RVec";
            else if (k == 4)
                sensor_str += ", GRVec";
            else if (k == 5)
                sensor_str += ", Pres";

            if (count[k] < 1000)
                sensor_str += String.format("(%d)", count[k]);
            else
                sensor_str += String.format("(%.1fk)", count[k] / 1e3);
        }

        sensor_str += ", GPS";
        if (count_gps < 1000)
            sensor_str += String.format("(%d)", count_gps);
        else
            sensor_str += String.format("(%.1fk)", count_gps / 1e3);

        ((MainActivity) mActivity).update_measurement_status(sensor_str, 0);
    }


    // start & stop sensor measurement (called from MainActivity)
    public void start_tracking(long start_time_from_main_ms, FileModule file_from_main, boolean is_enable_gps) {
        if (!flag_is_sensor_running) {
            // Init
            measurement_start_time_ms = start_time_from_main_ms;
            file = file_from_main;

            // Sensor module
            sensorManager.registerListener(this, s1, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s2, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s3, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s4, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s5, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s6, pres_sensor_sampling_interval_ms * 1000);

            for (int k = 0; k < n_sensor; k++) {
                last_update_time_s[k] = 0;          // from start_time
                count[k] = 0;
            }
            last_sensor_report_time_s = 0;
            actual_sampling_interval_s = sensor_sampling_interval_ms / 1000.0f;

            reset_step_detection_routine();

            // GPS
            if (ActivityCompat.checkSelfPermission(mActivity.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mActivity.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(mActivity.getApplicationContext(), "[WARNING] GPS coordinates will not be recorded", Toast.LENGTH_LONG).show();
            else {
                if (is_enable_gps)
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, this);
                last_gps_update_time_s = 0;
                last_gps_update_time_s = 0;
                count_gps = 0;
            }
        }
        flag_is_sensor_running = true;
    }

    public void stop_tracking(){
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
        if (locationManager != null)
            locationManager.removeUpdates(this);

        if (flag_is_sensor_running){
            flag_is_sensor_running = false;
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}