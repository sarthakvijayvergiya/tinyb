import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import tinyb.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.TimeUnit;
import java.nio.*;

public class HelloTinyB {
  private static final float SCALE_LSB = 0.03125f;
  static boolean running = true;

  static void printDevice(BluetoothDevice device) {
    System.out.print("Address = " + device.getAddress());
    System.out.print(" Name = " + device.getName());
    System.out.print(" Connected = " + device.getConnected());
    System.out.println();
  }

  static float convertCelsius(int raw) {
    return raw / 128f;
  }

  /*
   * After discovery is started, new devices will be detected. We can get a list of all devices through the manager's
   * getDevices method. We can the look through the list of devices to find the device with the MAC which we provided
   * as a parameter. We continue looking until we find it, or we try 15 times (1 minutes).
   */
  static BluetoothDevice getDevice(String address) throws InterruptedException {
    BluetoothManager manager = BluetoothManager.getBluetoothManager();
    BluetoothDevice sensor = null;
    for (int i = 0; (i < 15) && running; ++i) {
      List<BluetoothDevice> list = manager.getDevices();
      if (list == null)
        return null;

      for (BluetoothDevice device : list) {
        printDevice(device);
        /*
         * Here we check if the address matches.
         */
        if (device.getAddress().equals(address))
          sensor = device;
      }

      if (sensor != null) {
        return sensor;
      }
      Thread.sleep(4000);
    }
    return null;
  }

  /*
   * Our device should expose a temperature service, which has a UUID we can find out from the data sheet. The service
   * description of the SensorTag can be found here:
   * http://processors.wiki.ti.com/images/a/a8/BLE_SensorTag_GATT_Server.pdf. The service we are looking for has the
   * short UUID AA00 which we insert into the TI Base UUID: f000XXXX-0451-4000-b000-000000000000
   */
  static BluetoothGattService getService(BluetoothDevice device, String UUID) throws InterruptedException {
    System.out.println("Services exposed by device: " + UUID);
    BluetoothGattService tempService = null;
    List<BluetoothGattService> bluetoothServices = null;
    do {
      bluetoothServices = device.getServices();
      if (bluetoothServices == null)
        return null;

      for (BluetoothGattService service : bluetoothServices) {
        System.out.println("UUID: " + service.getUUID());
        if (service.getUUID().equals(UUID)){
          tempService = service;
          System.out.println("Match Success");
        }
      }
      Thread.sleep(4000);
    } while (bluetoothServices.isEmpty() && running);
    return tempService;
  }

  static BluetoothGattCharacteristic getCharacteristic(BluetoothGattService service, String UUID) {
    System.out.println("Charaterstics: " + UUID);
    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
    if (characteristics == null)
      return null;

    for (BluetoothGattCharacteristic characteristic : characteristics) {
      System.out.println("Characteristic-----: " + characteristic.getUUID());
      System.out.println("Service: " + characteristic.getService());
      List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
      System.out.println("Descriptor Length: " + descriptors.size());
      for (BluetoothGattDescriptor descriptor : descriptors) {
        System.out.println("Descriptor: " + descriptor.getUUID());
      }
      if (characteristic.getUUID().equals(UUID)) {
        System.out.println("Match success: " + UUID);
        return characteristic;
      }
    }
    return null;
  }

  public static void init(BluetoothGattCharacteristic tempValue, BluetoothGattCharacteristic batValue) throws InterruptedException, ExecutionException {
    CompletableFuture cfa = CompletableFuture.runAsync(() -> generateA(tempValue));
    CompletableFuture cfb = CompletableFuture.runAsync(() -> generateB(batValue));
    CompletableFuture.allOf(cfa, cfb).get();
  }

  public static void generateA(BluetoothGattCharacteristic tempValue) {
    while (true) {
      try {
        byte[] tempRaw = tempValue.readValue();
//        System.out.println("Temp raw----------------"+ tempRaw);
//        System.out.print("Temp raw = {");
        for (byte b : tempRaw) {
//          System.out.print(String.format("%02x,", b));
        }
//        System.out.print("}");
        ByteBuffer bb = ByteBuffer.wrap(tempRaw);
        bb.order( ByteOrder.LITTLE_ENDIAN);
        while( bb.hasRemaining()) {
          short v = bb.getShort();
          System.out.println("Temp Decimal-----"+ v);
          System.out.println("Temperature-----"+ v/256);
        }
        System.out.println("Entering temperature " + Thread.currentThread());
        Thread.sleep(2000);
      } catch (InterruptedException ex) {
        // never mind
      }
    }
  }

  public static void generateB(BluetoothGattCharacteristic batValue) {
    while (true) {
      try {
        System.out.println("Entering Battery " + Thread.currentThread());
        byte[] tempRaw = batValue.readValue();
//        System.out.println("Temp raw----------------"+ tempRaw);
//        System.out.print("Temp raw = {");
        for (byte b : tempRaw) {
//          System.out.print(String.format("%02x,", b));
        }
//        System.out.print("}");
        ByteBuffer bb = ByteBuffer.wrap(tempRaw);
        bb.order( ByteOrder.LITTLE_ENDIAN);
        while( bb.hasRemaining()) {
          short v = bb.getShort();
          System.out.println("Battery Decimal-----"+ v);
          System.out.println("Battery-----"+ v/100);
        }
        Thread.sleep(2000);
      } catch (InterruptedException ex) {
        // never mind
      }
    }
  }

  /*
   * This program connects to a TI SensorTag 2.0 and reads the temperature characteristic exposed by the device over
   * Bluetooth Low Energy. The parameter provided to the program should be the MAC address of the device.
   *
   * A wiki describing the sensor is found here: http://processors.wiki.ti.com/index.php/CC2650_SensorTag_User's_Guide
   *
   * The API used in this example is based on TinyB v0.3, which only supports polling, but v0.4 will introduce a
   * simplied API for discovering devices and services.
   */
  public static void main(String[] args) throws InterruptedException, ExecutionException {

    if (args.length < 1) {
      System.err.println("Run with <device_address> argument");
      System.exit(-1);
    }

    /*
     * To start looking of the device, we first must initialize the TinyB library. The way of interacting with the
     * library is through the BluetoothManager. There can be only one BluetoothManager at one time, and the
     * reference to it is obtained through the getBluetoothManager method.
     */
    BluetoothManager manager = BluetoothManager.getBluetoothManager();

    /*
     * The manager will try to initialize a BluetoothAdapter if any adapter is present in the system. To initialize
     * discovery we can call startDiscovery, which will put the default adapter in discovery mode.
     */
    boolean discoveryStarted = manager.startDiscovery();

    System.out.println("The discovery started: " + (discoveryStarted ? "true" : "false"));
    BluetoothDevice sensor = getDevice(args[0]);

    /*
     * After we find the device we can stop looking for other devices.
     */
    try {
      manager.stopDiscovery();
    } catch (BluetoothException e) {
      System.err.println("Discovery could not be stopped.");
    }

    if (sensor == null) {
      System.err.println("No sensor found with the provided address.");
      System.exit(-1);
    }

    System.out.print("Found device: ");
    printDevice(sensor);

    if (sensor.connect())
      System.out.println("Sensor with the provided address connected");
    else {
      System.out.println("Could not connect device.");
      System.exit(-1);
    }

    Lock lock = new ReentrantLock();
    Condition cv = lock.newCondition();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        running = false;
        lock.lock();
        try {
          cv.signalAll();
        } finally {
          lock.unlock();
        }

      }
    });


    BluetoothGattService configService = getService(sensor, "1c930003-d459-11e7-9296-b8e856369374");

    if (configService == null) {
      System.err.println("This device does not have the temperature service we are looking for.");
      sensor.disconnect();
      System.exit(-1);
    }
    System.out.println("Found service " + configService.getUUID());

    BluetoothGattCharacteristic calibrationCharacteristic = getCharacteristic(configService, "1c930029-d459-11e7-9296-b8e856369374");
//     BluetoothGattCharacteristic batValue = getCharacteristic(tempService, args[3]);

    System.out.println("Found BluetoothGattCharacteristic " + calibrationCharacteristic.getUUID());
//     System.out.println("Found BluetoothGattCharacteristic " + batValue.getUUID());
//     init(tempValue, batValue);

    byte[] calibrationValue = calibrationCharacteristic.readValue();

    double temperature =
        ByteBuffer.wrap(calibrationValue).order(ByteOrder.LITTLE_ENDIAN).getDouble();

    System.out.println("Temperature" + temperature);

//    Dictionary<String, byte[]> MODE_CONFIG = new Hashtable<String, byte[]>(){{
//      put("MANUAl", new byte[]{0x01});
//      put("WAKEUP", new byte[]{0x02});
//      put("WAKEUP+", new byte[]{0x03});
//    }};
//
//    byte[] modeConfigValue = MODE_CONFIG.get("WAKEUP");
//
//    System.out.println("ModeConfigValue " + modeConfigValue);
//
//    tempValue.writeValue(modeConfigValue);
//         BluetoothGattCharacteristic tempConfig = getCharacteristic(tempService, "f000aa02-0451-4000-b000-000000000000");
//         BluetoothGattCharacteristic tempPeriod = getCharacteristic(tempService, "f000aa03-0451-4000-b000-000000000000");

//         if (tempValue == null || tempConfig == null || tempPeriod == null) {
//             System.err.println("Could not find the correct characteristics.");
//             sensor.disconnect();
//             System.exit(-1);
//         }

//         System.out.println("Found the temperature characteristics");

    /*
     * Turn on the Temperature Service by writing 1 in the configuration characteristic, as mentioned in the PDF
     * mentioned above. We could also modify the update interval, by writing in the period characteristic, but the
     * default 1s is good enough for our purposes.
     */
//         byte[] config = { 0x01 };
//         tempConfig.writeValue(config);

//         /*
//          * Each second read the value characteristic and display it in a human readable format.
//          */
//    while (running) {
//      byte[] tempRaw = tempValue.readValue();
//      System.out.println("Temp raw----------------"+ tempRaw);
//      System.out.print("Temp raw = {");
//      for (byte b : tempRaw) {
//        System.out.print(String.format("%02x,", b));
//      }
//      System.out.print("}");
//      ByteBuffer bb = ByteBuffer.wrap(tempRaw);
//      bb.order( ByteOrder.LITTLE_ENDIAN);
//      while( bb.hasRemaining()) {
//        short v = bb.getShort();
//        System.out.println("Battery Hex-----"+ v);
//      }
//
//      /*
//       * The temperature service returns the data in an encoded format which can be found in the wiki. Convert the
//       * raw temperature format to celsius and print it. Conversion for object temperature depends on ambient
//       * according to wiki, but assume result is good enough for our purposes without conversion.
//       */
////             int objectTempRaw = (tempRaw[0] & 0xff) | (tempRaw[1] << 8);
////             int ambientTempRaw = (tempRaw[2] & 0xff) | (tempRaw[3] << 8);
//
////             System.out.println(objectTempRaw);
////             System.out.println(ambientTempRaw);
//
////             float objectTempCelsius = convertCelsius(objectTempRaw);
////             float ambientTempCelsius = convertCelsius(ambientTempRaw);
//
////             System.out.println(
////                     String.format(" Temp: Object = %fC, Ambient = %fC", objectTempCelsius, ambientTempCelsius));
//
//      lock.lock();
//      try {
//        cv.await(1, TimeUnit.SECONDS);
//      } finally {
//        lock.unlock();
//      }
//    }
    sensor.disconnect();

  }
}
