package dk.au.cs.ubi.hellosensing;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.io.Closer;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dk.au.cs.ubi.hellosensing.model.AccelerometerEvent;

public class SensingService extends Service {

    /**
     * A size limit on the number of events before we should flush.
     */
    private static final int FLUSH_SIZE_LIMIT = 10000;

    /**
     * A time limit on when we should flush.
     */
    private static final long FLUSH_TIME_LIMIT = TimeUnit.SECONDS.toMillis(30);

    /**
     * The filename to save our data to.
     */
    private static final String FILENAME = "AccelerometerEvent.csv";

    /**
     * The CSV delimiter character.
     */
    private static final String DELIMITER = ",";

    /**
     * Tag for logging. I usually use the class name, without the package part.
     */
    private static final String TAG = SensingService.class.getSimpleName();

    /**
     * This {@link android.hardware.SensorEventListener} just delegates to the
     * {@link #write(android.hardware.SensorEvent)} method.
     */
    private final SensorEventListener eventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            write(event);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private SensorManager sensorManager;
    private PowerManager.WakeLock wakeLock;

    /**
     * The name of our storage directory.
     */
    private String storageDirectoryName;

    /**
     * The last time we attempted to write to storage. We're basing this on
     * {@link android.os.SystemClock#elapsedRealtime()}, which always counts up even during phone
     * standby, and isn't user settable.
     */
    private long lastAttemptedFlushTime = SystemClock.elapsedRealtime();

    /**
     * A queue to holds our AccelerometerEvents. It's important that we don't store the
     * {@link android.hardware.SensorEvent}s directly, as these are reused by the system.
     */
    private BlockingQueue<AccelerometerEvent> eventQueue = Queues.newLinkedBlockingQueue();

    /**
     * This is just a convenient way to start new threads. You don't need to think about stopping
     * them again etc.
     */
    private final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Starting...");

        // I always use the application context for context, so we're sure it's the same for
        // the whole application.
        Context context = getApplicationContext();

        // Set storage directory name to current date and time
        DateTime now = DateTime.now();
        String storageDirectoryNameWithIllegalCharacters = now.toString(ISODateTimeFormat.dateTimeNoMillis());
        storageDirectoryName = storageDirectoryNameWithIllegalCharacters.replace(":", ".");

        // The wake lock is important so that we can still sense when the phone display is off
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SensingService.class.getName());
        wakeLock.acquire();

        // Register for sensing the accelerometer as fast as possible
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (!sensorManager.registerListener(eventListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)) {
            Log.w(TAG, "Couldn't register accelerometer.");
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "Stopping...");

        sensorManager.unregisterListener(eventListener);
        wakeLock.release();

        flush();
    }

    private void write(SensorEvent sensorEvent) {
        // We just take the timestamp from SensorEvent, which is in nanoseconds. But it differs from phone
        // to phone what exactly is in there (uptime, unixtime, etc.). It should be good for comparing event relatively, though.
        AccelerometerEvent event = new AccelerometerEvent(sensorEvent.timestamp, sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]);
        eventQueue.offer(event);

        // Check if we should flush
        if (eventQueue.size() >= FLUSH_SIZE_LIMIT || SystemClock.elapsedRealtime() - lastAttemptedFlushTime >= FLUSH_TIME_LIMIT) {
            lastAttemptedFlushTime = SystemClock.elapsedRealtime();
            flush();
        }
    }

    private void flush() {
        // Save to external storage
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Log.e(TAG, "External storage not mounted, cannot write.");
            return;
        }

        // Get our storage directory
        File externalFilesDir = getApplicationContext().getExternalFilesDir(null);
        final File storageDirectory = new File(externalFilesDir, storageDirectoryName);

        // We need to write in an external thread because:
        // a) Android says we have to
        // b) We don't want to delay our time-critical sensing thread with expensive write operations
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                // We'll synchronise on the event queue so that we don't write data simultaneously
                // from multiple threads.
                // Note that we can't synchronise on the main thread, that would lock up the whole GUI.
                synchronized (eventQueue) {
                    // Check if storage directory exists
                    if (!storageDirectory.exists()) {
                        if (!storageDirectory.mkdir()) {
                            Log.e(TAG, "Couldn't create storage directory " + storageDirectory + ".");
                            return;
                        }
                    }

                    // Drain the queue to a list. This means that we take out all events currently
                    // in the queue, and just write those. The queue can then receive new events
                    // and we don't have to bother with it.
                    List<AccelerometerEvent> events = Lists.newLinkedList();
                    eventQueue.drainTo(events);

                    // Return if we have no events to write
                    if (events.isEmpty()) {
                        return;
                    }

                    File file = new File(storageDirectory, FILENAME);

                    // Check if we should write the CSV header, only if the file doesn't exist
                    boolean writeHeader = !file.exists();

                    try {
                        // This is just a nice way to not have to handle closing streams properly,
                        // which can be a pain in the a**.
                        // See https://code.google.com/p/guava-libraries/wiki/ClosingResourcesExplained
                        Closer closer = Closer.create();
                        try {
                            BufferedWriter writer = closer.register(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), Charsets.UTF_8)));

                            if (writeHeader) {
                                writer.write("timestamp,x,y,z");
                                writer.newLine();
                            }
                            for (AccelerometerEvent event : events) {
                                writer.write(event.getTimestamp() + DELIMITER + event.getX() + DELIMITER);
                                writer.write(event.getY() + DELIMITER + event.getZ());
                                writer.newLine();
                            }
                            writer.flush();
                        } catch (Throwable e) {
                            throw closer.rethrow(e);
                        } finally {
                            closer.close();
                        }

                        Log.d(TAG, "Wrote " + events.size() + " events to file " + file + ".");
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing to file " + file + ".", e);
                    }
                }
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
