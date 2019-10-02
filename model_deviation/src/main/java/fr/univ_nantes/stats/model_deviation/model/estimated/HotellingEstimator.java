package fr.univ_nantes.stats.model_deviation.model.estimated;

import org.apache.commons.math3.distribution.FDistribution;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import javax.inject.Inject;

public class HotellingEstimator {

    @Inject
    public HotellingEstimator() {}

    public double getFrom(FiducialSet fiducialSet, double alpha) {
        FDistribution fisher = new FDistribution(
            fiducialSet.getTargetDataset().getDimension(),
            fiducialSet.getN() - fiducialSet.getSourceDataset().getDimension() - fiducialSet.getTargetDataset().getDimension()
        );

        return  (double) (
            fiducialSet.getTargetDataset().getDimension()
                * (fiducialSet.getN() - fiducialSet.getSourceDataset().getDimension() - 1)
        ) / (
            fiducialSet.getN() - fiducialSet.getSourceDataset().getDimension()
                - fiducialSet.getTargetDataset().getDimension()
        ) * fisher.inverseCumulativeProbability(alpha);
    }
}
