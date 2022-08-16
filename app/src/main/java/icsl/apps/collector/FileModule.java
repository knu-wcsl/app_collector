package icsl.apps.collector;

import android.app.Activity;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import static android.os.SystemClock.elapsedRealtime;

public class FileModule {
    private File file;
    private Activity mActivity;
    private boolean is_file_created = false;

    FileModule(Activity activity, String filename){
        // Create file with filename
        mActivity = activity;
        create_file(filename);
    }

    FileModule(Activity activity, String filename, boolean append_date, boolean append_model_info, String extension) {
        // Create file with filename and additional options
        // append_date: append date and time to filename
        // append_model_info: append device model information to filename
        // extension: specify file extension (e.g., '.txt')

        String date = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        String model = Build.MODEL;

        if (append_model_info)
            filename += "_" + model;
        if (append_date)
            filename += "_" + date;
        filename += extension;

        mActivity = activity;
        create_file(filename);
    }

    private void create_file(String filename){
        File folder = new File(mActivity.getApplicationContext().getExternalFilesDir(null), "measured_data");
        if (!folder.exists())
            folder.mkdir();

        file = new File(folder, filename);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(mActivity.getApplicationContext(), "[WARNING] Unable to create file", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        is_file_created = true;

        // Put file headers
        String date = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        String header = "";
        header += "## Date: " + date + "\n";
        header += "## File creation time since boot (ms): " + elapsedRealtime() + "\n";
        header += "## Model: " + Build.MODEL + "\n";
        header += "## SDK version: " + Build.VERSION.SDK_INT + "\n";
        save_str_to_file(header);
    }

    public void save_str_to_file(String data){
        // save a single string to file
        FileOutputStream fos = null;
        try{
            fos = new FileOutputStream(file, true);
        } catch (FileNotFoundException e){
            e.printStackTrace();
            return;
        }
        try{
            try {
                fos.write(data.getBytes());
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void save_str_to_file(ArrayList<String> data){
        // save a list of strings to file
        FileOutputStream fos = null;
        try{
            fos = new FileOutputStream(file, true);
        } catch (FileNotFoundException e){
            e.printStackTrace();
            return;
        }
        try{
            try {
                for (int k = 0; k < data.size(); k++)
                    fos.write(data.get(k).getBytes());
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String get_filename(){
        return file.getAbsolutePath();
    }
}
