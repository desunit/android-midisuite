package com.mobileer.miditools;

import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiSender;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.io.IOException;
import java.util.HashSet;

public class MidiOutputPortAutoSelector extends MidiManager.DeviceCallback {
    private int mType = MidiDeviceInfo.PortInfo.TYPE_OUTPUT;
    protected HashSet<MidiPortWrapper> mBusyPorts = new HashSet<MidiPortWrapper>();
    protected MidiManager mMidiManager;
    private MidiPortWrapper mCurrentWrapper;
    private MidiOutputPort mOutputPort;
    private MidiDispatcher mDispatcher = new MidiDispatcher();
    private MidiDevice mOpenDevice;

    public MidiOutputPortAutoSelector(MidiManager midiManager)
    {
        mMidiManager = midiManager;
        MidiDeviceMonitor.getInstance(mMidiManager).registerDeviceCallback(this,
                new Handler(Looper.getMainLooper()));

        MidiDeviceInfo[] infos = mMidiManager.getDevices();
        for (MidiDeviceInfo info : infos) {
            onDeviceAdded(info);
        }
    }

    private int getInfoPortCount(final MidiDeviceInfo info) {
        int portCount = (mType == MidiDeviceInfo.PortInfo.TYPE_INPUT)
                ? info.getInputPortCount() : info.getOutputPortCount();
        return portCount;
    }

    @Override
    public void onDeviceAdded(final MidiDeviceInfo info) {
        int portCount = getInfoPortCount(info);
        for (int i = 0; i < portCount; ++i) {
            MidiPortWrapper wrapper = new MidiPortWrapper(info, mType, i);
            Log.i(MidiConstants.TAG, wrapper + " was added to " + this);

            mCurrentWrapper = wrapper;
            onPortSelected(mCurrentWrapper);
            return;
        }
    }

    @Override
    public void onDeviceRemoved(final MidiDeviceInfo info) {
        int portCount = getInfoPortCount(info);
        for (int i = 0; i < portCount; ++i) {
            MidiPortWrapper wrapper = new MidiPortWrapper(info, mType, i);
            MidiPortWrapper currentWrapper = mCurrentWrapper;
            // If the currently selected port was removed then select no port.
            if (wrapper.equals(currentWrapper)) {
                //clearSelection();
            }
            Log.i(MidiConstants.TAG, wrapper + " was removed");
        }
    }

    public void onPortSelected(final MidiPortWrapper wrapper) {
        close();

        final MidiDeviceInfo info = wrapper.getDeviceInfo();
        if (info != null) {
            mMidiManager.openDevice(info, new MidiManager.OnDeviceOpenedListener() {

                @Override
                public void onDeviceOpened(MidiDevice device) {
                    if (device == null) {
                        Log.e(MidiConstants.TAG, "could not open " + info);
                    } else {
                        mOpenDevice = device;
                        mOutputPort = device.openOutputPort(wrapper.getPortIndex());
                        if (mOutputPort == null) {
                            Log.e(MidiConstants.TAG,
                                    "could not open output port for " + info);
                            return;
                        }
                        mOutputPort.connect(mDispatcher);
                    }
                }
            }, null);
            // Don't run the callback on the UI thread because openOutputPort might take a while.
        }
    }
    /**
     * Implement this method to clean up any open resources.
     */
    public void onClose() {
        try {
            if (mOutputPort != null) {
                mOutputPort.disconnect(mDispatcher);
            }
            mOutputPort = null;
            if (mOpenDevice != null) {
                mOpenDevice.close();
            }
            mOpenDevice = null;
        } catch (IOException e) {
            Log.e(MidiConstants.TAG, "cleanup failed", e);
        }
    }

    /**
     * Implement this method to clean up any open resources.
     */
    public void onDestroy() {
        MidiDeviceMonitor.getInstance(mMidiManager).unregisterDeviceCallback(this);
    }

    /**
     *
     */
    public void close() {
        onClose();
    }

    /**
     * You can connect your MidiReceivers to this sender. The user will then select which output
     * port will send messages through this MidiSender.
     * @return a MidiSender that will send the messages from the selected port.
     */
    public MidiSender getSender() {
        return mDispatcher.getSender();
    }

}
