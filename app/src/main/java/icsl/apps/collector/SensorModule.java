package icsl.apps.collector;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
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

import static android.os.SystemClock.elapsedRealtime;

public class SensorModule extends Service implements SensorEventListener, LocationListener {
    // Sensor
    private boolean flag_is_sensor_running = false;
    private int n_sensor;
    private Sensor s1, s2, s3, s4, s5, s6, s7, s8, s9, s10;
    private SensorManager sensorManager;

    private boolean flag_collect_sensor_data = false;
    private boolean flag_collect_gps_data = false;
    private boolean flag_gps_available = false;

    private int sensor_sampling_interval_ms = 10;
    private int gps_sampling_interval_ms = 10;
    private int env_sensor_sampling_interval_ms = 500;
    private float actual_sampling_interval_s;
    private long measurement_start_time_ms;

    private float[] last_update_time_s;    // for each sensor
    private int[] count;                // # or sensor measurements for each sensor

    // Placeholder for sensor data
    private float[] accL = new float[]{0, 0, 0, 0};
    private float[] gyroL = new float[]{0, 0, 0, 0};
    private float[] magL = new float[]{0, 0, 0, 0};
    private float[] rot_vec = new float[]{0, 0, 0, 0};
    private float[] game_rot_vec = new float[]{0, 0, 0, 0};
    private float pres = 0;
    private float temp = 0;
    private float light = 0;
    private float humidity = 0;
    private float proximity = 0;

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
    private LocationManager locationManager;
    private int count_gps;

    // File IO
    private FileModule file;

    // Report result
    private Activity mActivity;
    private MeasurementListener mListener;
    private float last_report_time_s;
    private float screen_refresh_interval_s;

    private final String TAG = "SENSOR_MODULE";

    SensorModule(Activity activity, MeasurementListener listener) {
        mActivity = activity;
        mListener = listener;
        n_sensor = 10;

        // Sensor manager
        sensorManager = (SensorManager) activity.getApplicationContext().getSystemService(SENSOR_SERVICE);

        s1 = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);         // accelerometer
        s2 = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);             // gyroscope
        s3 = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);        // magnetic field
        s4 = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);       // built-in sensor fusion alg. (acc + gyro + mag)
        s5 = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);  // built-in sensor fusion alg. (acc + gyro)
        s6 = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);              // barometer
        s7 = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        s8 = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        s9 = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        s10 = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        last_update_time_s = new float[n_sensor];
        count = new int[n_sensor];

        // GPS
        locationManager = (LocationManager) activity.getApplication().getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(mActivity.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mActivity.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            flag_gps_available = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        // Load settings
        loadSettings();
        report_init_status();
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
            Log.d(TAG, "Current gravity estimation: " + gravity_earth);

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
                    Log.d(TAG, "Warning: no new valley observed after a peak");
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
        float elapsed_app_time_s = (float) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);      // elapsed time computed using Android OS
        float elapsed_sensor_fw_time_s = (float) (event.timestamp / 1e9 - measurement_start_time_ms / 1e3);  // elapsed time computed using Sensor Firmware
        if (!flag_is_sensor_running)
            return;

        // Save each sensor data (actual sampling rate is computed with accelerometer readings)
        int sensor_idx = -1;
        String str_to_file = "";

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            actual_sampling_interval_s = 0.99f * actual_sampling_interval_s + 0.01f * (elapsed_app_time_s - last_update_time_s[0]);

            System.arraycopy(event.values, 0, accL, 0, 3);
            step_detection_routine();   // call step detection routine (PDR)

            sensor_idx = 0;
            str_to_file = String.format("ACC, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, accL[0], accL[1], accL[2]);
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroL, 0, 3);
            sensor_idx = 1;
            str_to_file = String.format("GYRO, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, gyroL[0], gyroL[1], gyroL[2]);
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magL, 0, 3);
            sensor_idx = 2;
            str_to_file = String.format("MAG, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, magL[0], magL[1], magL[2]);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, rot_vec, 0, 4);
            SensorManager.getRotationMatrixFromVector(rot_mat, event.values);
            sensor_idx = 3;
            str_to_file = String.format("ROT_VEC, %f, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, rot_vec[0], rot_vec[1], rot_vec[2], rot_vec[3]);
        }
        else if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, game_rot_vec, 0, 4);
            SensorManager.getRotationMatrixFromVector(game_rot_mat, event.values);
            sensor_idx = 4;
            str_to_file = String.format("GAME_ROT_VEC, %f, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, game_rot_vec[0], game_rot_vec[1], game_rot_vec[2], game_rot_vec[3]);
        }
        else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pres = event.values[0];
            sensor_idx = 5;
            str_to_file = String.format("PRES, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, pres);
        }
        else if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            temp = event.values[0];
            sensor_idx = 6;
            str_to_file = String.format("TEMP, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, temp);
        }
        else if (event.sensor.getType() == Sensor.TYPE_LIGHT){
            light = event.values[0];
            sensor_idx = 7;
            str_to_file = String.format("LIGHT, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, light);
        }
        else if (event.sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY){
            humidity = event.values[0];
            sensor_idx = 8;
            str_to_file = String.format("HUMIDITY, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, humidity);
        }
        else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            proximity = event.values[0];
            sensor_idx = 9;
            str_to_file = String.format("PROX, %f, %f, %f\n", elapsed_app_time_s, elapsed_sensor_fw_time_s, proximity);
        }
        else{
            Log.d(TAG, String.format("Unidentified sensor type: %d", event.sensor.getType()));
        }
        if (sensor_idx >= 0){
            last_update_time_s[sensor_idx] = elapsed_app_time_s;
            count[sensor_idx]++;
        }
        if (file != null && str_to_file.length()>0)
            file.save_str_to_file(str_to_file);
        report_measurement_status();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing to implement
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        // GPS data
        count_gps += 1;
        float elapsed_app_time_s = (float) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);      // Elapsed time computed using Android OS
        float elapsed_fw_time_s = (float) (location.getElapsedRealtimeNanos() / 1e9 - measurement_start_time_ms / 1e3);

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
        if (flag_is_sensor_running && file != null)
            file.save_str_to_file(String.format("GPS, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f\n", elapsed_app_time_s, elapsed_fw_time_s, latitude, longitude, altitude, bearing, speed, accuracy, verticalAccuracy, bearingAccuracy, speedAccuracy));
        report_measurement_status();
    }

    private void report_init_status(){
        String str_setting = "[Sensor settings]\n";
        if (flag_collect_sensor_data){
            str_setting += "Sensor measurement: enabled\n";
            if (sensor_sampling_interval_ms == 0)
                str_setting += "Sensor sampling rate: fastest\n";
            else
                str_setting += String.format("Sensor sampling rate: %.0f [Hz]\n", 1000. / sensor_sampling_interval_ms);
        }
        else
            str_setting += "Sensor measurement: disabled\n";
        if (flag_collect_gps_data){
            if (!flag_gps_available)
                str_setting += "WARNING: GPS is not available on this device\n";
            else {
                str_setting += "GPS measurement: enabled\n";
                if (gps_sampling_interval_ms == 0)
                    str_setting += "GPS sampling interval: fastest\n";
                else
                    str_setting += String.format("GPS sampling interval: %.1f [sec]\n", gps_sampling_interval_ms / 1000.);
            }
        }
        else
            str_setting += "GPS measurement: disabled\n";

        if (flag_collect_sensor_data || (flag_collect_gps_data && flag_gps_available))  // sensor data will be collected
            mListener.sensor_status(str_setting, MeasurementListener.TYPE_INIT_SUCCESS);
        else
            mListener.sensor_status(str_setting, MeasurementListener.TYPE_INIT_FAILED);
        mListener.log_msg(str_setting);
    }

    private void report_measurement_status(){
        float elapsed_app_time_s = (float) (elapsedRealtime() / 1e3 - measurement_start_time_ms / 1e3);
        if ((elapsed_app_time_s - last_report_time_s) >= screen_refresh_interval_s){
            last_report_time_s = elapsed_app_time_s;
            String status = String.format("Sampling rate: %.2f Hz, Step counter: %d\n", 1.0f / actual_sampling_interval_s, (int) step_counter);
            status += String.format("    Azimuth: %d deg, Pitch: %d deg, Roll: %d deg\n", (int)(orientation_angle[0] * 180. / PI), (int)(orientation_angle[1] * 180. / PI), (int)(orientation_angle[2] * 180. / PI));
            for (int k = 0; k < n_sensor; k++) {
                if (k == 0)
                    status += "    # data: Acc";
                else if (k == 1)
                    status += ", Gyro";
                else if (k == 2)
                    status += ", Mag";
                else if (k == 3)
                    status += "\n    RVec";
                else if (k == 4)
                    status += ", GRVec";
                else if (k == 5)
                    status += ", Pres";
                else if (k == 6)
                    status += ", Temp";
                else if (k == 7)
                    status += "\n    Light";
                else if (k == 8)
                    status += ", Humidity";
                else if (k == 9)
                    status += ", Prox";

                if (count[k] < 1000)
                    status += String.format("(%d)", count[k]);
                else
                    status += String.format("(%.1fk)", count[k] / 1e3);
            }

            status += ", GPS";
            if (count_gps < 1000)
                status += String.format("(%d)", count_gps);
            else
                status += String.format("(%.1fk)", count_gps / 1e3);

            String result = "";
            result += String.format("Local Acc x: %2.2f,  y: %2.2f,  z: %2.2f [m/s^2]\n", accL[0], accL[1], accL[2]);
            result += String.format("Global Acc x: %2.2f,  y: %2.2f,  z: %2.2f [m/s^2]\n", accW[0], accW[1], accW[2]);
            result += String.format("Local Gyro x: %2.2f,  y: %2.2f,  z: %2.2f [rad/s]\n", gyroL[0], gyroL[1], gyroL[2]);
            result += String.format("Local Mag x: %2.2f,  y: %2.2f,  z: %2.2f [uT]\n", magL[0], magL[1], magL[2]);
            result += String.format("Air pressure: %.2f [hPa]\n", pres);
            result += String.format("Temperature: %.2f [Celsius]\n", temp);
            result += String.format("Light: %.2f [lx]\n", light);
            result += String.format("Humidity: %.2f [%%]\n", humidity);
            result += String.format("Proximity: %.2f [cm]\n", proximity);

            mListener.sensor_status(status, MeasurementListener.TYPE_SENSOR_STATUS);
            mListener.sensor_status(result, MeasurementListener.TYPE_SENSOR_VALUE);
        }
    }

    // start & stop sensor measurement
    public boolean start_measurement(long start_time_ms, FileModule _file) {
        if (flag_is_sensor_running) {
            Toast.makeText(mActivity.getApplicationContext(), "Sensor measurement is already running", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!flag_collect_sensor_data) {
            if (!flag_collect_gps_data) {
                Toast.makeText(mActivity.getApplicationContext(), "Both sensor/GPS measurements are disabled", Toast.LENGTH_SHORT).show();
                return false;
            } else if (!flag_gps_available) {
                Toast.makeText(mActivity.getApplicationContext(), "GPS is not available", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        // start measurement
        measurement_start_time_ms = start_time_ms;
        file = _file;

        if (flag_collect_sensor_data) {
            // Sensor module
            sensorManager.registerListener(this, s1, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s2, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s3, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s4, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s5, sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s6, env_sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s7, env_sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s8, env_sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s9, env_sensor_sampling_interval_ms * 1000);
            sensorManager.registerListener(this, s10, env_sensor_sampling_interval_ms * 1000);
        }

        // Reset sensor-related values
        for (int k = 0; k < n_sensor; k++) {
            last_update_time_s[k] = 0;          // from start_time
            count[k] = 0;
        }
        actual_sampling_interval_s = sensor_sampling_interval_ms / 1000.0f;
        reset_step_detection_routine();

        // GPS
        if (flag_collect_gps_data) {
            if (ActivityCompat.checkSelfPermission(mActivity.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mActivity.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, gps_sampling_interval_ms, 0, this);
            count_gps = 0;
            if (!flag_gps_available)
                Toast.makeText(mActivity, "[Warning] GPS is not available", Toast.LENGTH_SHORT).show();
        }
        flag_is_sensor_running = true;
        last_report_time_s = 0f;
        return true;
    }

    public void stop_measurement(){
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
        if (locationManager != null)
            locationManager.removeUpdates(this);
        flag_is_sensor_running = false;
    }

    private void loadSettings() {
        Log.d(TAG, "Load settings");
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mActivity);
        flag_collect_sensor_data = pref.getBoolean("option_collect_sensor", true);
        flag_collect_gps_data = pref.getBoolean("option_collect_gps", true);
        sensor_sampling_interval_ms = Integer.parseInt(pref.getString("option_sensor_interval", "10"));
        gps_sampling_interval_ms = Integer.parseInt(pref.getString("option_gps_interval", "500"));
        screen_refresh_interval_s = 1f / Float.parseFloat(pref.getString("option_screen_refresh_rate", "10"));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}