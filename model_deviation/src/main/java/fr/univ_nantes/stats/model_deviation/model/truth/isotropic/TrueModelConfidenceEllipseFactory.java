package fr.univ_nantes.stats.model_deviation.model.truth.isotropic;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import fr.univ_nantes.stats.model_deviation.model.truth.ChiSquaredEstimator;
import plugins.fr.univ_nantes.ec_clem.error.ellipse.Ellipse;
import plugins.fr.univ_nantes.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.fiducialset.dataset.point.Point;
import plugins.fr.univ_nantes.ec_clem.transformation.TransformationFactory;
import javax.inject.Inject;

public class TrueModelConfidenceEllipseFactory {

    private TransformationFactory transformationFactory;
    private ChiSquaredEstimator chiSquaredEstimator;

    @Inject
    public TrueModelConfidenceEllipseFactory(
        TransformationFactory transformationFactory,
        ChiSquaredEstimator chiSquaredEstimator
    ) {
        this.transformationFactory = transformationFactory;
        this.chiSquaredEstimator = chiSquaredEstimator;
    }

    public Ellipse getFrom(Point zTarget, FiducialSet fiducialSet, double[][] covariance, double alpha) {
        EigenvalueDecomposition eigenValueDecomposition = new EigenvalueDecomposition(
            (new Matrix(covariance)).times(
                    chiSquaredEstimator.getFrom(fiducialSet, alpha)
                )
        );
        return new Ellipse(
            eigenValueDecomposition.getRealEigenvalues(),
            eigenValueDecomposition.getV(),
            zTarget
        );
    }
}
