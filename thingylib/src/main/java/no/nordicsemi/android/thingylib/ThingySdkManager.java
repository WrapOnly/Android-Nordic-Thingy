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

package no.nordicsemi.android.thingylib;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.thingylib.dfu.DfuService;
import no.nordicsemi.android.thingylib.utils.ThingyUtils;
import no.nordicsemi.android.thingylib.BaseThingyService.BaseThingyBinder;

public class ThingySdkManager {

    private static ThingySdkManager mInstance;
    public ThingySdkManager mThingySdkManager;
    private ServiceConnectionListener mServiceConnectionListener;
    private BaseThingyService.BaseThingyBinder mBinder;

    /**
     * Creates a static instance of this class that could be used throughout the application lifecycle.
     */
    public static ThingySdkManager getInstance() {
        if (mInstance == null)
            mInstance = new ThingySdkManager();
        return mInstance;
    }

    /**
     * Clears the static instance of this class created by @link {@link #getInstance()} when the application is finishing.
     */
    public static ThingySdkManager clearInstance() {
        return mInstance = null;
    }

    private ThingySdkManager() {
        // empty constructor
    }

    /**
     * Service connection listener interface
     */
    public interface ServiceConnectionListener {
        /**
         * Called when the the service is connected to the activity.
         */
        void onServiceConnected();
    }

    /**
     * Service connection is maintained in this class
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = (BaseThingyService.BaseThingyBinder) service;
            if (mBinder != null) {
                mServiceConnectionListener.onServiceConnected();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(ThingyUtils.TAG, "unbound");
            mBinder = null;
        }
    };

    /**
     * Bind to the base service. This will start the BaseThingyService as a started service and then bind to it.
     * Implement @link {@link ServiceConnectionListener} in the activity to get the service @link {@link ServiceConnection onServiceConnected callbacks}
     *
     * @param context required context to call unbind from
     */
    public void bindService(final Context context, final Class<? extends BaseThingyService> service) {
        mServiceConnectionListener = (ServiceConnectionListener) context;
        final Intent intent = new Intent(context, service);
        context.startService(intent);
        context.bindService(intent, mServiceConnection, 0);
    }

    /**
     * Unbind from the base service
     * used to unbind the service from the activity when switching between activities or when the app goes to background
     *
     * @param context required context to call unbind from
     */
    public void unbindService(final Context context) {
        context.unbindService(mServiceConnection);
    }

    /**
     * Stop the background service
     * Ensure @link {@link #unbindService(Context)} is called first since the background service is not a bound service
     *
     * @param context required context to call unbind from
     * @param service to be stopped
     */
    public void stopService(final Context context, final Class<? extends BaseThingyService> service) {
        //Since we are stopping the service we must make sure to disconnect from all thingy devices
        disconnectFromAllThingies();

        //call stop service since the initial service was not a bound service
        Intent i = new Intent(context, service);
        context.stopService(i);
    }

    /**
     * Returns a Binder object for the specifie bluetooth device (Thingy)
     * Use this binder to access your own implementation after extending the @link {@link no.nordicsemi.android.thingylib.BaseThingyService.BaseThingyBinder}
     *
     */
    public BaseThingyBinder getThingyBinder(){
        return mBinder;//.getThingyServiceBinder();
    }

    /**
     * Returns a ThingyConnection object for the specifie bluetooth device (Thingy)
     *
     * @param device is the unique id for a ThingyConnection stored in a map
     */
    public ThingyConnection getThingyConnection(final BluetoothDevice device){
        return mBinder.getThingyConnection(device);
    }

    /**
     * Connects to a particular thingy. This method will start the thingy service and pass the requested device using the intent extras
     * This will connect the thingy and do a complete service discovery of all service and characteristics available on Thingy:52.
     *
     * @param context context
     * @param device  Bluetooth device to connect to
     * @param service service class
     */
    public void connectToThingy(final Context context, final BluetoothDevice device, final Class<? extends BaseThingyService> service) {
        final Intent intent = new Intent(context, service);
        intent.putExtra(ThingyUtils.EXTRA_DEVICE, device);
        context.startService(intent);
    }

    /**
     * Disconnect from all thingies
     */
    public void disconnectFromAllThingies() {
        if (mBinder != null) {
            mBinder.disconnectFromAllDevices();
        }
    }

    /**
     * Disconnects from a particular thingy
     *
     * @param device bluetooth device to disconnect from
     */
    public void disconnectFromThingy(final BluetoothDevice device) {
        if (mBinder != null) {
            final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
            if (thingyConnection != null) {
                thingyConnection.disconnect();
            }
        }
    }

    /**
     * Returns the connections state a device
     */
    public boolean isConnected(final BluetoothDevice device) {
        if(device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if(thingyConnection != null) {
                    return thingyConnection.getConnectionState();
                }
            }
        }
        return false;
    }



    /**
     * Selects the current bluetooth device and is stored in the base service
     *
     * @param device bluetooth device to be selected
     */
    public void setSelectedDevice(BluetoothDevice device) {
        if (mBinder != null) {
            if (device != null) {
                mBinder.setSelectedDevice(device);
            }
        }
    }

    /**
     * Returns the current bluetooth device which was selected from {@link #setSelectedDevice(BluetoothDevice)}.
     */
    public BluetoothDevice getSelectedDevice() {
        if (mBinder != null) {
            return mBinder.getSelectedDevice();
        }
        return null;
    }

    /**
     * Returns the list of connected devices
     */
    public List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        if (mBinder != null) {
            devices.addAll(mBinder.getConnectedDevices());
        }
        return devices;
    }

    /**
     * Configure a device name for the thingy which would be used for advertising
     *
     * @param device     bluetooth device
     * @param deviceName device name to be set
     */
    public void setDeviceName(final BluetoothDevice device, final String deviceName) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.setDeviceName(deviceName);
                }
            }
        }
    }

    /**
     * Returns the device name for the specific thingy
     */
    public String getDeviceName(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getDeviceName();
                }
            }
        }
        return null;
    }

    /**
     * Configures the environment characteristic for a particular thingy
     *
     * @param device bluetooth device
     * @param temperatureInterval in ms
     * @param pressureInterval in ms
     * @param humidityInterval in ms
     * @param colorIntensityInterval in ms
     * @param gasMode as an interval in ms
     */
    public boolean setEnvironmentConfigurationCharacteristic(final BluetoothDevice device, final int temperatureInterval, final int pressureInterval, final int humidityInterval, final int colorIntensityInterval, final int gasMode) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setEnvironmentConfigurationCharacteristic(temperatureInterval, pressureInterval, humidityInterval, colorIntensityInterval, gasMode);
                }
            }
        }
        return false;
    }

    /**
     * Configures Temperature interval
     *
     * @param device bluetooth device
     * @param interval in ms
     */
    public boolean setTemperatureInterval(final BluetoothDevice device, final int interval) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setTemperatureInterval(interval);
                }
            }
        }
        return false;
    }

    /**
     * Configures pressure interval
     *
     * @param device bluetooth device
     * @param interval in ms
     */
    public boolean setPressureInterval(final BluetoothDevice device, final int interval) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setPressureInterval(interval);
                }
            }
        }
        return false;
    }

    /**
     * Configure humidity interval
     *
     * @param device bluetooth device
     * @param interval in ms
     */
    public boolean setHumidityInteval(final BluetoothDevice device, final int interval) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setHumidityInterval(interval);
                }
            }
        }
        return  false;
    }

    /**
     * Configures the color intensity intervals for a particular thingy
     *
     * @param device bluetooth device
     * @param interval in ms
     */
    public boolean setColorIntensityInterval(final BluetoothDevice device, final int interval) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setColorIntensityInterval(interval);
                }
            }
        }
        return false;
    }

    /**
     * Configures the gas mode for a particular thingy
     *
     * @param device bluetooth device
     * @param gasMode mode
     */
    public void setGasMode(final BluetoothDevice device, final int gasMode) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.setGasMode(gasMode);
                }
            }
        }
    }

    /**
     * Returns the advertising interval for the particular thingy
     *
     * @param device bluetooth device
     */
    public int getAdvertisignIntervalUnits(final BluetoothDevice device) {
        if (device != null)
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getAdvertisingIntervalUnits();
                }
            }
        return -1;
    }

    /**
     * Returns the advertising interval timeout for the particular thingy
     *
     * @param device bluetooth device
     */
    public int getAdvertisignIntervalTimeoutUnits(final BluetoothDevice device) {
        if (device != null)
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getAdvertisingIntervalTimeoutUnits();
                }
            }
        return -1;
    }

    /**
     * Confgiures the advertising parameters for a particular thingy
     *
     * @param device bluetooth device
     * @param advertisingInterval in millisecond units
     * @param advertisingTimeout in millisecond units
     */
    public boolean setAdvertisingParameters(final BluetoothDevice device, final int advertisingInterval, final int advertisingTimeout) {
        if (device != null)
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setAdvertisingParameters(advertisingInterval, advertisingTimeout);
                }
            }
        return false;
    }

    /**
     * Confgiures the advertising parameters for a particular thingy
     *
     * @param device bluetooth device
     * @param advertisingInterval in millisecond units
     */
    public boolean setAdvertisingIntervalUnits(final BluetoothDevice device, final int advertisingInterval) {
        if (device != null)
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setAdvertisingIntervalUnits(advertisingInterval);
                }
            }
        return false;
    }

    /**
     * Confgiures the advertising parameters for a particular thingy
     *
     * @param device bluetooth device
     * @param advertisingTimeout in millisecond units
     */
    public boolean setAdvertisingTimeoutUnits(final BluetoothDevice device, final int advertisingTimeout) {
        if (device != null)
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setAdvertisingTimeoutUnits(advertisingTimeout);
                }
            }
        return false;
    }

    /**
     * Confgiures the Connection parameters for a particular thingy
     *
     * @param device bluetooth device
     * @param minConnectionIntervalUnits min connection interval
     * @param maxConnectionIntervalUnits max connection interval
     * @param slaveLatency
     * @param connectionSupervisionTimeout
     */
    public boolean setConnectionParameters(final BluetoothDevice device, final int minConnectionIntervalUnits, final int maxConnectionIntervalUnits,
                                           final int slaveLatency, final int connectionSupervisionTimeout) {
        if (device != null)
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setConnectionParameters(minConnectionIntervalUnits, maxConnectionIntervalUnits, slaveLatency, connectionSupervisionTimeout);
                }
            }
        return false;
    }

    /**
     * Sets the minimum connection interval units
     *
     * @param device bluetooth device
     * @param units connection interval in units
     */
    public boolean setMinimumConnectionIntervalUnits(final BluetoothDevice device, final int units) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setMinimumConnectionIntervalUnits(units);
                }
            }
        }
        return false;
    }

    /**
     * Returns the minimum connection interval units
     *
     * @param device bluetooth device
     */
    public int getMinimumConnectionIntervalUnits(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getMinimumConnectionIntervalUnits();
                }
            }
        }
        return -1;
    }

    /**
     * Sets the maximum connection interval units
     *
     * @param device bluetooth device
     * @param units connection interval in units
     */
    public boolean setMaximumConnectionIntervalUnits(final BluetoothDevice device, final int units) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setMaximumConnectionIntervalUnits(units);
                }
            }
        }
        return false;
    }


    /**
     * Returns the maximum connection interval for a particular thingy
     *
     * @param device bluetooth device
     */
    public int getMaximumConnectionIntervalUnits(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getMaximumConnectionIntervalUnits();
                }
            }
        }
        return -1;
    }

    public boolean setSlaveLatency(final BluetoothDevice device, final int slaveLatency) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setSlaveLatency(slaveLatency);
                }
            }
        }
        return false;
    }

    /**
     * Returns the slave latency for a particular thingy
     *
     * @param device bluetooth device
     */
    public int getSlaveLatency(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getSlaveLatency();
                }
            }
        }
        return -1;
    }

    public boolean setConnectionSupervisionTimeout(final BluetoothDevice device, final int supervisionTimeoutUnits) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setConnectionSupervisionTimeout(supervisionTimeoutUnits);
                }
            }
        }
        return false;
    }

    /**
     * Returns the connection supervision for a particular thingy
     *
     * @param device bluetooth device
     */
    public int getConnectionSupervisionTimeout(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getConnectionSupervisionTimeoutUnit();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the Eddystone URL for a particular thingy
     *
     * @param device bluetooth device
     */
    public String getEddystoneUrl(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getEddystoneUrl();
                }
            }
        }
        return null;
    }

    /**
     * Configures the Eddystone URL for a particular thingy
     *
     * @param device bluetooth device
     * @param url   eddystone url
     */
    public boolean setEddystoneUrl(final BluetoothDevice device, final String url) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setEddystoneUrl(url);
                }
            }
        }
        return false;
    }

    /**
     * Disables the Eddystone URL for a particular thingy
     *
     * @param device bluetooth device
     *
     */
    public boolean disableEddystoneUrl(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.disableEddystoneUrl();
                }
            }
        }
        return false;
    }

    /**
     * Returns the clound token for a particular thingy
     *
     * @param device bluetooth device
     */
    public String getCloudTokenData(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getCloudTokenData();
                }
            }
        }
        return null;
    }

    /**
     * Confgiures/saves the cloud token for a particular thingy
     *
     * @param device bluetooth device
     * @param cloudToken
     */
    public boolean setCloudToken(final BluetoothDevice device, final String cloudToken) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setCloudToken(cloudToken);
                }
            }
        }
        return false;
    }

    /**
     * Returns the clound token for a particular thingy
     *
     * @param device bluetooth device
     */
    public String getFirmwareVersion(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getFirmwareVersion();
                }
            }
        }
        return null;
    }

    /**
     * Toggle all Environment notifications for a particular thingy
     *
     * @param device bluetooth device
     * @param flag   notification state on/off
     */
    public void enableEnvironmentNotifications(final BluetoothDevice device, final boolean flag) {
        if (device != null) {
            Log.v(ThingyUtils.TAG, "BINDER: " + mBinder);
            if (mBinder != null) {
                //mBinder.enableEnvironmentNotifications(device, flag);
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableEnvironmentNotifications(flag);
                }
            }
        }
    }

    /**
     * Toggle Temperature notifcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableTemperatureNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableTemperatureNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Pressure notifcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enablePressureNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enablePressureNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Air Quality notifcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableAirQualityNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableAirQualityNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Humidity notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableHumidityNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableHumidityNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Temperature notifications for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableColorNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableColorNotifications(enable);
                }
            }
        }
    }

    /**
     * Returns the temperature interval for a particular thingy
     *
     * @param device bluetooth device
     */
    public int getEnvironmentTemperatureInterval(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getEnvironmentTemperatureInterval();
                }
            }
        }

        return -1;
    }

    /**
     * Returns the pressure interval for a particular thingy.
     *
     * @param device bluetooth device
     */
    public int getPressureInterval(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getPressureInterval();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the humidity interval for a particular thingy.
     *
     * @param device bluetooth device
     */
    public int getHumidityInterval(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getHumidityInterval();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the color intensity interval for a particular thingy.
     *
     * @param device bluetooth device
     */
    public int getColorIntensityInterval(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getColorIntensityInterval();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the gas mode for a particular thingy.
     *
     * @param device bluetooth device
     */
    public int getGasMode(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getGasMode();
                }
            }
        }
        return -1;
    }

    /**
     * Configures the Motion configuration characteristic for a particular thingy
     *
     * @param pedometerInterval in ms
     * @param temperatureCompensationInterval in ms
     * @param magnetoMeterCompensationInterval in ms
     * @param motionInterval in ms
     * @param wakeOnMotion as an interval in ms
     */
    public boolean setMotionConfigurationCharacteristic(final BluetoothDevice device, final int pedometerInterval, final int temperatureCompensationInterval, final int magnetoMeterCompensationInterval, final int motionInterval, final int wakeOnMotion) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setMotionConfigurationCharacteristic(pedometerInterval, temperatureCompensationInterval, magnetoMeterCompensationInterval, motionInterval, wakeOnMotion);
                }
            }
        }
        return false;
    }

    /**
     * Configures the pedometer interval for thingy
     *
     * @param device bluetooth device
     * @param interval in milliseconds
     */
    public boolean setPedometerInterval(final BluetoothDevice device, final int interval) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setPedometerInterval(interval);
                }
            }
        }
        return false;
    }

    /**
     * Configures the Temperature compensation interval for thingy
     *
     * @param device bluetooth device
     * @param interval in milliseconds
     */
    public boolean setTemperatureCompensationInterval(final BluetoothDevice device, final int interval) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setTemperatureCompensationInterval(interval);
                }
            }
        }
        return false;
    }

    /**
     * Configures the magnetometer compensation interval for thingy
     *
     * @param device bluetooth device
     * @param interval in milliseconds
     */
    public boolean setMagnetometerCompensationInterval(final BluetoothDevice device, final int interval) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setMagnetometerCompensationInterval(interval);
                }
            }
        }
        return false;
    }

    /**
     * Configures the motion processing frequency for thingy
     *
     * @param device bluetooth device
     * @param interval in milliseconds
     */
    public boolean setMotionProcessingFrequency(final BluetoothDevice device, final int interval) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setMotionProcessingFrequency(interval);
                }
            }
        }
        return false;
    }

    /**
     * Configures the wake on motion mode for thingy
     *
     * @param device bluetooth device
     * @param mode on/off where 1 is for on and 0 is off
     */
    public boolean setWakeOnMotion(final BluetoothDevice device, final int mode) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.setWakeOnMotion(mode);
                }
            }
        }
        return false;
    }

    /**
     * Toggle all Motion notifications for a particular thingy
     *
     * @param device bluetooth device
     * @param flag   notification on/off state
     */
    public void enableMotionNotifications(final BluetoothDevice device, final boolean flag) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableMotionNotifications(flag);
                }
            }
        }
    }

    /**
     * Returns the pedometer interval for a particular thingy
     *
     * @param device bluetooth device
     * @return pedometer interval
     */
    public int getPedometerInterval(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getPedometerInterval();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the tenperature interval for  the motion sensor in a particular thingy
     *
     * @param device bluetooth device
     * @return temperature interval of motion sensor
     */
    public int getMotionTemperatureInterval(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getMotionTemperatureInterval();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the compass interval for a particular thingy
     *
     * @param device bluetooth device
     * @return compass interval
     */
    public int getCompassInterval(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getCompassInterval();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the motion interval for a particular thingy
     *
     * @param device bluetooth device
     * @return motion interval
     */
    public int getMotionInterval(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getMotionInterval();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the wake on motion state for a particular thingy
     *
     * @param device bluetooth device
     * @return wake on motion state
     */
    public boolean getWakeOnMotionState(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getWakeOnMotionState();
                }
            }
        }
        return false;
    }

    /**
     * Toggle Raw Data notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableRawDataNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableRawDataNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Rotation Matrix data notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableRotationMatrixNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableRotationMatrixNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Orientation notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableOrientationNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableOrientationNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Heading notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableHeadingNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableHeadingNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Tap notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableTapNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableTapNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Quaternion notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableQuaternionNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableQuaternionNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Pedometer notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enablePedometerNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enablePedometerNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Gravity vector notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableGravityVectorNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableGravityVectorNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggle Euler notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableEulerNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableEulerNotifications(enable);
                }
            }
        }
    }

    /**
     * Toggles all UI notifications for a particular thingy
     *
     * @param device bluetooth device
     * @param flag   notification on/off state
     */
    public void enableUiNotifications(final BluetoothDevice device, final boolean flag) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableUiNotifications(flag);
                }
            }
        }
    }

    /**
     * Returns the LED mode of a particluar thingy
     *
     * @param device bluetooth device
     */
    public int getLedMode(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getLedMode();
                }
            }
        }
        return -1;
    }

    /**
     * Sets the LED color of a particluar thingy
     *
     * @param device  bluetooth device
     * @param redIntensity for the led
     * @param greenIntensity for the led
     * @param blueIntensity for the led
     */
    public void setConstantLedMode(final BluetoothDevice device, final int redIntensity, final int greenIntensity, final int blueIntensity) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.setConstantLedMode(redIntensity, greenIntensity, blueIntensity);
                }
            }
        }
    }

    /**
     * Sets the LED color of a particluar thingy
     *
     * @param device  bluetooth device
     * @param colorIndex to set the led color
     * @param intensity of the led
     * @param delay for LED breathe
     *
     */
    public void setBreatheLedMode(final BluetoothDevice device, final int colorIndex, final int intensity, final int delay) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.setBreatheLedMode(colorIndex, intensity, delay);
                }
            }
        }
    }

    /**
     * Sets the LED color of a particluar thingy
     *
     * @param device  bluetooth device
     * @param colorIndex to set the led color
     */
    public void setOneShotLedMode(final BluetoothDevice device, final int colorIndex, final int intensity) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.setOneShotLedMode(colorIndex, intensity);
                }
            }
        }
    }

    /**
     * Turns off the LED of a particluar thingy
     *
     * @param device  bluetooth device
     *
     */
    public void turnOffLed(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.turnOffLed();
                }
            }
        }
    }

    /**
     * Returns the LED color index of a particluar thingy
     *
     * @param device bluetooth device
     */
    public int getLedColorIndex(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getLedColorIndex();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the LED RGB intensity of a particluar thingy
     *
     * @param device bluetooth device
     */
    public int getLedRgbIntensity(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getLedRgbIntensity();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the LED color intensity of a particluar thingy
     *
     * @param device bluetooth device
     */
    public int getLedColorIntensity(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getLedColorIntensity();
                }
            }
        }
        return -1;
    }

    /**
     * Returns the breathe delay for LED color on a particluar thingy
     *
     * @param device bluetooth device
     */
    public int getLedColorBreatheDelay(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.getLedColorBreatheDelay();
                }
            }
        }
        return -1;
    }

    /**
     * Toggle Button state notifIcations for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableButtonStateNotification(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableButtonStateNotification(enable);
                }
            }
        }
    }

    public void requestMtu(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.requestMtu();
                }
            }
        }
    }

    /**
     * Toggles Sound notifications for a particular thingy
     *
     * @param device bluetooth device
     * @param flag   notification on/off state
     */
    public void enableSoundNotifications(final BluetoothDevice device, final boolean flag) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableSoundNotifications(flag);
                }
            }
        }
    }

    /**
     * Toggle Speaker notifications for a particluar thingy
     *
     * @param device           bluetooth device
     * @param enable notification on/off state
     */
    public void enableSpeakerStatusNotifications(final BluetoothDevice device, final boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableSpeakerStatusNotifications(enable);
                }
            }
        }
    }
    /**
     * Plays the selected frequency sample for a particluar thingy
     *
     * @param frequency to be played
     * @param duration to be played for
     * @param volume of the sound
     */
    public void playSoundFrequency(final Activity context, final BluetoothDevice device, final int frequency, final int duration, final int volume) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.playSoundFrequency(frequency, duration, volume);
                } else {
                    ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
                }
            } else {
                ThingyUtils.showToast(context, context.getString(R.string.service_not_bound));
            }
        } else {
            ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
        }
    }

    /**
     * Plays the selected sound sample for a particluar thingy
     *
     * @param device bluetooth device
     * @param sample sample sound to be played
     */
    public void playSoundSample(final Activity context, final BluetoothDevice device, final int sample) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.playSoundSample(sample);
                } else {
                    ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
                }
            } else {
                ThingyUtils.showToast(context, context.getString(R.string.service_not_bound));
            }
        } else {
            ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
        }
    }

    /**
     * Plays the selected sound sample for a particluar thingy
     * @param context activity context
     * @param device bluetooth device
     * @param audioFile to be played
     */
    public boolean playPcmSample(final Activity context, final BluetoothDevice device, final File audioFile){
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.setAudioStreamingInProgress(true);
                    return thingyConnection.playPcmAudio(audioFile);
                } else {
                    ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
                }
            } else {
                ThingyUtils.showToast(context, context.getString(R.string.service_not_bound));
            }
        } else {
            ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
        }
        return false;
    }

    /**
     * Plays the selected sound sample for a particluar thingy
     * @param context activity context
     * @param device bluetooth device
     * @param sample sample sound to be played
     */
    public void playPcmSample(final Activity context, final BluetoothDevice device, byte [] sample) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.setAudioStreamingInProgress(true);
                    thingyConnection.playPcmSample(sample);
                } else {
                    ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
                }
            } else {
                ThingyUtils.showToast(context, context.getString(R.string.service_not_bound));
            }
        } else {
            ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
        }
    }

    /**
     * Plays the selected sound sample for a particluar thingy
     *  @param device bluetooth device
     *
     */
    public void stopPcmSample(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.stopPcmSample();
                }
            }
        }
    }

    /**
     * Plays the selected sound sample for a particluar thingy
     * @param device bluetooth device
     * @param enable sound to be played
     */
    public void enableThingyMicrophone(final BluetoothDevice device, boolean enable) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.enableThingyMicrophoneNotifications(enable);
                }
            }
        }
    }

    /**
     * Plays the selected sound sample for a particluar thingy
     * @param context activity context
     * @param device bluetooth device
     * @param sample sample sound to be played
     */
    public void playVoiceInput(final Activity context, final BluetoothDevice device, byte [] sample) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    //thingyConnection.setAudioStreamingInProgress(true);
                    thingyConnection.playVoiceInput(sample);
                } else {
                    ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
                }
            } else {
                ThingyUtils.showToast(context, context.getString(R.string.service_not_bound));
            }
        } else {
            ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
        }
    }

    /**
     * Plays the selected sound sample for a particluar thingy
     * @param context activity context
     * @param device bluetooth device
     *
     */
    public void stopPlayingVoiceInput(final Activity context, final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    //thingyConnection.setAudioStreamingInProgress(true);
                    thingyConnection.stopPlayingVoiceInput();
                } else {
                    ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
                }
            } else {
                ThingyUtils.showToast(context, context.getString(R.string.service_not_bound));
            }
        } else {
            ThingyUtils.showToast(context, context.getString(R.string.no_thingy_connected));
        }
    }

    /**
     * Returns the saved time stamp and temperature values (K,V) for a particluar thingy.
     * These values are used to re-plot the graph in the case of an orientation change or application close and restart.
     * However if the application was killed by swiping these values will not be saved.
     *
     * @param device bluetooth device
     */
    public LinkedHashMap<String, String> getSavedTemperatureData(final BluetoothDevice device) {
        final LinkedHashMap<String, String> temperatureData = new LinkedHashMap<>();
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    temperatureData.putAll(thingyConnection.getSavedTemperatureData());
                    return temperatureData;
                }
            }
        }
        return temperatureData;
    }

    /**
     * Returns the saved time stamp and pressure values (K,V) for a particluar thingy.
     * These values are used to re-plot the graph in the case of an orientation change or application close and restart.
     * However if the application was killed by swiping these values will not be saved.
     *
     * @param device bluetooth device
     */
    public LinkedHashMap<String, String> getSavedPressureData(final BluetoothDevice device) {
        final LinkedHashMap<String, String> pressureData = new LinkedHashMap<>();
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    pressureData.putAll(thingyConnection.getSavedPressureData());
                    return pressureData;
                }
            }
        }
        return pressureData;
    }

    /**
     * Returns the saved time stamp and pressure values (K,V) for a particluar thingy.
     * These values are used to re-plot the graph in the case of an orientation change or application close and restart.
     * However if the application was killed by swiping these values will not be saved.
     *
     * @param device bluetooth device
     */
    public LinkedHashMap<String, Integer> getSavedHumidityData(final BluetoothDevice device) {
        final LinkedHashMap<String, Integer> humidityData = new LinkedHashMap<>();
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    humidityData.putAll(thingyConnection.getSavedHumidityData());
                    return humidityData;
                }
            }
        }
        return humidityData;
    }

    /**
     * Checks if there is a thingy already streaming audio
     *
     * @param bluetoothDevice to validated with
     */
    public boolean isAnotherThingyIsStreamingAudio(final BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice != null) {
            if (mBinder != null) {
                List<BluetoothDevice> connectedDevices = mBinder.getConnectedDevices();
                for (BluetoothDevice device : connectedDevices) {
                    if (!bluetoothDevice.equals(device)) {
                        final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                        if (thingyConnection != null && thingyConnection.isAudioStreamingInProgress()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if there is a thingy already streaming audio
     *
     * @param device to validated with
     */
    public boolean isThingyStreamingAudio(final BluetoothDevice device) {
        if(device != null) {
            if(mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null && thingyConnection.isAudioStreamingInProgress()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Trigger boot loader mode on the thingy to initiate DFU
     *
     * @param device bluetooth device
     */
    public void triggerBootLoaderMode(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    thingyConnection.triggerBootLoaderMode();
                }
            }
        }
    }

    /**
     * Checks if the thingy is in bootloader mode
     *
     * @param device bluetooth device
     */
    public boolean isInBootloaderMode(final BluetoothDevice device) {
        if (device != null) {
            if (mBinder != null) {
                final ThingyConnection thingyConnection = mBinder.getThingyConnection(device);
                if (thingyConnection != null) {
                    return thingyConnection.isInBootloaderMode();
                }
            }
        }
        return false;
    }

    /**
     * Start a dfu process with the nordic fw bundled in the thingy application.
     *
     *  @param context of the activity or fragment
     *  @param device to be updated
     *  @param fileType of the fw and zip file is used by default
     *
     */
    public boolean startDFUWithNordicFW(final Context context, final BluetoothDevice device, final int resFileId, final int fileType){
        final DfuServiceInitiator starter = new DfuServiceInitiator(device.getAddress())
                .setDeviceName(device.getName())
                .setKeepBond(false);
        // Init packet is required by Bootloader/DFU from SDK 7.0+ if HEX or BIN file is given above.
        // In case of a ZIP file, the init packet (a DAT file) must be included inside the ZIP file.
        if (fileType == DfuService.TYPE_AUTO) {
            starter.setZip(resFileId);
        }

        /*mDfuServiceController = */starter.start(context, DfuService.class);
        return true;
    }

    /**
     * Start a dfu process with a fw of user selection. Here the user will have to select the file and send the file path or the uri as a parameter
     *
     *  @param context
     *  @param device
     *  @param fileType of the fw and zip file is used by default
     *  @param mFilePath
     *  @param mFileStreamUri
     *
     */
    public boolean startDFUWithCustomFW(final Context context, final BluetoothDevice device, final int fileType, final String mFilePath, final Uri mFileStreamUri){
        final DfuServiceInitiator starter = new DfuServiceInitiator(device.getAddress())
                .setDeviceName(device.getName())
                .setKeepBond(false);
        // Init packet is required by Bootloader/DFU from SDK 7.0+ if HEX or BIN file is given above.
        // In case of a ZIP file, the init packet (a DAT file) must be included inside the ZIP file.
        if (fileType == DfuService.TYPE_AUTO) {
            starter.setZip(mFileStreamUri, mFilePath);
        } else {
            starter.setBinOrHex(fileType, mFileStreamUri, mFilePath);
        }

        /*mDfuServiceController = */starter.start(context, DfuService.class);
        return true;
    }
    /**
     * Checks the DFU service is already in progress
     *
     *  @param context of activity or fragment
     *
     */
    public boolean isDfuServiceRunning(final Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DfuService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
