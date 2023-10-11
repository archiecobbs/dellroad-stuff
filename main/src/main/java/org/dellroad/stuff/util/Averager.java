
/*
 * Copyright (C) 2023 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.util;

import com.google.common.base.Preconditions;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Calculates running averages and variance.
 *
 * <p>
 * Instances are not thread safe.
 *
 * @see <a href="http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Online_algorithm">Wikipedia</a>
 */
public class Averager implements Cloneable {

    private int count;
    private double mean;
    private double m2;

// Constructors

    /**
     * Default constructor.
     *
     * <p>
     * Creates an empty (and therefore invalid) instance.
     */
    public Averager() {
    }

    /**
     * Constructor taking first datapoint.
     *
     * @param firstValue first data point
     */
    public Averager(double firstValue) {
        this.addValue(firstValue);
    }

    private Averager(int count, double mean, double m2) {
        this.count = count;
        this.mean = mean;
        this.m2 = m2;
    }

// Methods

    /**
     * Add a value.
     *
     * @param value value to add to the running average
     * @throws IllegalArgumentException if {@code value} is {@code NaN} or infinity
     */
    public void addValue(double value) {
        Preconditions.checkArgument(Double.isFinite(value), "non-finite value");
        this.count++;
        final double delta = value - this.mean;
        this.mean += delta / this.count;
        this.m2 += delta * (value - this.mean);
    }

    /**
     * Add this instance to the given instance and return a new combined instance.
     *
     * @param that other instance
     * @return combined average
     */
    public Averager add(Averager that) {
        Preconditions.checkNotNull(that, "null that");
        if (this.count == 0)
            return that.clone();
        if (that.count == 0)
            return this.clone();
        final int combinedCount = this.count + that.count;
        final double sigma = that.mean - this.mean;
        final double combinedMean = this.mean + sigma * that.count / (double)combinedCount;
        final double combinedM2 = this.m2 + that.m2 + sigma * sigma * (this.count * that.count) / (double)combinedCount;
        return new Averager(combinedCount, combinedMean, combinedM2);
    }

    /**
     * Subtract the given instance from this instance.
     *
     * @return average with {@code that} data points removed
     */
    public Averager subtract(Averager that) {
        Preconditions.checkNotNull(that, "null that");
        Preconditions.checkArgument(this.count >= that.count, "subtracting too many data points");
        if (that.count == 0)
            return this.clone();
        final int resultCount = this.count - that.count;
        final double sigma = (this.mean - that.mean) * this.count / (double)resultCount;
        final double resultMean = sigma + that.mean;
        final double resultM2 = resultCount > 1 ?
          this.m2 - that.m2 - sigma * sigma * (that.count * resultCount) / (double)this.count : 0;
        return new Averager(resultCount, resultMean, resultM2);
    }

    /**
     * Reset this instance.
     */
    public void reset() {
        this.count = 0;
        this.mean = 0;
        this.m2 = 0;
    }

    /**
     * Get the number of data points added so far.
     *
     * @return the number of data points added to this instance
     */
    public int size() {
        return this.count;
    }

    /**
     * Determine whether any data points have been added to this instance yet.
     *
     * @return true if no data points have been added yet
     */
    public boolean isEmpty() {
        return this.count == 0;
    }

    /**
     * Get the average of all the data points added to this instance so far.
     *
     * @return average value, or empty if no data points have been added yet
     */
    public OptionalDouble getAverage() {
        if (this.count == 0)
            return OptionalDouble.empty();
        return OptionalDouble.of(this.mean);
    }

    /**
     * Get the population variance.
     *
     * @return population variance, or empty if no data points have been added yet
     */
    public OptionalDouble getVariance() {
        if (this.count == 0)
            return OptionalDouble.empty();
        return OptionalDouble.of(this.m2 / this.count);
    }

    /**
     * Get the population standard deviation.
     *
     * @return population standard deviation, or empty if no data points have been added yet
     */
    public OptionalDouble getStandardDeviation() {
        if (this.count == 0)
            return OptionalDouble.empty();
        return OptionalDouble.of(Math.sqrt(this.m2 / this.count));
    }

    /**
     * Get the sample (unbiased estimated) variance.
     *
     * @return sample variance, or empty if less than two data points have been added yet
     */
    public OptionalDouble getSampleVariance() {
        if (this.count < 2)
            return OptionalDouble.empty();
        return OptionalDouble.of(this.m2 / (this.count - 1));
    }

    /**
     * Get the sample (unbiased estimated) standard deviation.
     *
     * @return population standard deviation, or empty if less than two data points have been added yet
     */
    public OptionalDouble getSampleStandardDeviation() {
        if (this.count < 2)
            return OptionalDouble.empty();
        return OptionalDouble.of(Math.sqrt(this.m2 / (this.count - 1)));
    }

    /**
     * Get the coefficient of variation (the standard deviation divided by the average).
     *
     * <p>
     * Note: if the average is zero, then {@link Double#NaN} is returned.
     *
     * @return coefficient of variation, or empty if no data points have been added yet
     */
    public OptionalDouble getCoefficientOfVariation() {
        if (this.count == 0)
            return OptionalDouble.empty();
        return OptionalDouble.of(Math.sqrt(this.m2 / this.count) / this.mean);
    }

// Object

    @Override
    public String toString() {
        if (this.count == 0)
            return "no data";
        return String.format(Locale.US, "num=%d, avg=%f, stddev=%f",
          this.count, this.getAverage().getAsDouble(), this.getStandardDeviation().getAsDouble());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final Averager that = (Averager)obj;
        return this.count == that.count
          && Double.compare(this.mean, that.mean) == 0
          && Double.compare(this.m2, that.m2) == 0;
    }

    @Override
    public int hashCode() {
        return this.count
          ^ Double.hashCode(this.mean)
          ^ Double.hashCode(this.m2);
    }

// Cloneable

    @Override
    public Averager clone() {
        try {
            return (Averager)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException();
        }
    }
}
