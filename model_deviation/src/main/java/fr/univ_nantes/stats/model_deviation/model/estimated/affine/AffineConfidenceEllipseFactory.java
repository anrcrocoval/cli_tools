package fr.univ_nantes.stats.model_deviation.model.estimated.affine;

import fr.univ_nantes.stats.model_deviation.model.ConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.estimated.HotellingEstimator;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.registration.AffineTransformationComputer;
import plugins.perrine.easyclemv0.transformation.AffineTransformation;
import javax.inject.Inject;
import java.awt.Shape;

public class AffineConfidenceEllipseFactory {

    private ConfidenceEllipseFactory confidenceEllipseFactory;
    private AffineTransformationComputer affineTransformationComputer;
    private AffineCovarianceEstimator affineCovarianceEstimator;
    private HotellingEstimator hotellingEstimator;

    @Inject
    public AffineConfidenceEllipseFactory(ConfidenceEllipseFactory confidenceEllipseFactory, AffineTransformationComputer affineTransformationComputer, AffineCovarianceEstimator affineCovarianceEstimator, HotellingEstimator hotellingEstimator) {
        this.confidenceEllipseFactory = confidenceEllipseFactory;
        this.affineTransformationComputer = affineTransformationComputer;
        this.affineCovarianceEstimator = affineCovarianceEstimator;
        this.hotellingEstimator = hotellingEstimator;
    }

    public Shape getFrom(Point zSource, FiducialSet fiducialSet, double alpha, int height) {
        AffineTransformation transformation = affineTransformationComputer.compute(fiducialSet);
        Point zTarget = transformation.apply(zSource);
        return confidenceEllipseFactory.getFrom(
            zTarget,
            affineCovarianceEstimator.getCovariance(transformation, fiducialSet, zSource),
            hotellingEstimator.getFrom(fiducialSet, alpha),
            height
        );
    }
}
