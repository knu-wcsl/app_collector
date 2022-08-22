package icsl.apps.collector;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Calendar;

public class ServerModule {
    private String TAG = "SERVER_MODULE";
    private Activity mActivity;
    private MeasurementListener mListener;

    // Status
    private boolean flag_connected = false;

    // Communication
    private Socket socket;
    private DataInputStream is;
    private DataOutputStream os;

    // Thread
    private Looper thread_looper;

    // Key
    private final String KEY_FILE_TRANSFER_CLIENT = "#N2nb023flkn3lkj%!df";
    private final String KEY_STATUS_CHECK_CLIENT = "b0D^GV#ADG213kjhb0#G";

    public ServerModule(Activity activity, MeasurementListener listener) {

        mActivity = activity;
        mListener = listener;

        HandlerThread handlerThread = new HandlerThread("SERVER_MODULE", Process.THREAD_PRIORITY_FOREGROUND);
        handlerThread.start();
        thread_looper = handlerThread.getLooper();
    }


    public void connect(String host, int port) {
        Handler handler = new Handler(thread_looper);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), 1000);
                    is = new DataInputStream(socket.getInputStream());
                    os = new DataOutputStream(socket.getOutputStream());

                    String msg = get_msg_from_server();

                    // get mac address of the device
                    String mac_addr = ((WifiManager) mActivity.getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getMacAddress();
                    os.write(mac_addr.getBytes());

                    // send KEY
                    os.write(KEY_STATUS_CHECK_CLIENT.getBytes());
                    get_msg_from_server();

                    flag_connected = true;
                    Log.d(TAG, "Connected to the server");
                    mListener.server_status(msg, MeasurementListener.TYPE_SERVER_CONNECTED);

                    // Save host/port information if connected
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mActivity);
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString("option_host", host);
                    editor.putString("option_port", String.valueOf(port));
                    editor.commit();

                } catch (IOException e) {
                    Log.d(TAG, "Connection failed");
                    mListener.server_status(String.format("Cannot connect to %s:%d", host, port), MeasurementListener.TYPE_SERVER_FAILED_TO_CONNECT);
                    e.printStackTrace();
                }
            }
        }, 0);
    }

    public void disconnect(){
        try{
            if (socket != null) {
                socket.close();
                mListener.server_status("Disconnected from server", MeasurementListener.TYPE_SERVER_DISCONNECTED);
            }
            flag_connected = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void sync_server_time(){
        int max_retry = 10;
        long rtt_ms = 0;
        long req_time = 0;
        String str_server_time = "0";
        try{
            for (int k=0; k<max_retry; k++) {
                req_time = Calendar.getInstance().getTimeInMillis();
                os.write("CMD_GET_TIME".getBytes());
                str_server_time = get_msg_from_server();
                Log.d(TAG, String.format("server time ms: %s", str_server_time));
                rtt_ms = Calendar.getInstance().getTimeInMillis() - req_time;

                if (rtt_ms < 100)      // if round-trip delay < 100ms
                    break;
            }

            if (rtt_ms < 100){
                long offset_ms = Long.parseLong(str_server_time) - (req_time + rtt_ms / 2);
                mListener.log_msg(String.format("Synchronized with the server (%d ms)", rtt_ms));
                mListener.log_msg(String.format("Server time offset: %.3f sec", (float)offset_ms / 1000f));
                mListener.server_status(String.valueOf(offset_ms), MeasurementListener.TYPE_SERVER_SYNC);
            } else{
                mListener.log_msg("Failed to synchronize with the server.");
                mListener.server_status("", MeasurementListener.TYPE_SERVER_SYNC);
                disconnect();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run_server_status_checker(){
        int refresh_interval_ms = 1000;
        if (!flag_connected){
            return;
        }

        try {
            os.write("CMD_STATUS".getBytes());
            String status = get_msg_from_server();
            mListener.server_status(status, MeasurementListener.TYPE_SERVER_STATUS);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Handler handler = new Handler(thread_looper);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                run_server_status_checker();
            }
        }, refresh_interval_ms);
    }

    private String get_msg_from_server(){
        byte[] buf = new byte[2048];
        String msg = "";
        try {
            int n = is.read(buf);
            if (n<=0) {                 // lost connection
                disconnect();
                return msg;
            }
            msg = new String(buf, 0, n);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return msg;
    }
}
