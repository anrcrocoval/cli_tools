package fr.univ_nantes.stats.model_deviation.model.truth.isotropic;

import fr.univ_nantes.stats.model_deviation.model.ConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.truth.ChiSquaredEstimator;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import javax.inject.Inject;
import java.awt.Shape;

public class IsotropicConfidenceEllipseFactory {

    private ConfidenceEllipseFactory confidenceEllipseFactory;
    private IsotropicCovarianceFactory isotropicCovarianceFactory;
    private ChiSquaredEstimator chiSquaredEstimator;

    @Inject
    public IsotropicConfidenceEllipseFactory(ConfidenceEllipseFactory confidenceEllipseFactory, IsotropicCovarianceFactory isotropicCovarianceFactory, ChiSquaredEstimator chiSquaredEstimator) {
        this.confidenceEllipseFactory = confidenceEllipseFactory;
        this.isotropicCovarianceFactory = isotropicCovarianceFactory;
        this.chiSquaredEstimator = chiSquaredEstimator;
    }

    public Shape getFrom(Point zTarget, FiducialSet fiducialSet, double alpha, int height, double sigma) {
        return confidenceEllipseFactory.getFrom(
            zTarget,
            isotropicCovarianceFactory.getFrom(fiducialSet.getTargetDataset().getDimension(), sigma),
            chiSquaredEstimator.getFrom(fiducialSet, alpha),
            height
        );
    }
}
