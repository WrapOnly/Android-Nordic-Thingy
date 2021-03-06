/*
 * Copyright (c) 2010 - 2017, Nordic Semiconductor ASA
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form, except as embedded into a Nordic
 *    Semiconductor ASA integrated circuit in a product or a software update for
 *    such product, must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. Neither the name of Nordic Semiconductor ASA nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * 4. This software, with or without modification, must only be used with a
 *    Nordic Semiconductor ASA integrated circuit.
 *
 * 5. Any software provided in binary form under this license must not be reverse
 *    engineered, decompiled, modified and/or disassembled.
 *
 * THIS SOFTWARE IS PROVIDED BY NORDIC SEMICONDUCTOR ASA "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY, NONINFRINGEMENT, AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NORDIC SEMICONDUCTOR ASA OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.nrfthingy.configuration;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.Space;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import no.nordicsemi.android.nrfthingy.MainActivity;
import no.nordicsemi.android.nrfthingy.R;
import no.nordicsemi.android.nrfthingy.common.PermissionRationaleDialogFragment;
import no.nordicsemi.android.nrfthingy.common.ScannerFragment;
import no.nordicsemi.android.nrfthingy.common.ScannerFragmentListener;
import no.nordicsemi.android.nrfthingy.common.Utils;
import no.nordicsemi.android.nrfthingy.database.DatabaseContract;
import no.nordicsemi.android.nrfthingy.database.DatabaseHelper;
import no.nordicsemi.android.nrfthingy.dfu.DfuUpdateAvailableDialogFragment;
import no.nordicsemi.android.nrfthingy.dfu.SecureDfuActivity;
import no.nordicsemi.android.nrfthingy.thingy.Thingy;
import no.nordicsemi.android.nrfthingy.thingy.ThingyService;
import no.nordicsemi.android.thingylib.ThingyListener;
import no.nordicsemi.android.thingylib.ThingyListenerHelper;
import no.nordicsemi.android.thingylib.ThingySdkManager;
import no.nordicsemi.android.thingylib.utils.ThingyUtils;

public class InitialConfigurationActivity extends AppCompatActivity implements ScannerFragmentListener,
        PermissionRationaleDialogFragment.PermissionDialogListener,
        ThingySdkManager.ServiceConnectionListener,
        CancelInitialConfigurationDialogFragment.CancleInitialConfigurationListener,
        DfuUpdateAvailableDialogFragment.DfuUpdateAvailableListener {

    private static final int SCAN_DURATION = 15000;
    private LinearLayout mThingyInfoContainer;
    private LinearLayout mDeviceNameContainer;
    private LinearLayout mSetupCompleteContainer;
    private LinearLayout mLocationServicesContainer;

    private TextInputEditText mDeviceInfo;
    private TextView mEnableLocationServices;

    private Button mConfirmThingy;
    private Button mConfirmDeviceName;
    private Button mSkipDeviceName;
    private Button mGetStarted;

    private TextView mStepOne;
    private TextView mStepTwo;
    private TextView mStepOneSummary;

    private View mView;
    private Space mSpace;

    private ScrollView mScrollView;

    private boolean mStepOneComplete;
    private boolean mStepTwoComplete;

    private String mDeviceName;
    private String mFirmwareFileVersion;

    private boolean mConfig;
    private BluetoothDevice mDevice;

    private DatabaseHelper mDatabaseHelper;

    private ThingySdkManager mThingySdkManager;

    private Handler mProgressHandler = new Handler();
    private ProgressDialog mProgressDialog;

    private ScannerFragment mScannerFragment;

    private BroadcastReceiver mLocationProviderChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final boolean enabled = isLocationEnabled();
            if(enabled){
                mLocationServicesContainer.setVisibility(View.GONE);
            } else {
                mLocationServicesContainer.setVisibility(View.VISIBLE);
            }

        }
    };

    private ThingyListener mThingyListener = new ThingyListener() {

        @Override
        public void onDeviceConnected(BluetoothDevice device, int connectionState) {
            if (device.equals(mDevice)) {
                updateProgressDialogState(getString(R.string.state_discovering_services, device.getName()));
                mStepOneSummary.setText("Status: Connected to : " + device.getName());
            }
        }

        @Override
        public void onDeviceDisconnected(BluetoothDevice device, int connectionState) {
            if (device.equals(mDevice)) {
                mStepOneSummary.setText(R.string.connect_thingy_summary);
                Utils.showToast(InitialConfigurationActivity.this, getString(R.string.thingy_disconnected, device.getName()));
            }
        }

        @Override
        public void onServiceDiscoveryCompleted(BluetoothDevice device) {
            if (device.equals(mDevice)) {
                mThingySdkManager.enableEnvironmentNotifications(device, true);
                hideProgressDialog();

                checkForFwUpdates();
            }
        }

        @Override
        public void onTemperatureValueChangedEvent(BluetoothDevice bluetoothDevice, String temperature) {
        }

        @Override
        public void onPressureValueChangedEvent(BluetoothDevice bluetoothDevice, final String pressure) {
        }

        @Override
        public void onHumidityValueChangedEvent(BluetoothDevice bluetoothDevice, final String humidity) {
        }

        @Override
        public void onAirQualityValueChangedEvent(BluetoothDevice bluetoothDevice, final int eco2, final int tvoc) {
        }

        @Override
        public void onColorIntensityValueChangedEvent(BluetoothDevice bluetoothDevice, final float red, final float green, final float blue, final float alpha) {
        }

        @Override
        public void onButtonStateChangedEvent(BluetoothDevice bluetoothDevice, int buttonState) {

        }

        @Override
        public void onTapValueChangedEvent(BluetoothDevice bluetoothDevice, int direction, int count) {

        }

        @Override
        public void onOrientationValueChangedEvent(BluetoothDevice bluetoothDevice, int orientation) {

        }

        @Override
        public void onQuaternionValueChangedEvent(BluetoothDevice bluetoothDevice, float w, float x, float y, float z) {

        }

        @Override
        public void onPedometerValueChangedEvent(BluetoothDevice bluetoothDevice, int steps, long duration) {

        }

        @Override
        public void onAccelerometerValueChangedEvent(BluetoothDevice bluetoothDevice, float x, float y, float z) {

        }

        @Override
        public void onGyroscopeValueChangedEvent(BluetoothDevice bluetoothDevice, float x, float y, float z) {

        }

        @Override
        public void onCompassValueChangedEvent(BluetoothDevice bluetoothDevice, float x, float y, float z) {

        }

        @Override
        public void onEulerAngleChangedEvent(BluetoothDevice bluetoothDevice, float roll, float pitch, float yaw) {

        }

        @Override
        public void onRotationMatixValueChangedEvent(BluetoothDevice bluetoothDevice, byte[] matrix) {

        }

        @Override
        public void onHeadingValueChangedEvent(BluetoothDevice bluetoothDevice, float heading) {

        }

        @Override
        public void onGravityVectorChangedEvent(BluetoothDevice bluetoothDevice, float x, float y, float z) {

        }

        @Override
        public void onSpeakerStatusValueChangedEvent(BluetoothDevice bluetoothDevice, int status) {

        }

        @Override
        public void onMicrophoneValueChangedEvent(BluetoothDevice bluetoothDevice, byte[] data) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_initial_configuration);

        //this line is required if needed to set drawables within the activity if they are vectors.
        //However setting a drawable as an ImageResource will not require this line
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        mThingySdkManager = ThingySdkManager.getInstance();

        final Toolbar mainToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mainToolbar);
        mainToolbar.setTitle(getString(R.string.initial_configuration));

        Intent intent = getIntent();
        mConfig = intent.getBooleanExtra(Utils.INITIAL_CONFIG_FROM_ACTIVITY, false);

        if (mConfig) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white);
        }

        mDatabaseHelper = new DatabaseHelper(this);
        mScannerFragment = ScannerFragment.getInstance(ThingyUtils.THINGY_BASE_UUID);

        mThingyInfoContainer = findViewById(R.id.thingy_container);
        mDeviceNameContainer = findViewById(R.id.device_name_container);
        mSetupCompleteContainer = findViewById(R.id.setup_complete_container);
        mLocationServicesContainer = findViewById(R.id.location_services_container);
        mEnableLocationServices = findViewById(R.id.enable_location_services);

        mScrollView = findViewById(R.id.scroll_view);

        mDeviceInfo = findViewById(R.id.device_name);

        mConfirmThingy = findViewById(R.id.confirm_thingy);
        mConfirmDeviceName = findViewById(R.id.confirm_device_name);
        mSkipDeviceName = findViewById(R.id.skip_device_name);
        mGetStarted = findViewById(R.id.get_started);

        mStepOne = findViewById(R.id.step_one);
        mStepTwo = findViewById(R.id.step_two);
        mStepOneSummary = findViewById(R.id.step_one_summary);
        mView = findViewById(R.id.vertical_line);
        mSpace = findViewById(R.id.space);

        mEnableLocationServices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });

        mStepOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateOnStepOneComplete();
            }
        });

        mStepTwo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateOnStepTwoComplete();
            }
        });

        mConfirmThingy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.checkIfVersionIsMarshmallowOrAbove()) {
                    if (ActivityCompat.checkSelfPermission(InitialConfigurationActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if(isLocationEnabled()) {
                            if (isBleEnabled()) {
                                final String title = mConfirmThingy.getText().toString().trim();
                                if (title.contains("Disconnect")) {
                                    mThingySdkManager.disconnectFromThingy(mDevice);
                                    mConfirmThingy.setText(R.string.scan_thingy);
                                }
                                mScannerFragment.show(getSupportFragmentManager(), null);
                            } else enableBle();
                        } else {
                            Utils.showToast(InitialConfigurationActivity.this, getString(R.string.location_services_disabled));
                        }
                    } else {
                        final PermissionRationaleDialogFragment dialog = PermissionRationaleDialogFragment.getInstance(Manifest.permission.ACCESS_COARSE_LOCATION, Utils.REQUEST_ACCESS_COARSE_LOCATION, getString(R.string.rationale_message_location));
                        dialog.show(getSupportFragmentManager(), null);
                    }
                } else {
                    if (isBleEnabled()) {
                        final String title = mConfirmThingy.getText().toString().trim();
                        if (title.contains("Disconnect")) {
                            mThingySdkManager.disconnectFromThingy(mDevice);
                        }
                        mScannerFragment.show(getSupportFragmentManager(), null);
                    } else enableBle();
                }
            }
        });

        mConfirmDeviceName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateStepTwo();
            }
        });

        mSkipDeviceName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animateStepTwo();
            }
        });

        mGetStarted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getStarted();
            }
        });

        mDeviceInfo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length() > 0)
                    mConfirmDeviceName.setEnabled(true);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (savedInstanceState != null) {
            mDevice = savedInstanceState.getParcelable(Utils.EXTRA_DEVICE);
            mStepOneComplete = savedInstanceState.getBoolean("Step1", false);
            mStepTwoComplete = savedInstanceState.getBoolean("Step2", false);

            if (mStepOneComplete) {
                mStepOneSummary.setText("Status: Connected to : " + mDevice.getName());
                mConfirmThingy.setText(R.string.disconnect_connect);
                animateStepOne(mDevice);
            }

            if (mStepTwoComplete) {
                animateStepTwo();
            }
        }

        registerReceiver(mLocationProviderChangedReceiver, new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(!isLocationEnabled()){
            mLocationServicesContainer.setVisibility(View.VISIBLE);
        }

        mThingySdkManager.bindService(this, ThingyService.class);
        ThingyListenerHelper.registerThingyListener(this, mThingyListener);
        registerReceiver(mBleStateChangedReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences sp = getSharedPreferences("APP_STATE", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("APP_STATE", isFinishing());
        editor.commit();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!isFinishing()) {
            if (mScannerFragment != null && mScannerFragment.isVisible()) {
                mScannerFragment.dismiss();
            }
        }

        mThingySdkManager.unbindService(this);
        ThingyListenerHelper.unregisterThingyListener(this, mThingyListener);
        unregisterReceiver(mBleStateChangedReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideProgressDialog();
        unregisterReceiver(mLocationProviderChangedReceiver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                handleOnBackPressed();
                break;
            case android.R.id.closeButton:
                onBackPressed();
                break;

        }
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(Utils.EXTRA_DEVICE, mDevice);
        outState.putBoolean("Step1", mStepOneComplete);
        outState.putBoolean("Step2", mStepTwoComplete);
    }

    private void handleOnBackPressed() {
        if (!Utils.isAppInitialisedBefore(this)) {
            mThingySdkManager.disconnectFromAllThingies();
            stopService(new Intent(InitialConfigurationActivity.this, ThingyService.class));
            super.onBackPressed();
        } else {
            CancelInitialConfigurationDialogFragment cancelInitialConfiguration = new CancelInitialConfigurationDialogFragment().newInstance(mDevice);
            cancelInitialConfiguration.show(getSupportFragmentManager(), null);
        }
    }

    @Override
    public void onBackPressed() {
        handleOnBackPressed();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Utils.REQUEST_ENABLE_BT:
                if (resultCode != RESULT_OK) {
                    if (mScannerFragment != null && mScannerFragment.isVisible()) {
                        mScannerFragment.dismiss();
                    }
                    finish();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermission(final String permission, final int requestCode) {
        ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
    }

    @Override
    public void onCancellingPermissionRationale() {
        Utils.showToast(this, getString(R.string.requested_permission_not_granted_rationale));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Utils.REQUEST_ACCESS_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!isBleEnabled()) {
                        enableBle();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.rationale_permission_denied), Toast.LENGTH_SHORT).show();
                }
                break;
            case Utils.REQUEST_ACCESS_FINE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isBleEnabled()) {
                        mScannerFragment.show(getSupportFragmentManager(), null);
                    } else {
                        enableBle();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.rationale_permission_denied), Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /**
     * Checks whether the Bluetooth adapter is enabled.
     */
    private boolean isBleEnabled() {
        final BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        final BluetoothAdapter ba = bm.getAdapter();
        return ba != null && ba.isEnabled();
    }

    /**
     * Tries to start Bluetooth adapter.
     */
    private void enableBle() {
        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, Utils.REQUEST_ENABLE_BT);
    }

    @Override
    public void onDeviceSelected(BluetoothDevice device, String name) {
        if (mThingySdkManager != null) {
            mThingySdkManager.connectToThingy(this, device, ThingyService.class);
        }

        animateStepOne(device);
        showConnectionProgressDialog();
    }

    @Override
    public void onNothingSelected() {

    }

    private void animateStepOne(final BluetoothDevice device) {
        mDevice = device;
        mStepOne.setText("");
        mStepOne.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(InitialConfigurationActivity.this, R.drawable.ic_done_white), null, null, null);
        mStepTwo.setBackground(ContextCompat.getDrawable(InitialConfigurationActivity.this, R.drawable.ic_blue_bg));
        mThingyInfoContainer.animate()
                .translationX(mThingyInfoContainer.getHeight())
                .alpha(0.0f)
                .setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mThingyInfoContainer.setVisibility(View.GONE);
                        mDeviceNameContainer.setVisibility(View.VISIBLE);

                        //Resetting the animation parameters, if not the views are not visible in case they are made visible
                        mThingyInfoContainer.setAlpha(1.0f);
                        mThingyInfoContainer.setTranslationX(0);
                        mThingyInfoContainer.clearAnimation();
                    }
                });

        mStepOneComplete = true;
    }

    private void animateStepTwo() {
        mStepTwoComplete = true;
        mStepTwo.setText("");
        mStepTwo.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(InitialConfigurationActivity.this, R.drawable.ic_done_white), null, null, null);

        mDeviceNameContainer.animate()
                .translationX(mDeviceNameContainer.getHeight())
                .alpha(0.0f)
                .setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mDeviceName = mDeviceInfo.getText().toString();
                        if (mDevice != null && !mDeviceName.isEmpty())
                            if (mThingySdkManager != null) {
                                mThingySdkManager.setDeviceName(mDevice, mDeviceName);
                            }
                        mDeviceNameContainer.setVisibility(View.GONE);
                        mView.setVisibility(View.GONE);
                        mSpace.setVisibility(View.GONE);

                        //Resetting the animation parameters, if not the views are not visible in case they are made visible
                        mDeviceNameContainer.setAlpha(1.0f);
                        mDeviceNameContainer.setTranslationX(0);
                        mDeviceNameContainer.clearAnimation();
                        mScrollView.fullScroll(View.FOCUS_DOWN);
                        mSetupCompleteContainer.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void animateOnStepOneComplete() {
        if (mStepOneComplete) {
            mThingyInfoContainer.setVisibility(View.VISIBLE);
            mConfirmThingy.setText(R.string.disconnect_connect);
            if (mDeviceNameContainer.getVisibility() == View.VISIBLE) {
                mDeviceNameContainer.animate()
                        .translationX(mDeviceNameContainer.getHeight())
                        .alpha(0.0f)
                        .setDuration(400)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                mDeviceNameContainer.setVisibility(View.GONE);
                                if (mSetupCompleteContainer.getVisibility() == View.VISIBLE)
                                    mSetupCompleteContainer.setVisibility(View.GONE);

                                //Resetting the animation parameters, if not the views are not visible in case they are made visible
                                mDeviceNameContainer.setAlpha(1.0f);
                                mDeviceNameContainer.setTranslationX(0);
                                mDeviceNameContainer.clearAnimation();
                            }
                        });
            } else {
                mSetupCompleteContainer.animate()
                        .translationY(mSetupCompleteContainer.getHeight())
                        .alpha(0.0f)
                        .setDuration(400)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                if (mSetupCompleteContainer.getVisibility() == View.VISIBLE)
                                    mSetupCompleteContainer.setVisibility(View.GONE);

                                //Resetting the animation parameters, if not the views are not visible in case they are made visible
                                mSetupCompleteContainer.setAlpha(1.0f);
                                mSetupCompleteContainer.setTranslationY(0);
                                mSetupCompleteContainer.clearAnimation();
                            }
                        });
            }
        }
    }

    private void animateOnStepTwoComplete() {
        if (mDevice != null) {
            final Thingy thingy = new Thingy(mDevice);
            if (Utils.isConnected(thingy, mThingySdkManager.getConnectedDevices())) {
                if (mStepOneComplete) {
                    if (mThingyInfoContainer.getVisibility() == View.VISIBLE) {
                        mThingyInfoContainer.animate()
                                .translationX(mThingyInfoContainer.getHeight())
                                .alpha(0.0f)
                                .setDuration(400)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        mDeviceNameContainer.setVisibility(View.VISIBLE);
                                        mThingyInfoContainer.setVisibility(View.GONE);
                                        if (mSetupCompleteContainer.getVisibility() == View.VISIBLE)
                                            mSetupCompleteContainer.setVisibility(View.GONE);

                                        //Resetting the animation parameters, if not the views are not visible in case they are made visible
                                        mThingyInfoContainer.setAlpha(1.0f);
                                        mThingyInfoContainer.setTranslationX(0);
                                        mThingyInfoContainer.clearAnimation();
                                    }
                                });
                    } else if (mDeviceNameContainer.getVisibility() == View.GONE) {
                        mDeviceNameContainer.setVisibility(View.VISIBLE);
                    } else if (mSetupCompleteContainer.getVisibility() == View.VISIBLE) {
                        mSetupCompleteContainer.animate()
                                .translationY(mSetupCompleteContainer.getHeight())
                                .alpha(0.0f)
                                .setDuration(400)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        mSetupCompleteContainer.setVisibility(View.GONE);

                                        //Resetting the animation parameters, if not the views are not visible in case they are made visible
                                        mSetupCompleteContainer.setAlpha(1.0f);
                                        mSetupCompleteContainer.setTranslationX(0);
                                        mSetupCompleteContainer.clearAnimation();
                                    }
                                });
                    }
                }
            } else {
                Utils.showToast(InitialConfigurationActivity.this, getString(R.string.no_thingy_connected_step_one));
            }
        } else {
            Utils.showToast(InitialConfigurationActivity.this, getString(R.string.no_thingy_connected_step_one));
        }
    }

    private void getStarted() {

        if (!Utils.isAppInitialisedBefore(this)) {
            SharedPreferences sp = getSharedPreferences(Utils.PREFS_INITIAL_SETUP, MODE_PRIVATE);
            sp.edit().putBoolean(Utils.INITIAL_CONFIG_STATE, true).commit();
        }

        final String address = mDevice.getAddress();
        final String deviceName = mDevice.getName();

        if (!mDatabaseHelper.isExist(address)) {
            if (mDeviceName == null || mDeviceName.isEmpty()) {
                mDeviceName = mDevice.getName();
            }
            mDatabaseHelper.insertDevice(address, mDeviceName);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_TEMPERATURE);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_PRESSURE);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_HUMIDITY);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_AIR_QUALITY);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_COLOR);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_BUTTON);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_QUATERNION);
            mThingySdkManager.setSelectedDevice(mDevice);
        }
        updateSelectionInDb(new Thingy(mDevice), true);

        if (!mConfig) {
            finish();
            Intent intent = new Intent(InitialConfigurationActivity.this, MainActivity.class);
            intent.putExtra(Utils.EXTRA_DEVICE, mDevice);
            startActivity(intent);
        } else {
            finish();
        }
    }

    private void updateSelectionInDb(final no.nordicsemi.android.nrfthingy.thingy.Thingy thingy, final boolean selected) {
        final ArrayList<no.nordicsemi.android.nrfthingy.thingy.Thingy> thingyList = mDatabaseHelper.getSavedDevices();
        for (int i = 0; i < thingyList.size(); i++) {
            if (thingy.getDeviceAddress().equals(thingyList.get(i).getDeviceAddress())) {
                mDatabaseHelper.setLastSelected(thingy.getDeviceAddress(), selected);
            } else {
                mDatabaseHelper.setLastSelected(thingyList.get(0).getDeviceAddress(), !selected);
            }
        }
    }

    @Override
    public void onServiceConnected() {

    }

    @Override
    public void cancleInitialConfiguration() {
        if (mThingySdkManager != null) {
            mThingySdkManager.disconnectFromThingy(mDevice);
        }
        super.onBackPressed();
    }

    final BroadcastReceiver mBleStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        enableBle();
                        break;
                }
            }
        }
    };

    private void showConnectionProgressDialog() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(getString(R.string.please_wait));
        mProgressDialog.setMessage(getString(R.string.state_connecting));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setCanceledOnTouchOutside(false);

        mProgressHandler.postDelayed(mProgressDialogRunnable, SCAN_DURATION);
        mProgressDialog.show();
    }

    final Runnable mProgressDialogRunnable = new Runnable() {
        @Override
        public void run() {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }
    };

    private void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressHandler.removeCallbacks(mProgressDialogRunnable);
            mProgressDialog.dismiss();
        }
    }

    private void updateProgressDialogState(String message) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.setMessage(message);
        }
    }

    private boolean checkIfFirmwareUpdateAvailable() {
        final String[] fwVersion = mThingySdkManager.getFirmwareVersion(mDevice).split("\\.");

        final int fwVersionMajor = Integer.parseInt(fwVersion[fwVersion.length - 3]);
        final int fwVersionMinor = Integer.parseInt(fwVersion[fwVersion.length - 2]);
        final int fwVersionPatch = Integer.parseInt(fwVersion[fwVersion.length - 1]);
        final String name = getResources().getResourceEntryName(R.raw.thingy_dfu_pkg_app_v1_1_0).replace("v", "");
        final String[] resourceEntryNames = name.split("_");

        final int fwFileVersionMajor = Integer.parseInt(resourceEntryNames[resourceEntryNames.length - 3]);
        final int fwFileVersionMinor = Integer.parseInt(resourceEntryNames[resourceEntryNames.length - 2]);
        final int fwFileVersionPatch = Integer.parseInt(resourceEntryNames[resourceEntryNames.length - 1]);

        mFirmwareFileVersion = resourceEntryNames[resourceEntryNames.length - 3] + "." +
                resourceEntryNames[resourceEntryNames.length - 2] + "." +
                resourceEntryNames[resourceEntryNames.length - 1];

        return fwFileVersionMajor > fwVersionMajor || fwFileVersionMinor > fwVersionMinor || fwFileVersionPatch > fwVersionPatch;

    }

    private void checkForFwUpdates() {
        if (checkIfFirmwareUpdateAvailable()) {
            DfuUpdateAvailableDialogFragment fragment = DfuUpdateAvailableDialogFragment.newInstance(mDevice, mFirmwareFileVersion);
            fragment.show(getSupportFragmentManager(), null);
            mFirmwareFileVersion = null;
        }
    }

    @Override
    public void onDfuRequested() {
        Intent intent = new Intent(this, SecureDfuActivity.class);
        intent.putExtra(Utils.EXTRA_DEVICE, mDevice);
        startActivity(intent);
    }

    /**
     * Since Marshmallow location services must be enabled in order to scan.
     * @return true on Android 6.0+ if location mode is different than LOCATION_MODE_OFF. It always returns true on Android versions prior to Marshmellow.
     */
    public boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (final Settings.SettingNotFoundException e) {
                // do nothing
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }
        return true;
    }
}
