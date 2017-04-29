package com.schatzsv.googledoctest;

import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class ScrollingActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    String TAG = "GoogleDocTest";

    // timer that generates log data for testing
    int line;
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            String entry;
            line++;
            entry = "Line" + line + "," + System.currentTimeMillis() + "\n";
            Log.d(TAG, entry);
            appendLogCache(entry);
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mGoogleApiClient.isConnected()) {
                    Snackbar.make(view, "Connected", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();

                } else {
                    Snackbar.make(view, "Not connected", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();

                }
            }
        });

        // initialize Google Play Services API
        doInitGoogleApiClient();

        // open log file
        openLogCache();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, "onRestart()");
        super.onRestart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_start) {
            timerHandler.post(timerRunnable);
            return true;
        }
        if (id == R.id.action_stop) {
            timerHandler.removeCallbacks(timerRunnable);
            return true;
        }
        if (id == R.id.action_save) {
            saveLogToGoogleDrive();
            return true;
        }
        if (id == R.id.action_reset) {
            clearLogCache();
            return true;
        }
        if (id == R.id.action_checkconn) {
            boolean rv = mGoogleApiClient.isConnected();
            if (rv) {
                Toast.makeText(this, "Connected", Toast.LENGTH_LONG).show();
            }

            return true;
        }
        return super.onOptionsItemSelected(item);
    }



    /*
     * Fields and methods for using Google APIs from Google Play Services. Google Play Services
     * requires credentials via https://console.developers.google.com/apis/credentials, and a
     * app/build.gradle dependency compile 'com.google.android.gms:play-services-drive:10.2.1'
     */

    GoogleApiClient mGoogleApiClient;

    void doInitGoogleApiClient() {
        // get a google drive api client object
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .build();
    }

    // onConnected(), onConnectionFailed(), onConnectionSuspended() are the callbacks associated
    // with the google api object

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected()");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed()");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended()");
    }



    /*
     * Fields and methods for using google document APIs to log application data to a file.  Google
     * play services must be initialized first.
     */

    final String DIR_NAME = "GoogleDocTest";
    DriveId mLogFolder;
    DriveFile mLogFile;
    DriveContents mLogFileContents;
    ParcelFileDescriptor mLogFilePFD;
    File logToSave;

    String makeLogFileName() {
        return "LogFile" + System.currentTimeMillis() + ".csv";
    }

    void saveLog(File f) {
        logToSave = f;
        getGdrFolder();
    }

    // getGdrFolder() - get the folder that contains log file, creates if necessary
    // createLogFile() - called by get folder to create log file
    // writeToLogFile(String) - open file, get and write cache data, commit and close

    void getGdrFolder() {
        Query query = new Query.Builder()
                .addFilter(Filters.and(
                        Filters.eq(SearchableField.TITLE, DIR_NAME),
                        Filters.eq(SearchableField.TRASHED, false)))
                .build();
        Drive.DriveApi.query(mGoogleApiClient, query)
                .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                    @Override
                    public void onResult(@NonNull DriveApi.MetadataBufferResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.d(TAG, "getGdrFolder() query failed");
                            return;
                        }
                        for (Metadata m : result.getMetadataBuffer()) {
                            if (m.getTitle().equals(DIR_NAME) && m.isFolder()) {
                                mLogFolder = m.getDriveId();
                                createLogFile();
                                return;
                            }
                        }
                        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                .setTitle(DIR_NAME)
                                .build();
                        Drive.DriveApi.getRootFolder(mGoogleApiClient)
                                .createFolder(mGoogleApiClient, changeSet)
                                .setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                                    @Override
                                    public void onResult(@NonNull DriveFolder.DriveFolderResult result) {
                                        if (!result.getStatus().isSuccess()) {
                                            Log.d(TAG, "getFolder() create log folder failed");
                                        } else {
                                            mLogFolder = result.getDriveFolder().getDriveId();
                                            createLogFile();
                                        }
                                    }
                                });
                    }
                });
    }

    void createLogFile() {
        //create empty log file
        String fileName = makeLogFileName();
        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                .setTitle(fileName)
                .setMimeType("text/plain")
                .build();
        mLogFolder.asDriveFolder()
                .createFile(mGoogleApiClient, changeSet, null)
                .setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                    @Override
                    public void onResult(@NonNull DriveFolder.DriveFileResult driveFileResult) {
                        if (!driveFileResult.getStatus().isSuccess()) {
                            Log.d(TAG, "createLogFile() file create error");
                        } else {
                            mLogFile = driveFileResult.getDriveFile();
                            writeToLogFile();
                        }
                    }
                });
    }

    void writeToLogFile() {
        mLogFile.open(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                    @Override
                    public void onResult(@NonNull DriveApi.DriveContentsResult driveContentsResult) {
                        if (!driveContentsResult.getStatus().isSuccess()) {
                            Log.d(TAG, "openLogFile() file create error");
                        } else {
                            mLogFileContents = driveContentsResult.getDriveContents();
                            mLogFilePFD = mLogFileContents.getParcelFileDescriptor();
                            try {
                                FileOutputStream fileOutputStream = new FileOutputStream(mLogFilePFD.getFileDescriptor());
                                Writer writer = new OutputStreamWriter(fileOutputStream);
                                BufferedReader br = new BufferedReader(new FileReader(logToSave));
                                String line;
                                while ((line = br.readLine()) != null) {
                                    writer.write(line + "\n");
                                }
                                writer.close();
                            } catch (IOException e) {
                                Log.d(TAG, "writeToLogFile() IOException");
                            }
                            mLogFileContents.commit(mGoogleApiClient, null);
                        }
                    }
                });
    }

    /*
     * Fields and methods associated with internal storage. Internal storage is used to capture
     * data that is eventually saved to a Google Drive file. This implementation uses device cache.
     */

    String CACHE_FILE_NAME = "GoogleDocTestLog";
    File logFile;

    void openLogCache() {
        logFile = new File(getCacheDir(), CACHE_FILE_NAME);
    }

    void appendLogCache(String l) {
        try {
            FileOutputStream fos = new FileOutputStream(logFile, true);
            fos.write(l.getBytes());
            fos.close();
        }
        catch (FileNotFoundException e) {
            Log.d(TAG, "appendLogCache() cache file not found");
        }
        catch (IOException e) {
            Log.d(TAG, "appendLogCache() IO exception");
        }
    }

    void saveLogToGoogleDrive() {
        saveLog(logFile);
    }

    void clearLogCache() {
        if (logFile.delete()) {
            logFile = new File(getCacheDir(), CACHE_FILE_NAME);
        } else {
            Log.d(TAG, "clearLogCache() could not delete cache file");
        }
    }

}
