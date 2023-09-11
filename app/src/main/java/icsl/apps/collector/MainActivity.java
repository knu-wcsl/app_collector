package icsl.apps.collector;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private String TAG = "MainActivity";
    private boolean flag_permission_granted = false;
    private BottomNavigationView bottomNavigationView;
    private int active_fragment_idx = -1;

    // Fragments
    private HomeFragment homeFragment;
    private ServerFragment serverFragment;
    private WifiFragment wifiFragment;
    private SensorFragment sensorFragment;
    private BLEFragment bleFragment;
    private SettingsFragment settingFragment;
    private ImageButton btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permission
        ActivityCompat.requestPermissions(this, new String[]{WRITE_EXTERNAL_STORAGE, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CHANGE_WIFI_STATE, ACCESS_WIFI_STATE}, 1);

        // Fragments
        homeFragment = new HomeFragment();
        serverFragment = new ServerFragment();
        wifiFragment = new WifiFragment();
        sensorFragment = new SensorFragment();
        bleFragment = new BLEFragment();
        settingFragment = new SettingsFragment();

        // Navigation view
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

        btn = findViewById(R.id.btn_setting);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSupportFragmentManager().beginTransaction().replace(R.id.container, settingFragment).commit();
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (!flag_permission_granted) {
            Toast.makeText(getApplicationContext(), "Permissions are not granted. Please try again", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Check if measurement is running
        if (active_fragment_idx == 0 && homeFragment.flag_measurement_running){
            Toast.makeText(getApplicationContext(), "Measurement is running. Please stop before navigating to another page", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (active_fragment_idx == 2 && serverFragment.flag_measurement_running){
            Toast.makeText(getApplicationContext(), "Measurement is running. Please stop before navigating to another page", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (active_fragment_idx == 3 && sensorFragment.flag_measurement_running){
            Toast.makeText(getApplicationContext(), "Measurement is running. Please stop before navigating to another page", Toast.LENGTH_SHORT).show();
            return false;
        }

        switch (item.getItemId()) {
            case R.id.home:
                getSupportFragmentManager().beginTransaction().replace(R.id.container, homeFragment).commit();
                active_fragment_idx = 0;
                return true;

            case R.id.wifi:
                getSupportFragmentManager().beginTransaction().replace(R.id.container, wifiFragment).commit();
                active_fragment_idx = 1;
                return true;

            case R.id.server:
                getSupportFragmentManager().beginTransaction().replace(R.id.container, serverFragment).commit();
                active_fragment_idx = 2;
                return true;

            case R.id.sensor:
                getSupportFragmentManager().beginTransaction().replace(R.id.container, sensorFragment).commit();
                active_fragment_idx = 3;
                return true;

            case R.id.ble:
                getSupportFragmentManager().beginTransaction().replace(R.id.container, bleFragment).commit();
                active_fragment_idx = 4;
                return true;

//            case R.id.settings:
//                getSupportFragmentManager().beginTransaction().replace(R.id.container, settingFragment).commit();
//                active_fragment_idx = 4;
//                return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for(int i = 0; i < grantResults.length; i++)
            if (grantResults[i] != PERMISSION_GRANTED) {
                return;
            }
        Log.d(TAG, "All permissions granted");
        flag_permission_granted = true;
        bottomNavigationView.setSelectedItemId(R.id.home);
    }
}