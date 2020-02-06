package fr.univ_nantes.stats.model_deviation;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

public class ShapeStat {
    private Mean area = new Mean();
    private StandardDeviation areaSd = new StandardDeviation();
    private int counter = 0;
    private int n = 0;

    public synchronized void updateCounter(boolean shouldUpdate) {
        if(shouldUpdate) {
            counter++;
        }
        n++;
    }

    public synchronized void updateArea(double area) {
        this.area.increment(area);
        this.areaSd.increment(area);
    }

    public double getRatio() {
        return (double) counter / n;
    }

    public double getArea() {
        return area.getResult();
    }

    public double getAreaSd() {
        return areaSd.getResult();
    }
}
