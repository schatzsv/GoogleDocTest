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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class ScrollingActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    String TAG = "GoogleDocTest";

    // member variables used for logging
    GoogleApiClient mGoogleApiClient;
    DriveId mLogFolder;
    DriveFile mLogFile;
    DriveContents mLogFileContents;
    ParcelFileDescriptor mLogFilePFD;

    // timer
    int line;
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            String entry;
            line++;
            entry = "Line" + line + "," + System.currentTimeMillis() + "\n";
            logGoogle(entry);
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

        // get a google drive api client object
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .build();

        timerHandler.postDelayed(timerRunnable, 5000);
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

    // onConnected(), onConnectionFailed(), onConnectionSuspended() are the callbacks associated
    // with the google api object
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "onConnected()");
        // getFolder()starts the process of setting up log file, if folder does not exist
        // it is created and then the log file is created
        getFolder();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // An unresolvable error has occurred and a connection to Google APIs
        // could not be established. Display an error message, or handle
        // the failure silently
        Log.d(TAG, "onConnectionFailed()");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended()");
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
        if (id == R.id.action_settings) {
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

    String makeLogFileName() {
        return "LogFile" + System.currentTimeMillis();
    }

    void logGoogle(String l) {
        Log.d(TAG, "logGoogle() " + l);
        writeToLogFile(l);
    }

    /*
    getFolder() - get the folder that contains log file, creates if necessary
    createLogFile() - called by get folder to create log file (todo open existing if app continue)
    writeToLogFile(String) - open file, write data, commit and close
     */

    void getFolder() {
        Log.d(TAG, "getFolder()");
        Query query = new Query.Builder()
                .addFilter(Filters.and(
                        Filters.eq(SearchableField.TITLE, "GoogleDocTest"),
                        Filters.eq(SearchableField.TRASHED, false)))
                .build();
        Drive.DriveApi.query(mGoogleApiClient, query)
                .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                    @Override
                    public void onResult(DriveApi.MetadataBufferResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.d(TAG, "getFolder() query failed");
                            return;
                        }
                        for (Metadata m : result.getMetadataBuffer()) {
                            if (m.getTitle().equals("GoogleDocTest") && m.isFolder()) {
                                mLogFolder = m.getDriveId();
                                createLogFile();
                                return;
                            }
                        }
                        Log.d(TAG, "getFolder() creating log folder");
                        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                .setTitle("GoogleDocTest")
                                .build();
                        Drive.DriveApi.getRootFolder(mGoogleApiClient)
                                .createFolder(mGoogleApiClient, changeSet)
                                .setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                                    @Override
                                    public void onResult(DriveFolder.DriveFolderResult result) {
                                        if (!result.getStatus().isSuccess()) {
                                            Log.d(TAG, "getFolder() create log folder failed");
                                        } else {
                                            Log.d(TAG, "Created a folder");
                                            mLogFolder = result.getDriveFolder().getDriveId();
                                            createLogFile();
                                        }
                                    }
                                });


                    }
                });
    }

    void createLogFile() {
        Log.d(TAG, "createLogFile()");
        //create empty log file
        //todo check if a current logfile exists if restart
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
                        }
                    }
                });
    }

    void writeToLogFile(final String entry) {
        Log.d(TAG, "writeToLogFile()");
        mLogFile.open(mGoogleApiClient, DriveFile.MODE_READ_WRITE, null)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                    @Override
                    public void onResult(@NonNull DriveApi.DriveContentsResult driveContentsResult) {
                        if (!driveContentsResult.getStatus().isSuccess()) {
                            Log.d(TAG, "openLogFile() file create error");
                        } else {
                            mLogFileContents = driveContentsResult.getDriveContents();
                            mLogFilePFD = mLogFileContents.getParcelFileDescriptor();
                            try {
                                FileInputStream fileInputStream = new FileInputStream(mLogFilePFD.getFileDescriptor());
                                // Read to the end of the file.
                                fileInputStream.read(new byte[fileInputStream.available()]);
                                // Append to the file.
                                FileOutputStream fileOutputStream = new FileOutputStream(mLogFilePFD.getFileDescriptor());
                                Writer writer = new OutputStreamWriter(fileOutputStream);
                                writer.write(entry);
                                writer.close();
                            } catch (IOException e) {
                                Log.d(TAG, "writeToLogFile() IOException " + entry);
                            }
                            mLogFileContents.commit(mGoogleApiClient, null);
                        }
                    }
                });
    }
}
