package fr.univ_nantes.stats.model_deviation.model;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;

public class ConfidenceEllipseFactory {

    @Inject
    public ConfidenceEllipseFactory() {}

    public Shape getFrom(Point zTarget, Matrix covarianceEstimate, double hotellingEstimate, int height) {
        EigenvalueDecomposition eigenvalueDecomposition = new EigenvalueDecomposition(
            covarianceEstimate.times(hotellingEstimate)
        );
        double[] eigenValues = eigenvalueDecomposition.getRealEigenvalues();
        double a = Math.sqrt(eigenValues[0]);
        double b = Math.sqrt(eigenValues[1]);
        return AffineTransform.getTranslateInstance(zTarget.get(0), height - zTarget.get(1)).createTransformedShape(
            AffineTransform.getRotateInstance(-1 * Math.atan2(
                eigenvalueDecomposition.getV().get(1, 0),
                eigenvalueDecomposition.getV().get(0, 0)
            )).createTransformedShape(
                new Ellipse2D.Double(-a, -b, 2 * a, 2 * b)
            )
        );
    }
}
