package org.mccurley.first.ledbadge;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.CursorLoader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor>,
        AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener {
    private SimpleCursorAdapter adapter;
    private static final int REQUEST_CODE = 777;
    public static final String TEXT = "text";
    private TextView statusView;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothChannel mChannel;
    private static final int REQUEST_CONNECT = 2277;
    private static final int REQUEST_ENABLE_BT = 2278;
    private static final String TAG = "MainActivity";
    private String mConnectedDeviceName = null;

    // A handler to update the UI from the BluetoothChannel.
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChannel.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            break;
                        case BluetoothChannel.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChannel.STATE_STARTED:
                        case BluetoothChannel.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    setStatus("Wrote:" + writeMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(MainActivity.this, "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, msg.getData().getString(Constants.TOAST),
                            Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusView = (TextView) findViewById(R.id.status);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (toolbar != null) {
            toolbar.setTitle("This is the title");
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(MainActivity.this, InputActivity.class);
                    startActivityForResult(intent, REQUEST_CODE, null);
                }
            });
        }
        String[] fromColumns = {HistoryProvider.MESSAGE};
        int[] toViews = {android.R.id.text1};
        adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1, null,
                fromColumns, toViews, 0);
        ListView listView = (ListView) findViewById(R.id.history);
        if (listView != null) {
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(this);
            listView.setOnItemLongClickListener(this);
        }
        getLoaderManager().initLoader(0, null, this);
       mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
       if (mBluetoothAdapter == null) {
           Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_LONG).show();
           finish();
       }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else if (mChannel == null) {
            setupChannel();
        }
        setStatus(R.string.title_not_connected);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChannel != null) {
            mChannel.stop();
        }
    }

    private void sendMessage(String msg) {
        if (mChannel.getState() != BluetoothChannel.STATE_CONNECTED) {
            Toast.makeText(this, R.string.title_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (msg.length() > 0) {
            byte[] send = msg.getBytes();
            byte[] lenBytes = ByteBuffer.allocate(4).putInt(send.length).array();
            mChannel.write(lenBytes);
            mChannel.write(send);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    String value = data.getStringExtra(TEXT);
                    ContentValues values = new ContentValues();
                    values.put(HistoryProvider.MESSAGE, value);
                    values.put(HistoryProvider.LAST_MODIFIED, System.currentTimeMillis()/1000);
                    values.put(HistoryProvider.CREATION_TIME, System.currentTimeMillis()/1000);
                    getContentResolver().insert(HistoryProvider.CONTENT_URI, values);
                    adapter.notifyDataSetChanged();
                    sendMessage(value);
                }
                break;
            case REQUEST_CONNECT:
                if (resultCode == RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    if (address != null) {
                        connectDevice(address);
                    } else {
                        Toast.makeText(this, "Missing address", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    setupChannel();
                } else {
                    Toast.makeText(this, R.string.bt_not_enabled_exit, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Toast.makeText(this, "Unrecognized action", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void connectDevice(String address) {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mChannel.connect(device);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mChannel != null) {
            if (mChannel.getState() == BluetoothChannel.STATE_NONE) {
                mChannel.start();
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, final View view,
                            int position, long id) {
        Cursor c = (Cursor) parent.getItemAtPosition(position);
        Toast.makeText(this, c.getString(1), Toast.LENGTH_SHORT).show();
        sendMessage(c.getString(1));
        ContentValues values = new ContentValues();
        values.put(HistoryProvider.LAST_MODIFIED, System.currentTimeMillis()/1000);
        getContentResolver().update(ContentUris.withAppendedId(HistoryProvider.CONTENT_URI, c.getInt(0)),
                values, null, null);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Cursor c = (Cursor) parent.getItemAtPosition(position);
        final long itemId = c.getLong(0);
        final String itemString = c.getString(1);
        AlertDialog.Builder adb=new AlertDialog.Builder(MainActivity.this);
        adb.setTitle("Delete?");
        adb.setMessage("Are you sure you want to delete " + itemString);
        adb.setNegativeButton("Cancel", null);

        adb.setPositiveButton("Ok", new AlertDialog.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                getContentResolver().delete(ContentUris.withAppendedId(HistoryProvider.CONTENT_URI, itemId),
                        null, null);
            }});
        adb.show();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch(id) {
            case R.id.connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT);
                return true;
            }
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new android.content.CursorLoader(this,
                HistoryProvider.CONTENT_URI,
                HistoryProvider.HISTORY_PROJECTION,
                null,
                null,
                HistoryProvider.LAST_MODIFIED + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        adapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.swapCursor(null);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        statusView.setText(resId);
    }
    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        statusView.setText(subTitle);
    }

    private void setupChannel() {
        Log.d(TAG, "setupChannel");
        mChannel = new BluetoothChannel(this, mHandler);
    }
}
