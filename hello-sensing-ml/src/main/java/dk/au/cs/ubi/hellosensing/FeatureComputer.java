package dk.au.cs.ubi.hellosensing;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;

import dk.au.cs.ubi.hellosensing.model.AccelerometerEvent;

/**
 * A class to compute some basic accelerometer features from raw accelerometer events.
 *
 * Input is from STD_IN in CSV format, output is on STD_OUT, logging on STD_ERR.
 *
 * @author Markus Wüstenberg
 */
public class FeatureComputer {

    /**
     * Our window size. It's often good to have this be an even number, and perhaps also a
     * power of 2 (for e.g. frequency analysis).
     */
    private static final int WINDOW_SIZE = 256;

    /**
     * The number of samples to overlap, for sliding windows.
     */
    private static final int WINDOW_OVERLAP = WINDOW_SIZE / 2;

    /**
     * Earth's gravity in SI units (m/s^2).
     */
    public static final float GRAVITY_EARTH = 9.80665f;

    private static final String DELIMITER = ",";

    private static final Logger log = LoggerFactory.getLogger(FeatureComputer.class);

    private static BufferedWriter writer;

    /**
     * Our sliding window.
     */
    private static List<AccelerometerEvent> window = Lists.newArrayList();

    public static void main(String... args) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, Charsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(System.out, Charsets.UTF_8));

            // Write our header
            writer.write("timestamp,min,max,mean,variance,standard_deviation");
            writer.newLine();

            // Ignore first line with header
            reader.readLine();

            // Parse CSV file
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(DELIMITER);
                AccelerometerEvent event = new AccelerometerEvent(Long.parseLong(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
                handle(event);
            }
            writer.flush();

            log.info("Finished writing features.");
        } catch (IOException e) {
            log.error("Couldn't calculate features.", e);
        }
    }

    /**
     * Calculates some example features from the accelerometer.
     *
     * @param event An AccelerometerEvent to add to the sliding window.
     */
    private static void handle(AccelerometerEvent event) throws IOException {
        window.add(event);

        // Check if we have filled our window
        if (window.size() < WINDOW_SIZE) {
            return;
        }

        // Compute some features on the now-full window
        DescriptiveStatistics stats = new DescriptiveStatistics();
        List<Double> magnitudes = getMagnitudes(window);
        for (double magnitude : magnitudes) {
            stats.addValue(magnitude);
        }

        double min = stats.getMin();
        double max = stats.getMax();
        double mean = stats.getMean();
        double variance = stats.getVariance();
        double standardDeviation = stats.getStandardDeviation();

        long firstTimestamp = window.get(0).getTimestamp();

        write(firstTimestamp, min, max, mean, variance, standardDeviation);

        // Remove events from the beginning of the list, for overlap
        while (window.size() > WINDOW_OVERLAP) {
            window.remove(0);
        }
    }

    /**
     * Writes out in CSV format.
     * @param firstTimestamp The timestamp for the window. Note that this should probably be filtered out in Weka.
     * @param features The features to write.
     */
    private static void write(long firstTimestamp, double... features) throws IOException {
        String featureLine = firstTimestamp + DELIMITER;
        for (double feature : features) {
            featureLine += feature + DELIMITER;
        }

        // Remove last delimiter
        featureLine = featureLine.substring(0, featureLine.length() - 1);

        writer.write(featureLine);
        writer.newLine();
    }

    /**
     * This gives an approximation of the magnitude of the acceleration signal, but isn't always correct.
     * @param event The event to compute on.
     * @return The magnitude over all three axes, minus gravitational acceleration.
     */
    private static double getMagnitude(AccelerometerEvent event) {
        double magnitude = Math.sqrt(Math.pow(event.getX(), 2) + Math.pow(event.getY(), 2) + Math.pow(event.getZ(), 2));
        return magnitude - GRAVITY_EARTH;
    }

    /**
     * Get magnitudes conveniently in a list.
     * @param events The events to calculate magnitudes on.
     * @return A List of magnitudes.
     */
    private static List<Double> getMagnitudes(Iterable<AccelerometerEvent> events) {
        List<Double> magnitudes = Lists.newArrayList();
        for (AccelerometerEvent event : events) {
            magnitudes.add(getMagnitude(event));
        }
        return magnitudes;
    }
}
