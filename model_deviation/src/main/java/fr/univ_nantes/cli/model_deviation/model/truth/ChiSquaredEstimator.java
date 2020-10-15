package fr.univ_nantes.cli.model_deviation.model.truth;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import plugins.fr.univ_nantes.ec_clem.ec_clem.fiducialset.FiducialSet;
import javax.inject.Inject;

public class ChiSquaredEstimator {

    @Inject
    public ChiSquaredEstimator() {}

    public double getFrom(FiducialSet fiducialSet, double alpha) {
        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(fiducialSet.getTargetDataset().getDimension());
        return chi2.inverseCumulativeProbability(alpha);
    }
}
