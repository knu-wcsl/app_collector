package icsl.apps.collector;

public interface MeasurementListener {
    // This interface implements callback functions

    public static final int TYPE_INIT_SUCCESS = 0;
    public static final int TYPE_INIT_FAILED = 1;
    public static final int TYPE_SENSOR_STATUS = 2;
    public static final int TYPE_SENSOR_VALUE = 3;
    public static final int TYPE_WIFI_STATUS = 4;
    public static final int TYPE_RSS_VALUE = 5;
    public static final int TYPE_RTT_VALUE = 6;

    public void log_msg(String log);
    public void sensor_status(String status, int type);
    public void wifi_status(String status, int type);
}