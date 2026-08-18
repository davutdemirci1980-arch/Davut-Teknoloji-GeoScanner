package com.geoscanner.app.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.geoscanner.app.data.GradientData;
import com.geoscanner.app.utils.CryptoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Talks to the physical GPR/metal-detector device over either Classic
 * Bluetooth SPP or BLE, auto-detecting which. Understands three wire
 * protocols: plain delimited text lines ("G:123", "12,45", AES-encrypted
 * lines), a length/CRC-framed binary protocol used by "Zirve" branded
 * devices, and raw 16-bit little-endian values as a last resort.
 */
public class BLEManager {
    private static final String TAG = "BLEManager";

    // Zirve binary protocol framing/commands.
    private static final byte SOF = 15;
    private static final byte NCB = 3; // escape byte
    private static final byte EOF_BYTE = 4;
    private static final byte CMD_CONNECT = 1;
    private static final byte CMD_LIST_FILES = 2;
    private static final byte CMD_READ_FILE = 3;
    private static final byte CMD_DELETE_FILE = 4;
    private static final byte CMD_READ_DEVICE_ID = 5;
    private static final byte CMD_GET_SAMPLES = 6;
    private static final int MAX_RETRY = 3;

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_SERVICE_FFE0 = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CHAR_FFE1 = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_SERVICE_FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CHAR_FFF1 = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CHAR_FFF2 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID NORDIC_SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NORDIC_TX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NORDIC_RX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final String[] ZIRVE_NAMES = {"TE", "G-20"};

    // CRC-16/CCITT-FALSE table used to checksum outgoing Zirve packets.
    private static final int[] CRC_TABLE = {
            0, 4129, 8258, 12387, 16516, 20645, 24774, 28903, 33032, 37161, 41290, 45419, 49548, 53677, 57806, 61935,
            4657, 528, 12915, 8786, 21173, 17044, 29431, 25302, 37689, 33560, 45947, 41818, 54205, 50076, 62463, 58334,
            9314, 13379, 1056, 5121, 25830, 29895, 17572, 21637, 42346, 46411, 34088, 38153, 58862, 62927, 50604, 54669,
            13907, 9842, 5649, 1584, 30423, 26358, 22165, 18100, 46939, 42874, 38681, 34616, 63455, 59390, 55197, 51132,
            18628, 22757, 26758, 30887, 2112, 6241, 10242, 14371, 51660, 55789, 59790, 63919, 35144, 39273, 43274, 47403,
            23285, 19156, 31415, 27286, 6769, 2640, 14899, 10770, 56317, 52188, 64447, 60318, 39801, 35672, 47931, 43802,
            27814, 31879, 19684, 23749, 11298, 15363, 3168, 7233, 60846, 64911, 52716, 56781, 44330, 48395, 36200, 40265,
            32407, 28342, 24277, 20212, 15891, 11826, 7761, 3696, 65439, 61374, 57309, 53244, 48923, 44858, 40793, 36728,
            37256, 33193, 45514, 41451, 53516, 49453, 61774, 57711, 4224, 161, 12482, 8419, 20484, 16421, 28742, 24679,
            33721, 37784, 41979, 46042, 49981, 54044, 58239, 62302, 689, 4752, 8947, 13010, 16949, 21012, 25207, 29270,
            46570, 42443, 38312, 34185, 62830, 58703, 54572, 50445, 13538, 9411, 5280, 1153, 29798, 25671, 21540, 17413,
            42971, 47098, 34713, 38840, 59231, 63358, 50973, 55100, 9939, 14066, 1681, 5808, 26199, 30326, 17941, 22068,
            55628, 51565, 63758, 59695, 39368, 35305, 47498, 43435, 22596, 18533, 30726, 26663, 6336, 2273, 14466, 10403,
            52093, 56156, 60223, 64286, 35833, 39896, 43963, 48026, 19061, 23124, 27191, 31254, 2801, 6864, 10931, 14994,
            64814, 60687, 56684, 52557, 48554, 44427, 40424, 36297, 31782, 27655, 23652, 19525, 15522, 11395, 7392, 3265,
            61215, 65342, 53085, 57212, 44955, 49082, 36825, 40952, 28183, 32310, 20053, 24180, 11923, 16050, 3793, 7920,
    };

    public enum ConnectionMode { CLASSIC_SPP, BLE, NONE }

    public interface ConnectionListener {
        void onConnected(BluetoothDevice device);
        void onDisconnected();
        void onConnectionFailed(String reason);
    }

    public interface DeviceDiscoveryListener {
        void onDeviceFound(BluetoothDevice device, int rssi);
        void onScanFinished();
    }

    public interface GradientListener {
        void onGradientReceived(GradientData data);
    }

    public interface RawDataListener {
        void onRawDataReceived(String raw);
    }

    private static BLEManager instance;

    public static synchronized BLEManager getInstance() {
        if (instance == null) instance = new BLEManager();
        return instance;
    }

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic bleNotifyChar;
    private BluetoothGattCharacteristic bleWriteChar;
    private BluetoothSocket btSocket;
    private InputStream btInputStream;
    private OutputStream btOutputStream;
    private Thread sppReaderThread;
    private BluetoothDevice connectedDevice;
    private BluetoothDevice pendingDevice;

    private ConnectionMode connectionMode = ConnectionMode.NONE;
    private String detectedDeviceType = "unknown";
    private boolean isConnected = false;
    private boolean isScanning = false;
    private boolean isLiveMode = false;
    private int connectRetryCount = 0;
    private final ReentrantLock ioLock = new ReentrantLock();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private final List<BluetoothDevice> priorityDevices = new ArrayList<>();
    private StringBuilder dataBuffer = new StringBuilder();
    private final List<Byte> zirveBuffer = new ArrayList<>();
    private boolean inFrame = false;

    private volatile float lastGradientValue = 0f;
    private volatile long lastDataTime = 0;
    private volatile int totalDataReceived = 0;

    private ConnectionListener connectionListener;
    private DeviceDiscoveryListener discoveryListener;
    private GradientListener gradientListener;
    private RawDataListener rawDataListener;

    private BLEManager() {
    }

    public void initialize(Context appContext) {
        this.context = appContext.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) bluetoothAdapter = manager.getAdapter();
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    // ---- device discovery -------------------------------------------------

    public void startScan() {
        if (bluetoothAdapter == null || isScanning) return;
        discoveredDevices.clear();
        priorityDevices.clear();

        try {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice device : bonded) {
                    String name = safeName(device);
                    if (name != null && isZirveDevice(name)) {
                        priorityDevices.add(device);
                        discoveredDevices.add(device);
                        Log.i(TAG, "Eşleşmiş Zirve cihazı: " + name + " [" + device.getAddress() + "]");
                        if (discoveryListener != null) {
                            mainHandler.post(() -> discoveryListener.onDeviceFound(device, -30));
                        }
                    }
                }
                for (BluetoothDevice device : bonded) {
                    if (!discoveredDevices.contains(device)) {
                        discoveredDevices.add(device);
                        if (discoveryListener != null) {
                            mainHandler.post(() -> discoveryListener.onDeviceFound(device, -50));
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Bonded devices permission error", e);
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) {
            isScanning = true;
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
            try {
                scanner.startScan(null, settings, scanCallback);
            } catch (SecurityException e) {
                Log.e(TAG, "BLE scan error", e);
            }
            mainHandler.postDelayed(this::stopScan, 15000L);
        } else if (discoveryListener != null) {
            mainHandler.postDelayed(() -> discoveryListener.onScanFinished(), 500L);
        }
    }

    public void stopScan() {
        if (scanner != null && isScanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException ignored) {
            }
            isScanning = false;
        }
        if (discoveryListener != null) discoveryListener.onScanFinished();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (discoveredDevices.contains(device)) return;

            String name = safeName(device);
            if (name == null && result.getScanRecord() != null) {
                name = result.getScanRecord().getDeviceName();
            }
            discoveredDevices.add(device);
            if (name != null && isZirveDevice(name)) {
                priorityDevices.add(0, device);
            }
            if (discoveryListener != null) {
                mainHandler.post(() -> discoveryListener.onDeviceFound(device, result.getRssi()));
            }
        }
    };

    public boolean isZirveDevice(String name) {
        if (name == null) return false;
        for (String z : ZIRVE_NAMES) {
            if (name.contains(z)) return true;
        }
        String upper = name.toUpperCase();
        return upper.contains("ZIRVE") || upper.contains("ZRV") || upper.contains("4D") || upper.contains("GEO")
                || upper.contains("SCANNER") || upper.contains("GPR");
    }

    private String safeName(BluetoothDevice device) {
        try {
            return device.getName();
        } catch (SecurityException e) {
            return null;
        }
    }

    // ---- connection ---------------------------------------------------

    public void connect(BluetoothDevice device) {
        if (device == null) return;
        pendingDevice = device;
        connectRetryCount = 0;
        String name = safeName(device);

        if (name != null && isZirveDevice(name)) {
            detectedDeviceType = "zirve";
            connectClassicSPP(device);
        } else if (device.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC || device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL) {
            detectedDeviceType = "classic";
            connectClassicSPP(device);
        } else {
            detectedDeviceType = "ble";
            connectBLE(device);
        }
    }

    private void connectClassicSPP(BluetoothDevice device) {
        Log.i(TAG, "Klasik SPP bağlantısı deneniyor: " + device.getAddress());
        connectionMode = ConnectionMode.CLASSIC_SPP;
        new Thread(() -> runClassicSPPConnect(device)).start();
    }

    private void runClassicSPPConnect(BluetoothDevice device) {
        try {
            closeClassicConnection();
            btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException ignored) {
            }
            btSocket.connect();
            btOutputStream = btSocket.getOutputStream();
            btInputStream = btSocket.getInputStream();
            isConnected = true;
            connectedDevice = device;
            connectRetryCount = 0;
            Log.i(TAG, "SPP bağlantısı başarılı: " + device.getAddress());
            mainHandler.post(() -> notifyConnected(device));
            startSPPReader();
        } catch (SecurityException e) {
            Log.e(TAG, "SPP izin hatası", e);
            mainHandler.post(() -> {
                if (connectionListener != null) connectionListener.onConnectionFailed("İzin hatası");
            });
        } catch (IOException e) {
            Log.e(TAG, "SPP bağlantı hatası: " + e.getMessage());
            retryOrFallbackClassicConnect(device);
        }
    }

    private void retryOrFallbackClassicConnect(BluetoothDevice device) {
        try {
            closeClassicConnection();
            Log.i(TAG, "Fallback yöntemi deneniyor...");
            BluetoothSocket socket = (BluetoothSocket) device.getClass()
                    .getMethod("createRfcommSocket", Integer.TYPE).invoke(device, 1);
            btSocket = socket;
            socket.connect();
            btOutputStream = btSocket.getOutputStream();
            btInputStream = btSocket.getInputStream();
            isConnected = true;
            connectedDevice = device;
            Log.i(TAG, "Fallback SPP bağlantısı başarılı");
            mainHandler.post(() -> notifyConnected(device));
            startSPPReader();
        } catch (Exception e) {
            Log.e(TAG, "Fallback da başarısız: " + e.getMessage());
            connectRetryCount++;
            if (connectRetryCount > MAX_RETRY) {
                Log.i(TAG, "SPP başarısız, BLE deneniyor...");
                mainHandler.post(() -> connectBLE(device));
            } else {
                mainHandler.postDelayed(() -> connectClassicSPP(device), 1500L);
            }
        }
    }

    private void notifyConnected(BluetoothDevice device) {
        if (connectionListener != null) connectionListener.onConnected(device);
    }

    private void closeClassicConnection() {
        try {
            if (btInputStream != null) btInputStream.close();
        } catch (Exception ignored) {
        }
        try {
            if (btOutputStream != null) btOutputStream.close();
        } catch (Exception ignored) {
        }
        try {
            if (btSocket != null) btSocket.close();
        } catch (Exception ignored) {
        }
        btInputStream = null;
        btOutputStream = null;
        btSocket = null;
    }

    private void startSPPReader() {
        if (sppReaderThread != null && sppReaderThread.isAlive()) sppReaderThread.interrupt();
        sppReaderThread = new Thread(this::runSPPReaderLoop);
        sppReaderThread.setDaemon(true);
        sppReaderThread.start();
    }

    private void runSPPReaderLoop() {
        byte[] buffer = new byte[4096];
        Log.i(TAG, "SPP reader thread başladı");
        while (isConnected && btInputStream != null) {
            try {
                int available = btInputStream.available();
                if (available > 0) {
                    int bytesRead = btInputStream.read(buffer, 0, Math.min(available, buffer.length));
                    if (bytesRead > 0) {
                        byte[] data = Arrays.copyOf(buffer, bytesRead);
                        Log.d(TAG, "SPP veri alındı: " + bytesRead + " byte, hex=" + bytesToHex(data, Math.min(bytesRead, 20)));
                        totalDataReceived += bytesRead;
                        processReceivedData(data);
                    }
                } else {
                    Thread.sleep(30L);
                }
            } catch (IOException e) {
                Log.e(TAG, "SPP okuma hatası: " + e.getMessage());
                isConnected = false;
                mainHandler.post(() -> {
                    if (connectionListener != null) connectionListener.onDisconnected();
                });
            } catch (InterruptedException ignored) {
            }
        }
        Log.i(TAG, "SPP reader thread bitti");
    }

    private String bytesToHex(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len && i < bytes.length; i++) {
            sb.append(String.format("%02X ", bytes[i] & 0xFF));
        }
        return sb.toString().trim();
    }

    private void connectBLE(BluetoothDevice device) {
        Log.i(TAG, "BLE bağlantısı deneniyor: " + device.getAddress());
        connectionMode = ConnectionMode.BLE;
        try {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            mainHandler.postDelayed(() -> {
                if (!isConnected && bluetoothGatt != null) {
                    connectRetryCount++;
                    if (connectRetryCount <= MAX_RETRY) {
                        connectBLE(device);
                    } else if (connectionListener != null) {
                        connectionListener.onConnectionFailed("BLE bağlantı timeout");
                    }
                }
            }, 10000L);
        } catch (SecurityException e) {
            Log.e(TAG, "BLE connect error", e);
            if (connectionListener != null) connectionListener.onConnectionFailed("İzin hatası");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                isConnected = true;
                connectedDevice = gatt.getDevice();
                mainHandler.postDelayed(() -> {
                    try {
                        if (bluetoothGatt != null) bluetoothGatt.discoverServices();
                    } catch (SecurityException ignored) {
                    }
                }, 500L);
                return;
            }
            if (newState == BluetoothGatt.STATE_DISCONNECTED && (isConnected || connectRetryCount >= MAX_RETRY)) {
                isConnected = false;
                connectedDevice = null;
                mainHandler.post(() -> {
                    if (connectionListener != null) connectionListener.onDisconnected();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            boolean found = tryBLEService(gatt, BLE_SERVICE_FFF0, BLE_CHAR_FFF1, BLE_CHAR_FFF2);
            if (!found) found = tryBLEService(gatt, NORDIC_SERVICE, NORDIC_TX, NORDIC_RX);
            if (!found) found = tryBLEService(gatt, BLE_SERVICE_FFE0, BLE_CHAR_FFE1, BLE_CHAR_FFE1);
            if (!found) tryAllBLEServices(gatt);
            mainHandler.post(() -> {
                if (connectionListener != null) connectionListener.onConnected(connectedDevice);
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) processReceivedData(data);
        }
    };

    private boolean tryBLEService(BluetoothGatt gatt, UUID serviceUuid, UUID notifyUuid, UUID writeUuid) {
        BluetoothGattService service = gatt.getService(serviceUuid);
        if (service == null) return false;

        BluetoothGattCharacteristic notifyChar = service.getCharacteristic(notifyUuid);
        if (notifyChar == null) {
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                    notifyChar = c;
                    break;
                }
            }
        }
        if (notifyChar != null) {
            enableBLENotification(gatt, notifyChar);
            bleNotifyChar = notifyChar;
        }

        BluetoothGattCharacteristic writeChar = service.getCharacteristic(writeUuid);
        if (writeChar == null) {
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                    writeChar = c;
                    break;
                }
            }
        }
        bleWriteChar = writeChar;
        return bleNotifyChar != null;
    }

    private boolean tryAllBLEServices(BluetoothGatt gatt) {
        for (BluetoothGattService service : gatt.getServices()) {
            String uuid = service.getUuid().toString().substring(0, 8);
            if (uuid.equals("00001800") || uuid.equals("00001801") || uuid.equals("0000180a")) continue;
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0 && bleNotifyChar == null) {
                    enableBLENotification(gatt, c);
                    bleNotifyChar = c;
                }
                if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0 && bleWriteChar == null) {
                    bleWriteChar = c;
                }
            }
            if (bleNotifyChar != null) return true;
        }
        return false;
    }

    private void enableBLENotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                boolean indicate = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                descriptor.setValue(indicate
                        ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
            gatt.requestMtu(512);
        } catch (SecurityException ignored) {
        }
    }

    public void disconnect() {
        isConnected = false;
        isLiveMode = false;
        connectedDevice = null;
        if (sppReaderThread != null) sppReaderThread.interrupt();
        closeClassicConnection();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (Exception ignored) {
            }
            bluetoothGatt = null;
        }
        bleWriteChar = null;
        bleNotifyChar = null;
        connectionMode = ConnectionMode.NONE;
    }

    // ---- outgoing commands ----------------------------------------------

    public void sendCommand(String command) {
        if (!isConnected) return;
        if (connectionMode == ConnectionMode.CLASSIC_SPP) {
            sendSPP(command.getBytes(StandardCharsets.UTF_8));
        } else if (connectionMode == ConnectionMode.BLE && bleWriteChar != null && bluetoothGatt != null) {
            try {
                bleWriteChar.setValue(command.getBytes(StandardCharsets.UTF_8));
                bluetoothGatt.writeCharacteristic(bleWriteChar);
            } catch (SecurityException ignored) {
            }
        }
    }

    public void sendSPP(byte[] data) {
        if (btOutputStream == null) return;
        new Thread(() -> {
            ioLock.lock();
            try {
                btOutputStream.write(data);
                btOutputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "SPP yazma hatası", e);
            } finally {
                ioLock.unlock();
            }
        }).start();
    }

    public void sendZirvePacket(byte[] packetData, int size) {
        if (connectionMode != ConnectionMode.CLASSIC_SPP || btOutputStream == null) return;
        new Thread(() -> {
            ioLock.lock();
            try {
                btOutputStream.write(new byte[]{SOF});
                btOutputStream.write(new byte[]{SOF});
                for (int i = 0; i < size; i++) btOutputStream.write(new byte[]{packetData[i]});
                btOutputStream.write(new byte[]{EOF_BYTE});
                btOutputStream.flush();
                Log.d(TAG, "Zirve paket gönderildi: " + size + " byte");
            } catch (IOException e) {
                Log.e(TAG, "Zirve paket gönderme hatası", e);
            } finally {
                ioLock.unlock();
            }
        }).start();
    }

    public void sendZirveConnect(int sampleMode, int sensorMode, int numPaths, int numSamples) {
        byte[] packet = new byte[1024];
        int pos = 0;
        pos = addToPacket(packet, pos, CMD_CONNECT);
        pos = addToPacket(packet, pos, (byte) sampleMode);
        pos = addToPacket(packet, pos, (byte) Math.min(sampleMode, 2));
        pos = addToPacket(packet, pos, (byte) sensorMode);
        pos = addToPacket(packet, pos, (byte) 0);
        pos = addToPacket(packet, pos, (byte) 0);
        pos = addToPacket(packet, pos, (byte) 0);
        pos = addToPacket(packet, pos, (byte) 4);
        pos = addToPacket(packet, pos, (byte) numPaths);
        pos = addToPacket(packet, pos, (byte) (numSamples & 0xFF));
        pos = addToPacket(packet, pos, (byte) ((numSamples >> 8) & 0xFF));
        int crc = calcPacketCRC(packet, pos);
        pos = addToPacket(packet, pos, (byte) (crc & 0xFF));
        pos = addToPacket(packet, pos, (byte) ((crc >> 8) & 0xFF));
        sendZirvePacket(packet, pos);
    }

    public void sendZirveGetSamples(int sampleMode) {
        byte[] packet = new byte[1024];
        int pos = 0;
        pos = addToPacket(packet, pos, CMD_GET_SAMPLES);
        pos = addToPacket(packet, pos, (byte) sampleMode);
        pos = addToPacket(packet, pos, (byte) 0);
        pos = addToPacket(packet, pos, (byte) 0);
        pos = addToPacket(packet, pos, (byte) 0);
        pos = addToPacket(packet, pos, (byte) 0);
        int crc = calcPacketCRC(packet, pos);
        pos = addToPacket(packet, pos, (byte) (crc & 0xFF));
        pos = addToPacket(packet, pos, (byte) ((crc >> 8) & 0xFF));
        sendZirvePacket(packet, pos);
    }

    /** Byte-stuffs SOF/NCB/EOF control bytes so they never appear literally in the payload. */
    private int addToPacket(byte[] packet, int pos, byte data) {
        if (data == SOF || data == NCB || data == EOF_BYTE) {
            packet[pos++] = NCB;
        }
        packet[pos] = data;
        return pos + 1;
    }

    private int calcPacketCRC(byte[] packet, int size) {
        int crc = 0;
        int i = 0;
        while (i < size) {
            if (packet[i] == NCB) i++;
            if (i < size) {
                crc = (CRC_TABLE[(packet[i] ^ (crc >> 8)) & 0xFF] ^ (crc << 8)) & 0xFFFF;
            }
            i++;
        }
        return crc;
    }

    // ---- incoming data ----------------------------------------------------

    public void processReceivedData(byte[] rawData) {
        if (rawData == null || rawData.length == 0) return;
        Log.d(TAG, "Raw data: " + rawData.length + " bytes, first=" + (rawData[0] & 0xFF) + " isLive=" + isLiveMode + " type=" + detectedDeviceType);

        if (connectionMode == ConnectionMode.CLASSIC_SPP) {
            processClassicSPPData(rawData);
        } else {
            processBLEStreamData(rawData);
        }
    }

    private void processClassicSPPData(byte[] rawData) {
        String textData = new String(rawData, StandardCharsets.UTF_8).trim();
        boolean isText = true;
        for (byte b : rawData) {
            int ub = b & 0xFF;
            if (ub == SOF || ub == EOF_BYTE) {
                isText = false;
                break;
            }
        }

        if (isText && !textData.isEmpty()) {
            Log.d(TAG, "SPP text data: '" + textData + "'");
            if (rawDataListener != null) mainHandler.post(() -> rawDataListener.onRawDataReceived(textData));

            for (String rawLine : textData.split("[\\r\\n]+")) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                for (String rawPart : line.split("[,;|\\t\\s]+")) {
                    String part = rawPart.trim().replaceAll("[^0-9.\\-eE]", "");
                    if (part.isEmpty() || part.equals("-") || part.equals(".")) continue;
                    try {
                        float val = Float.parseFloat(part);
                        emitGradient(new GradientData(val));
                        return;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        boolean hasBinaryFrame = false;
        for (byte b : rawData) {
            if (b == SOF) {
                hasBinaryFrame = true;
                break;
            }
        }
        if (hasBinaryFrame) {
            Log.d(TAG, "Binary frame algılandı, Zirve binary parse deneniyor");
            processZirveBinaryData(rawData);
            return;
        }

        if (rawData.length >= 2) {
            int val16 = (rawData[0] & 0xFF) | ((rawData[1] & 0xFF) << 8);
            if (val16 > 0) {
                Log.d(TAG, "Raw 16-bit value: " + val16);
                emitGradient(new GradientData((float) val16));
            }
        }
    }

    private void processBLEStreamData(byte[] rawData) {
        String data = new String(rawData, StandardCharsets.UTF_8).trim();
        if (data.isEmpty()) return;
        if (rawDataListener != null) mainHandler.post(() -> rawDataListener.onRawDataReceived(data));

        dataBuffer.append(data);
        String buffered = dataBuffer.toString();
        String[] lines;
        if (buffered.contains("\n") || buffered.contains("\r")) {
            String[] split = buffered.split("[\\r\\n]+");
            if (buffered.endsWith("\n") || buffered.endsWith("\r")) {
                dataBuffer = new StringBuilder();
                lines = split;
            } else {
                dataBuffer = new StringBuilder(split[split.length - 1]);
                lines = Arrays.copyOf(split, split.length - 1);
            }
        } else if (buffered.length() <= 100) {
            mainHandler.postDelayed(() -> {
                if (dataBuffer.length() > 0) {
                    String pending = dataBuffer.toString().trim();
                    dataBuffer = new StringBuilder();
                    if (!pending.isEmpty()) processTextLine(pending);
                }
            }, 300L);
            return;
        } else {
            lines = new String[]{buffered};
            dataBuffer = new StringBuilder();
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) processTextLine(trimmed);
        }
    }

    private void emitGradient(GradientData gd) {
        lastGradientValue = gd.getGradient();
        lastDataTime = System.currentTimeMillis();
        if (gradientListener != null) mainHandler.post(() -> gradientListener.onGradientReceived(gd));
    }

    private void processZirveBinaryData(byte[] data) {
        for (byte b : data) {
            if (b == SOF) {
                inFrame = true;
                zirveBuffer.clear();
            } else if (b == EOF_BYTE && inFrame) {
                inFrame = false;
                processZirveFrame();
            } else if (inFrame) {
                zirveBuffer.add(b);
            }
        }
    }

    private void processZirveFrame() {
        if (zirveBuffer.size() < 3) return;

        byte[] decoded = new byte[zirveBuffer.size()];
        int dLen = 0;
        int i = 0;
        while (i < zirveBuffer.size()) {
            byte b = zirveBuffer.get(i);
            if (b == NCB && i + 1 < zirveBuffer.size()) {
                i++;
                decoded[dLen++] = zirveBuffer.get(i);
            } else {
                decoded[dLen++] = b;
            }
            i++;
        }

        byte cmd = decoded[0];
        Log.d(TAG, "Zirve frame: cmd=" + cmd + " len=" + dLen + " hex=" + bytesToHex(decoded, Math.min(dLen, 30)));

        if (rawDataListener != null) {
            StringBuilder hex = new StringBuilder();
            for (int j = 0; j < dLen; j++) hex.append(String.format("%02X ", decoded[j]));
            String hexStr = "ZRV:" + hex;
            mainHandler.post(() -> rawDataListener.onRawDataReceived(hexStr));
        }

        if (cmd == CMD_GET_SAMPLES && dLen > 4) {
            if (dLen > 6) {
                int dataSize = ((decoded[4] & 0xFF) + ((decoded[5] & 0xFF) << 8)) * 4;
                for (int idx = 6; idx + 3 < dLen && idx < dataSize + 6; idx += 4) {
                    double value = (decoded[idx + 1] & 0xFF) + (decoded[idx + 2] & 0xFF) * 256.0;
                    Log.d(TAG, "Zirve sinyal: " + value);
                    emitGradient(new GradientData((float) value));
                }
            } else if (dLen > 2) {
                for (int idx = 2; idx + 1 < dLen - 2; idx += 2) {
                    int val = (decoded[idx] & 0xFF) | ((decoded[idx + 1] & 0xFF) << 8);
                    if (val > 0) {
                        Log.d(TAG, "Zirve kısa sinyal: " + val);
                        emitGradient(new GradientData((float) val));
                    }
                }
            }
        }
        if (cmd == CMD_CONNECT) {
            Log.i(TAG, "Zirve Connect yanıtı alındı, dLen=" + dLen);
        }
    }

    private void processTextLine(String line) {
        String decrypted = line;
        try {
            String d = CryptoUtils.decrypt(line);
            if (d != null && !d.isEmpty()) decrypted = d;
        } catch (Exception ignored) {
        }
        GradientData gd = parseGradientData(decrypted);
        if (gd != null) emitGradient(gd);
    }

    private GradientData parseGradientData(String data) {
        if (data == null || data.isEmpty() || data.startsWith("OK") || data.startsWith("AT") || data.startsWith("+")) {
            return null;
        }
        try {
            if (data.contains("|")) {
                String[] parts = data.split("\\|");
                if (parts.length >= 3) {
                    float s1 = Float.parseFloat(parts[0].replaceAll("[^\\d.\\-]", ""));
                    float s2 = Float.parseFloat(parts[1].replaceAll("[^\\d.\\-]", ""));
                    float g = Float.parseFloat(parts[2].replaceAll("[^\\d.\\-]", ""));
                    return new GradientData(GradientData.DataType.TRIPLE, s1, s2, g);
                }
            }
            if (data.contains(":")) {
                String[] parts = data.split(":");
                if (parts.length >= 2) {
                    String prefix = parts[0].trim().toUpperCase();
                    String valStr = parts[1].trim().replaceAll("[^\\d.\\-]", "");
                    if (!valStr.isEmpty()) {
                        float val = Float.parseFloat(valStr);
                        GradientData.DataType type;
                        switch (prefix) {
                            case "G":
                            case "GRAD":
                                type = GradientData.DataType.GRADIENT;
                                break;
                            case "M":
                            case "MAG":
                                type = GradientData.DataType.MAGNETOMETER;
                                break;
                            case "D":
                            case "DIFF":
                                type = GradientData.DataType.DIFFERENTIAL;
                                break;
                            default:
                                type = GradientData.DataType.SINGLE;
                                break;
                        }
                        return new GradientData(type, null, null, val);
                    }
                }
            }
            if (data.contains(",")) {
                String[] parts = data.split(",");
                if (parts.length >= 2) {
                    float s1 = Float.parseFloat(parts[0].replaceAll("[^\\d.\\-]", ""));
                    float s2 = Float.parseFloat(parts[1].replaceAll("[^\\d.\\-]", ""));
                    if (parts.length >= 3) {
                        float g = Float.parseFloat(parts[2].replaceAll("[^\\d.\\-]", ""));
                        return new GradientData(GradientData.DataType.TRIPLE, s1, s2, g);
                    }
                    return new GradientData(GradientData.DataType.DIFFERENTIAL, s1, s2, s1 - s2);
                }
            }
            String cleaned = data.replaceAll("[^\\d.\\-]", "");
            if (!cleaned.isEmpty() && !cleaned.equals("-") && !cleaned.equals(".")) {
                return new GradientData(Float.parseFloat(cleaned));
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse error: " + e.getMessage());
        }
        return null;
    }

    // ---- live scanning control --------------------------------------------

    public void startLiveMode() {
        isLiveMode = true;
        Log.i(TAG, "startLiveMode: mode=" + connectionMode + " type=" + detectedDeviceType + " connected=" + isConnected);
        if (connectionMode == ConnectionMode.CLASSIC_SPP) {
            if ("zirve".equals(detectedDeviceType)) {
                Log.i(TAG, "Zirve Connect komutu gönderiliyor...");
                sendZirveConnect(0, 0, 1, 100);
                mainHandler.postDelayed(this::zirveGetSamplesLoop, 800L);
            }
            sendCommand("START");
            sendCommand("LIVE:START");
            return;
        }
        sendCommand("LIVE:START");
    }

    public void zirveGetSamplesLoop() {
        if (!isLiveMode || !isConnected) {
            Log.d(TAG, "zirveGetSamplesLoop durdu: isLive=" + isLiveMode + " connected=" + isConnected);
            return;
        }
        Log.d(TAG, "GetSamples gönderiliyor...");
        sendZirveGetSamples(0);
        mainHandler.postDelayed(this::zirveGetSamplesLoop, 150L);
    }

    public void stopLiveMode() {
        isLiveMode = false;
        Log.i(TAG, "stopLiveMode çağrıldı");
        if (connectionMode != ConnectionMode.CLASSIC_SPP) sendCommand("LIVE:STOP");
    }

    public void calibrate() {
        sendCommand("CAL:ZERO");
    }

    // ---- getters/listeners --------------------------------------------

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isLiveMode() {
        return isLiveMode;
    }

    public String getDetectedDeviceType() {
        return detectedDeviceType;
    }

    public ConnectionMode getConnectionMode() {
        return connectionMode;
    }

    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    public float getLastGradientValue() {
        return lastGradientValue;
    }

    public long getLastDataTime() {
        return lastDataTime;
    }

    public int getTotalDataReceived() {
        return totalDataReceived;
    }

    public List<BluetoothDevice> getDiscoveredDevices() {
        List<BluetoothDevice> sorted = new ArrayList<>();
        for (BluetoothDevice d : priorityDevices) if (!sorted.contains(d)) sorted.add(d);
        for (BluetoothDevice d : discoveredDevices) if (!sorted.contains(d)) sorted.add(d);
        return sorted;
    }

    public void setGradientListener(GradientListener l) {
        gradientListener = l;
    }

    public void setConnectionListener(ConnectionListener l) {
        connectionListener = l;
    }

    public void setDiscoveryListener(DeviceDiscoveryListener l) {
        discoveryListener = l;
    }

    public void setRawDataListener(RawDataListener l) {
        rawDataListener = l;
    }
}
