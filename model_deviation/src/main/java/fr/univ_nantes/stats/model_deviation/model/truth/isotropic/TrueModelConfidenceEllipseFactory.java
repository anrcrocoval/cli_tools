package fr.univ_nantes.stats.model_deviation.model.truth.isotropic;

import Jama.Matrix;
import fr.univ_nantes.stats.model_deviation.model.ConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.truth.ChiSquaredEstimator;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import javax.inject.Inject;
import java.awt.Shape;

public class TrueModelConfidenceEllipseFactory {

    private ConfidenceEllipseFactory confidenceEllipseFactory;
    private ChiSquaredEstimator chiSquaredEstimator;

    @Inject
    public TrueModelConfidenceEllipseFactory(ConfidenceEllipseFactory confidenceEllipseFactory , ChiSquaredEstimator chiSquaredEstimator) {
        this.confidenceEllipseFactory = confidenceEllipseFactory;
        this.chiSquaredEstimator = chiSquaredEstimator;
    }

    public Shape getFrom(Point zTarget, FiducialSet fiducialSet, double alpha, int height, double[][] covariance) {
        return confidenceEllipseFactory.getFrom(
            zTarget,
            new Matrix(covariance),
            chiSquaredEstimator.getFrom(fiducialSet, alpha),
            height
        );
    }
}
