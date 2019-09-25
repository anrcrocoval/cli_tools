package fr.univ_nantes.stats.model_deviation.model.estimated.rigid;

import fr.univ_nantes.stats.model_deviation.model.ConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.estimated.HotellingEstimator;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.registration.RigidTransformationComputer;
import plugins.perrine.easyclemv0.transformation.Similarity;
import javax.inject.Inject;
import java.awt.Shape;

public class RigidConfidenceEllipseFactory {

    private ConfidenceEllipseFactory confidenceEllipseFactory;
    private RigidTransformationComputer rigidTransformationComputer;
    private RigidCovarianceEstimator rigidCovarianceEstimator;
    private HotellingEstimator hotellingEstimator;

    @Inject
    public RigidConfidenceEllipseFactory(ConfidenceEllipseFactory confidenceEllipseFactory, RigidTransformationComputer rigidTransformationComputer, RigidCovarianceEstimator rigidCovarianceEstimator, HotellingEstimator hotellingEstimator) {
        this.confidenceEllipseFactory = confidenceEllipseFactory;
        this.rigidTransformationComputer = rigidTransformationComputer;
        this.rigidCovarianceEstimator = rigidCovarianceEstimator;
        this.hotellingEstimator = hotellingEstimator;
    }

    public Shape getFrom(Point zSource, FiducialSet fiducialSet, double alpha, int height) {
        Similarity transformation = rigidTransformationComputer.compute(fiducialSet);
        Point zTarget = transformation.apply(zSource);
        return confidenceEllipseFactory.getFrom(
            zTarget,
            rigidCovarianceEstimator.getCovariance(transformation, fiducialSet, zSource),
            hotellingEstimator.getFrom(fiducialSet, alpha),
            height
        );
    }
}
