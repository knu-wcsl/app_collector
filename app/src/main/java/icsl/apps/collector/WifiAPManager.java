package icsl.apps.collector;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class WifiAPManager {
    private Activity mActivity;
    private ArrayList<APInfo> aplist = new ArrayList<>();

    ArrayList<String> init_result = new ArrayList<>();
    private String filename;
    private final int mac_addr_len = 17; // 00:00:00:00:00:00

    private ArrayList<String> freq_change_list = new ArrayList<>();

    public WifiAPManager(Activity activity, String _filename, boolean flag_create_if_not_exist) {
        mActivity = activity;
        filename = _filename;
        File folder = new File(mActivity.getApplicationContext().getExternalFilesDir(null), "");
        File file = new File(folder, filename);
        if (!file.exists() && flag_create_if_not_exist){
            create_file(file);
        }
        try {
            InputStream inputStream = new FileInputStream(file);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                if (line.length() < 2)
                    continue;
                if (line.substring(0, 2).equals("##"))
                    continue;
                if (!add_access_point(line))
                    init_result.add(String.format("[Warning] Can not decode \"%s\" line in \"%s\" file", line, filename));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        init_result.add(String.format("Loaded %d FTMR(s) from \"%s\" file", aplist.size(), filename));
    }

    private void create_file(File file){
        Log.d("FILE", "CREATE");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file, false);
            fos.write("## FTMR list\n".getBytes());
            fos.write("## (line starting with ## will be ignored)\n".getBytes());
            fos.write("## GroupID,  SSID,  mac address,  frequency[MHz]\n".getBytes());
            fos.write("icsl, icsl_rtt_testbed, 28:bd:89:c3:01:7f, 5745\n".getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Toast.makeText(mActivity.getApplicationContext(), String.format("%s file created", filename), Toast.LENGTH_SHORT).show();
    }

    public ArrayList<String> get_init_result(){
        return init_result;
    }

    public boolean update_freq(String mac, int _freq, String _SSID){
        for (int k=0; k<aplist.size(); k++) {
            if (aplist.get(k).mac_addr.equals(mac)){
                aplist.get(k).is_active = true;
                aplist.get(k).SSID = _SSID;
                if (aplist.get(k).freq != _freq) {
                    freq_change_list.add(String.format("(%s, %d -> %d MHz)", aplist.get(k).mac_addr, aplist.get(k).freq, _freq));
                    aplist.get(k).freq = _freq;
                }
                return true;
            }
        }
        return false;
    }


    public ArrayList<APInfo> get_ap_list(){
        return aplist;
    }


    public String get_ftm_list_for_file_header(){
        String result = "## AP list in ftmr_list.txt file\n";
        for (int k=0; k < aplist.size(); k++)
            result += "## " + aplist.get(k).group + ", " + aplist.get(k).name + ", " + aplist.get(k).mac_addr + ", " + aplist.get(k).freq + "\n";
        return result;
    }


    public String get_ftmr_list() {
        String result = "";
        for (int k = 0; k < aplist.size(); k++) {
            if (aplist.get(k).is_active)
                result += "(active) ";
            else
                result += "(inactive) ";
            result += aplist.get(k).mac_addr + ", " + aplist.get(k).SSID + ", " + aplist.get(k).freq + "MHz\n";
        }
        return result;
    }


    public String update_ftmr_file(){
        if (freq_change_list.size() == 0)
            return null;

        String result = "Freq changed: ";
        for (int k=0; k<freq_change_list.size(); k++) {
            if (k > 0)
                result += ", ";
            result += freq_change_list.get(k);
        }

        freq_change_list.clear();
        return result;
    }


    private boolean add_access_point(String line){
        String[] items = line.split(", ");
        if (items.length < 4)
            return false;
        String group = items[0];
        String name = items[1];
        String mac = items[2];
        int freq = Integer.parseInt(items[3]);

        // Check mac address format
        if (mac.length() != mac_addr_len)
            return false;

        if (items.length == 4)
            aplist.add(new APInfo(group, name, mac, freq));
        else
            aplist.add(new APInfo(group, name, mac, freq, Boolean.getBoolean(items[5])));
        return true;
    }

    // class for AP information
    class APInfo {
        public String group;
        public String name;
        public String mac_addr;
        public int freq;
        public boolean flag_official;
        public boolean is_active = false;
        public String SSID = "";

        public APInfo(String _group, String _name, String _mac_addr, int _freq) {
            group = _group;
            name = _name;
            mac_addr = _mac_addr;
            freq = _freq;
        }

        public APInfo(String _group, String _name, String _mac_addr, int _freq, boolean _flag_official){
            group = _group;
            name = _name;
            mac_addr = _mac_addr;
            freq = _freq;
            flag_official = _flag_official;
        }
    }
}
