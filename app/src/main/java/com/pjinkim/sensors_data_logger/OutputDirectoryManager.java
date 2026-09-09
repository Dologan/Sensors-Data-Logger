package com.pjinkim.sensors_data_logger;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class OutputDirectoryManager {

    // properties
    private final static String LOG_TAG = OutputDirectoryManager.class.getName();

    private Context mContext;
    private String mOutputDirectory;


    // constructors
    public OutputDirectoryManager(Context context, final String prefix, final String suffix) throws FileNotFoundException {
        mContext = context;
        update(prefix, suffix);
    }

    public OutputDirectoryManager(Context context, final String prefix) throws FileNotFoundException {
        mContext = context;
        update(prefix);
    }

    public OutputDirectoryManager(Context context) throws FileNotFoundException {
        mContext = context;
        update();
    }


    // methods
    private void update(final String prefix, final String suffix) throws FileNotFoundException {

        // initialize folder name with current time information
        Calendar currentTime = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        File externalDirectory = mContext.getExternalFilesDir(null);
        String folderName = formatter.format(currentTime.getTime());

        // combine prefix and suffix
        if (prefix != null) {
            folderName = prefix + folderName;
        }
        if (suffix != null) {
            folderName = folderName + suffix;
        }

        // generate output directory folder
        File outputDirectory = new File(externalDirectory, folderName);
        if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdirs()) {
                Log.e(LOG_TAG, "update: Cannot create output directory.");
                throw new FileNotFoundException();
            }
        }
        mOutputDirectory = outputDirectory.getAbsolutePath();
        Log.i(LOG_TAG, "update: Output directory: " + outputDirectory.getAbsolutePath());
    }

    private void update(final String prefix) throws FileNotFoundException {
        update(prefix, null);
    }

    private void update() throws FileNotFoundException {
        update(null, null);
    }


    // getter and setter
    public String getOutputDirectory() {
        return mOutputDirectory;
    }
}
