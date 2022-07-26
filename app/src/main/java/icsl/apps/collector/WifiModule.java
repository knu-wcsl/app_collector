package icsl.apps.collector;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.WIFI_RTT_RANGING_SERVICE;
import static android.os.SystemClock.elapsedRealtime;

public class WifiModule {

    private boolean is_wifi_running = false;
    private boolean is_rss_running = true;
    private boolean clear_to_scan = false;

    private final float min_scan_interval_ms = 500;
    private final float max_scan_interval_rss_ms = 5000;
    private final float max_scan_interval_rtt_ms = 1000;
    private final float retry_time_rss_ms = 8000;
    private final float retry_time_rtt_ms = 2000;
    private boolean is_scan_time_exceed_max_time = false;

    private long measurement_start_time_ms;
    private long last_scan_init_time_ms;

    private Activity mActivity;
    private FileModule file;

    private WifiManager wifiManager;
    private WifiReceiver wifiReceiver;
    private WifiAPManager wifiAPManager;

    // RTT measurement
    private WifiRttManager wifiRttManager;
    private Constructor responderConfigConstructor = null;
    private Method addResponderMethod = null;
    private int RESPONDER_AP = 0;
    private RangingRequest.Builder builder;
    private int ftm_bandwidth;
    private int curr_ftm_bandwidth;
    private int n_ftmr;
    private int[] n_active_ap;
    ArrayList<WifiAPManager.APInfo> aplist;
    private int curr_ap_idx;

    private int time_step_idx = 0;

    WifiModule(Activity activity){
        mActivity = activity;

        wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();
        wifiAPManager = new WifiAPManager(mActivity);
        ArrayList<String> init_result = wifiAPManager.get_init_result();
        for (int k=0; k<init_result.size(); k++)
            ((MainActivity) mActivity).update_log(init_result.get(k), false);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        activity.getApplicationContext().registerReceiver(wifiReceiver, intentFilter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            wifiRttManager = (WifiRttManager) mActivity.getApplicationContext().getSystemService(WIFI_RTT_RANGING_SERVICE);
            Log.d("WIFIMODULE", RangingRequest.getMaxPeers() + "");

            prepare_custom_rtt_ranging();
            aplist = wifiAPManager.get_ap_list();
        }
    }

    public void start_tracking(long _start_time_ms, FileModule _file, boolean _is_rss_running, int _ftm_bandwidth) {
        is_wifi_running = true;
        measurement_start_time_ms = _start_time_ms;
        file = _file;
        is_rss_running = _is_rss_running;
        ftm_bandwidth = _ftm_bandwidth;

        time_step_idx = 0;
        clear_to_scan = true;

        wifi_scan_routine();
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void prepare_custom_rtt_ranging() {
        // Access hidden class and method using Java reflection
        // Reference: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/wifi/java/android/net/wifi/rtt/ResponderConfig.java
        Class responderConfigClass = null;
        Field field = null;
        try {
            responderConfigClass = Class.forName("android.net.wifi.rtt.ResponderConfig");
            responderConfigConstructor = responderConfigClass.getConstructor(MacAddress.class, int.class, boolean.class, int.class, int.class, int.class, int.class, int.class);
            addResponderMethod = RangingRequest.Builder.class.getMethod("addResponder", responderConfigClass);
            field = responderConfigClass.getDeclaredField("RESPONDER_AP");
            RESPONDER_AP = field.getInt(null);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.P)
    private void add_ftmr_responder(String mac_str, int frequency, int centerFreq0, int centerFreq1, int channelWidth, int preamble){
        n_ftmr += 1;
        Object responder = null;
        try {
            responder = responderConfigConstructor.newInstance(MacAddress.fromString(mac_str), RESPONDER_AP, true, channelWidth, frequency, centerFreq0, centerFreq1, preamble);
            addResponderMethod.invoke(builder, responder);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.P)
    private void invoke_rtt_scan() {

        if (curr_ap_idx >= aplist.size()) {
            curr_ftm_bandwidth += 1;
            curr_ap_idx = 0;
        }

        if ((curr_ftm_bandwidth >= 3) || (curr_ftm_bandwidth > ftm_bandwidth)){
            // Ranging is done
            clear_to_scan = true;
            float elapsed_scan_time_ms = elapsedRealtime() - last_scan_init_time_ms;
            String str_status = String.format("Step: %d, time: %d ms, # AP: (%d, %d, %d) (total: %d)", time_step_idx, (int)elapsed_scan_time_ms, n_active_ap[0], n_active_ap[1], n_active_ap[2], aplist.size());
            ((MainActivity) mActivity).update_measurement_status(str_status, 1);
            return;
        }

        Log.d("WIFIMODULE", curr_ftm_bandwidth + ", " + ftm_bandwidth + ", " + curr_ap_idx);


        n_ftmr = 0;
        builder = new RangingRequest.Builder();
        add_ftmr_responder(aplist.get(curr_ap_idx).mac_addr, aplist.get(curr_ap_idx).freq, 0, 0, curr_ftm_bandwidth, 2);
        curr_ap_idx += 1;

        RangingRequest rangingRequest;
        rangingRequest = builder.build();

        if (n_ftmr == 0)
            return;

        if (ActivityCompat.checkSelfPermission(mActivity.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            wifiRttManager.startRanging(rangingRequest, AsyncTask.SERIAL_EXECUTOR, rangingcallback); //...and send out the ranging request. When results are obtained, rangingcallback will be called
    }


    @RequiresApi(Build.VERSION_CODES.P)
    private RangingResultCallback rangingcallback = new RangingResultCallback() {
        @Override
        public void onRangingFailure(final int i) { // ranging failed completely
            ((MainActivity) mActivity).update_log("[Warning] RTT failed", false);
            invoke_rtt_scan();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> results) {
            float elapsed_time_ms = elapsedRealtime() - measurement_start_time_ms;
            float elapsed_scan_time_ms = elapsedRealtime() - last_scan_init_time_ms;
            String str_to_file = String.format("RTT, %f, %f", elapsed_time_ms/1e3, elapsed_scan_time_ms/1e3);
            if (curr_ftm_bandwidth == 0)
                str_to_file += ", 20\n";
            else if (curr_ftm_bandwidth == 1)
                str_to_file += ", 40\n";
            else if (curr_ftm_bandwidth == 2)
                str_to_file += ", 80\n";

            for (int k=0; k < results.size(); k++) {
                RangingResult curr = results.get(k);
                if (curr.getStatus() == RangingResult.STATUS_SUCCESS) {
                    n_active_ap[curr_ftm_bandwidth] += 1;
                    str_to_file += String.format("RTT, %f, %s, %f, %f, %d, %d, %d\n", (float) (curr.getRangingTimestampMillis() - measurement_start_time_ms)/1e3, curr.getMacAddress(), (float) curr.getDistanceMm() / 1e3, (float) curr.getDistanceStdDevMm() / 1e3, curr.getRssi(), curr.getNumSuccessfulMeasurements(), curr.getNumAttemptedMeasurements());
                }
            }
            file.save_str_to_file(str_to_file);
            invoke_rtt_scan();
        }
    };


    private void wifi_scan_routine(){
        if (!is_wifi_running)
            return;

        long sleep_time_ms = 50;
        if (clear_to_scan){
            last_scan_init_time_ms = elapsedRealtime();
            is_scan_time_exceed_max_time = false;
            clear_to_scan = false;

            if (is_rss_running){
                file.save_str_to_file(String.format("RSS, %f\n", (float)((elapsedRealtime() - measurement_start_time_ms)/1e3)));
                boolean is_scan_success = wifiManager.startScan();
                if (!is_scan_success) {
                    ((MainActivity) mActivity).update_log("[Warning] Couldn't initiate WiFi scan (throttling)", false);
                    sleep_time_ms = 5000;
                }
                else
                    sleep_time_ms = 3000;
            }
            else{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (ftm_bandwidth == 3)
                        curr_ftm_bandwidth = 0;
                    else
                        curr_ftm_bandwidth = ftm_bandwidth;
                    file.save_str_to_file(String.format("RTT, %f\n", (float)((elapsedRealtime() - measurement_start_time_ms)/1e3)));
                    n_active_ap = new int[3];
                    curr_ap_idx = 0;
                    time_step_idx += 1;
                    invoke_rtt_scan();
                }
                sleep_time_ms = (long) min_scan_interval_ms - (elapsedRealtime() - last_scan_init_time_ms);
            }
        }
        else {
            long curr_time_ms = elapsedRealtime();
            float elapsed_time_ms = curr_time_ms - last_scan_init_time_ms;
            float max_scan_interval_ms = max_scan_interval_rtt_ms;
            float retry_time_ms = retry_time_rtt_ms;
            if (is_rss_running) {
                max_scan_interval_ms = max_scan_interval_rss_ms;
                retry_time_ms = retry_time_rss_ms;
            }

            if ((is_scan_time_exceed_max_time == false) && (elapsed_time_ms > max_scan_interval_ms)) {
                ((MainActivity) mActivity).update_log(String.format("[Warning] WiFi scan procedure is not responding for %.1f sec", elapsed_time_ms / 1e3), false);
                is_scan_time_exceed_max_time = true;
            }

            if(elapsed_time_ms > retry_time_ms){
                ((MainActivity) mActivity).update_log(String.format("[Warning] WiFi scan timeout (%.1f sec). Restart the scan procedure.", elapsed_time_ms / 1e3), false);
                clear_to_scan = true;
                sleep_time_ms = 0;
            }
        }

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                wifi_scan_routine();
            }
        }, sleep_time_ms);
    }


    public void stop_tracking(){
        is_wifi_running = false;
    }


    class WifiReceiver extends BroadcastReceiver {

        private List<ScanResult> scanResults;

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("WifiModule", "Received scan results");
            if (!is_wifi_running)
                return;
            if (!is_rss_running)
                return;

            // Process RSS scan results
            scanResults = wifiManager.getScanResults();
            float elapsed_time_ms = elapsedRealtime() - measurement_start_time_ms;
            float elapsed_scan_time_ms = elapsedRealtime() - last_scan_init_time_ms;
            clear_to_scan = true;

            if (elapsed_scan_time_ms < 100)
                return;
            time_step_idx += 1;

            int n_active_ap_2 = 0;
            int n_active_ap_5 = 0;
            String str_to_file = String.format("RSS, %f, %f\n", elapsed_time_ms/1e3, elapsed_scan_time_ms/1e3);

            for (int k = 0; k < scanResults.size(); k++) {
                ScanResult curr = scanResults.get(k);
                wifiAPManager.update_freq(curr.BSSID, curr.frequency, curr.SSID);

                str_to_file += String.format("RSS, %f, %s, %s, %d, %d, %d, %d, %d", (float)(curr.timestamp/1e6 - measurement_start_time_ms/1e3), curr.BSSID, curr.SSID, curr.frequency, curr.centerFreq0, curr.centerFreq1, curr.channelWidth, curr.level);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
//                    List<ScanResult.InformationElement> info = curr.getInformationElements();
                    str_to_file += ", " + curr.getWifiStandard();
                }
                str_to_file += "\n";
                if (curr.frequency >= 5000)
                    n_active_ap_5 += 1;
                else
                    n_active_ap_2 += 1;
            }
            file.save_str_to_file(str_to_file);

            String update_result = wifiAPManager.update_ftmr_file();
            if (update_result != null)
                ((MainActivity) mActivity).update_log(update_result, false);

            String str_status = String.format("Step: %d, time: %d ms, # AP: %d (2.4G: %d, 5G: %d)", time_step_idx, (int)elapsed_scan_time_ms, n_active_ap_2+n_active_ap_5, n_active_ap_2, n_active_ap_5);
            ((MainActivity) mActivity).update_measurement_status(str_status, 1);
        }
    }
}
