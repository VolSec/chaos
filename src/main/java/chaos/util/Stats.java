package chaos.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class contains a few useful stats functions.
 *
 * @author pendgaft
 */
public class Stats {

    /**
     * Method that computes the mean of a list.
     *
     * @param vals - a non-empty list of integers
     * @return - the mean of the values in a double
     */
    public static <T> double mean(List<T> vals) {
        /*
         * div zero errors are bad...
         */
        if (vals.size() == 0) {
            throw new RuntimeException("Asked to compute the mean of an empty list!");
        }

        double sum = 0;
        for (T tVal : vals) {
            sum += (Double) tVal;
        }
        return sum / ((double) vals.size());
    }

    /**
     * Method that computes the median of a list of integers.
     *
     * @param vals - a non-empty list of ints
     * @return the median of the supplied list
     */
    public static <T> double median(List<T> vals) {
        /*
         * median on empty lists, that a nono..
         */
        if (vals.size() == 0) {
            throw new RuntimeException("Asked to compute the median of an empty list!");
        }

        /*
         * create a clone of the list, since sorting of the original list would
         * be a side effect of this method and we want to avoid that.
         */
        List<Double> sortedList = new ArrayList<Double>();
        sortedList.addAll((List<Double>) vals);

        /*
         * Actually compute the median
         */
        Collections.sort(sortedList);
        if (sortedList.size() % 2 == 0) {
            double a = sortedList.get(sortedList.size() / 2);
            double b = sortedList.get((sortedList.size() / 2) - 1);
            return (a + b) / 2;
        } else {
            int pos = sortedList.size() / 2;
            return sortedList.get(pos);
        }
    }

    /**
     * Method that computes the standard deviation of a list
     *
     * @param vals - a non-empty list of ints to compute over
     * @return - the std deviation of the supplied list
     */
    public static <T> double stdDev(List<T> vals) {
        /*
         * div zero errors are bad...
         */
        if (vals.size() == 0) {
            throw new RuntimeException("Asked to compute the std dev of an empty list!");
        }

        double mean = Stats.mean(vals);

        double sum = 0;
        for (T tVal : vals) {
            double tDoub = (Double) tVal;
            sum += Math.pow((tDoub - mean), 2);
        }

        return Math.sqrt(sum / ((double) vals.size()));
    }

    /**
     * Function that dumps a CDF of the supplied list of doubles to a file
     * specified by a string.
     */
    public static void printCDF(List<List<Double>> origVals, String fileName) throws IOException {
        /*
         * CDFs over empty lists don't really make sense
         */
        if (origVals.size() == 0) {
            throw new RuntimeException("Asked to build CDF of an empty list!");
        }

        /*
         * Clone the list to avoid the side effect of sorting the original list
         */
        List<List<Double>> vals = new ArrayList<List<Double>>(origVals.size());
        vals.addAll(origVals);

        for (List<Double> dVals : vals) {
            Collections.sort(dVals);
        }

        double fracStep = 1.0 / (double) vals.size();
        double currStep = 0.0;

        BufferedWriter outFile = new BufferedWriter(new FileWriter(fileName));

        for (int counter = 0; counter < vals.size(); counter++) {
            currStep += fracStep;
            if (counter >= vals.size() - 1 || vals.get(counter) != vals.get(counter + 1)) {
                //if (vals.get(counter) != vals.get(counter + 1) && counter <= vals.size() + 1) {
                outFile.write("" + currStep + "," + vals.get(counter) + "\n");
                System.out.print("" + currStep + "," + vals.get(counter) + "\n");
            }
        }

        outFile.close();
    }
}
