package edu.stanford.nlp.ie.pascal;

import java.util.GregorianCalendar;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * For plotting distributions of distance between dates.
 *
 * @author Jamie Nicolson
 */

class Histogram {

    int bins[];
    double low;
    double high;

    public Histogram(double low, double high, int numbins) {
        bins = new int[numbins];
        this.low = low;
        this.high = high;
    }

    public void addSample(double sample) {
        if( sample >= low || sample < high) {
            // THIS METHOD OBVIOUSLY ISN'T DONE YET
        }
    }
}

class ClassLearner {

    private long[] counts;
    private double[] probs;
    private long total;

    public ClassLearner(int numClasses) {
        reset(numClasses);
    }

    public void reset(int numClasses) {
        counts = new long[numClasses];
        total = 0;
    }

    public void increment(int classIndex) {
        counts[classIndex]++;
        total++;
    }

    public void doAddOneSmoothing() {
        for( int i = 0; i < counts.length; ++i ) {
            counts[i]++;
        }
        total += counts.length;
    }

    public void computeProbs() {
        boolean hasZeroEntry = false;
        for(int i = 0; i < counts.length; i++) {
            if( counts[i] == 0 ) {
                hasZeroEntry = true;
            }
        }
        if( hasZeroEntry ) {
            doAddOneSmoothing();
        }

        probs = new double[counts.length];
        for(int i = 0; i < counts.length; i++) {
            probs[i] = ((double)counts[i])/((double)total);
        }
    }

    public double[] getProbs() {
        return probs;
    }

    public String dump() {
        StringBuffer sb = new StringBuffer();
        if( probs != null ) {
            for(int i = 0; i < probs.length; ++i ) {
                sb.append(probs[i]);
                if( i < probs.length-1 ) {
                    sb.append(",");
                }
            }
        }
        return sb.toString();
    }
}

class GaussianParameterComputer {

    double squareSum;
    double sum;
    double count;

    boolean clean;
    double mean;
    double stddev;

    public GaussianParameterComputer() {
        reset();
    }

    public void reset() {
        squareSum = sum = count = mean = stddev = 0.0;
    }
    
    public void addSample(double sample) {
        sum += sample;
        squareSum += (sample*sample);
        count += 1.0;
        clean = false;
    }

    private void computeStats() {
        mean = sum / count;
        stddev = Math.sqrt( (squareSum/count) - (mean*mean) );
        clean = true;
    }

    public double getMean() {
        if( ! clean ) {
            computeStats();
        }
        return mean;
    }

    public double getStddev() {
        if( ! clean ) {
            computeStats();
        }
        return stddev;
    }
}

public class ComputeDateDistributions {

    private static final int NUM_DATES = 4;
    private static final int NUM_DATES_CHOOSE_2 = 6;

    private static final String OUTPUT_FILENAME = "date_dist_out.prop";

    private static final String[] DATE_NAMES =
        {"sub","noa", "crc", "ws"};

    public static void main(String args[]) throws Exception {

        BufferedReader reader = new BufferedReader(new FileReader(args[0]));
        PrintWriter output = new PrintWriter(new FileWriter(OUTPUT_FILENAME));

        String line;

        SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/y");
        int count = 0;

        GaussianParameterComputer gaussians[] =
            new GaussianParameterComputer[NUM_DATES_CHOOSE_2];
        ClassLearner nullProbs[] = new ClassLearner[NUM_DATES];
        ClassLearner dayOfWeekProbs[] = new ClassLearner[NUM_DATES];
        ClassLearner monthProbs[] = new ClassLearner[NUM_DATES];
        ClassLearner rangeProbs[] = new ClassLearner[NUM_DATES];
        for(int i = 0; i < NUM_DATES_CHOOSE_2; ++i ) {
            gaussians[i] = new GaussianParameterComputer();
        }
        for( int i = 0; i < NUM_DATES; ++i ) {
            nullProbs[i] = new ClassLearner(2);
            dayOfWeekProbs[i] = new ClassLearner(7);
            monthProbs[i] = new ClassLearner(12);
            rangeProbs[i] = new ClassLearner(2);
        }
        for( ; (line = reader.readLine()) != null ; ++count ) {
            StringTokenizer tok = new StringTokenizer(line);

            String nameString = tok.nextToken();
            System.out.println("Name: " + nameString);

            Date []dates = new Date[NUM_DATES];
            boolean []ranges = new boolean[NUM_DATES];
            for( int idx = 0; tok.hasMoreTokens(); ++idx ) {
                String dateString = tok.nextToken();
                dates[idx] = dateFormat.parse(dateString);
                System.out.println("Date: " + dates[idx].toString());
                String rangeString = tok.nextToken();
                if( rangeString.indexOf("true") != -1) {
                    ranges[idx] = true;
                    if( idx < NUM_DATES-1 ) {
                        System.out.println(nameString + " has a range");
                    }
                } else {
                    ranges[idx] = false;
                }
            }
            int gaussIndex = 0;
            for( int i = 0; i < NUM_DATES; ++i ) {
                long t0 = dates[i].getTime();
                if( t0 < 0 ) {
                    // invalid or null date
                    nullProbs[i].increment( 1 );
                } else {
                    // not a null date
                    nullProbs[i].increment( 0 );
                    GregorianCalendar cal = new GregorianCalendar();
                    cal.setTime(dates[i]);
                    dayOfWeekProbs[i].increment( cal.get(Calendar.DAY_OF_WEEK) - 1);
                    monthProbs[i].increment( cal.get(Calendar.MONTH) );
                    rangeProbs[i].increment( ranges[i] ? 1 : 0 );
                }
                if( i < NUM_DATES-1 ) {
                    for( int j = i+1; j < NUM_DATES; ++j, ++gaussIndex ) {
                        long t1 = dates[j].getTime();
                        if( t1 < 0 || t0 < 0 ) {
                            //output.print(",");
                            continue;
                        }
                        double diff = millisToDays(t1 - t0);
                        gaussians[gaussIndex].addSample(diff);
                    }
                }
            }
            //output.println();

        }

        output.print("gaussian_means=");
        for( int i = 0; i < NUM_DATES_CHOOSE_2; ++i ) {
            output.print(gaussians[i].getMean());
            if( i < NUM_DATES_CHOOSE_2 - 1 ) {
                output.print(",");
            }
        }
        output.print("\ngaussian_deviations=");
        for( int i = 0; i < NUM_DATES_CHOOSE_2; ++i ) {
            output.print(gaussians[i].getStddev());
            if( i < NUM_DATES_CHOOSE_2 - 1 ) {
                output.print(",");
            }
        }
        output.println();

        for( int i = 0; i < NUM_DATES; ++i ) {
            output.print("null_distribution_" + DATE_NAMES[i] + "=");
            //nullProbs[i].doAddOneSmoothing();
            nullProbs[i].computeProbs();
            output.println(nullProbs[i].dump());
        }
        for( int i = 0; i < NUM_DATES; ++i ) {
            output.print("day_of_week_distribution_" + DATE_NAMES[i] + "=");
            //dayOfWeekProbs[i].doAddOneSmoothing();
            dayOfWeekProbs[i].computeProbs();
            output.println(dayOfWeekProbs[i].dump());
        }
        for( int i = 0; i < NUM_DATES; ++i ) {
            output.print("month_distribution_" + DATE_NAMES[i] + "=");
            //monthProbs[i].doAddOneSmoothing();
            monthProbs[i].computeProbs();
            output.println(monthProbs[i].dump());
        }
        for( int i = 0; i < NUM_DATES; ++i ) {
            output.print("range_distribution_" + DATE_NAMES[i] + "=");
            rangeProbs[i].computeProbs();
            output.println(rangeProbs[i].dump());
        }

        output.close();
    }

    public static double millisToDays(long millis) {
        return (millis) / (1000 * 3600 * 24.0);
    }
    public static double millisToDays(double millis) {
        return (millis) / (1000 * 3600 * 24.0);
    }
}

