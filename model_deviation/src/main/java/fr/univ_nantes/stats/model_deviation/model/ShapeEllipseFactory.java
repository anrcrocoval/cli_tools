package fr.univ_nantes.stats.model_deviation.model;

import plugins.fr.univ_nantes.ec_clem.error.ellipse.Ellipse;
import plugins.fr.univ_nantes.ec_clem.fiducialset.dataset.point.Point;
import javax.inject.Inject;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.util.Arrays;

public class ShapeEllipseFactory {

    @Inject
    public ShapeEllipseFactory() {}

    public Shape getFrom(Ellipse ellipse, int height) {
        double[] eigenValues = ellipse.getEigenValues();
//        System.out.println(Arrays.toString(eigenValues));
        Point zTarget = ellipse.getCenter();
        double a = Math.sqrt(eigenValues[0]);
        double b = Math.sqrt(eigenValues[1]);
        return AffineTransform.getTranslateInstance(zTarget.get(0), height - zTarget.get(1)).createTransformedShape(
            AffineTransform.getRotateInstance(-1 * Math.atan2(
                ellipse.getEigenVectors().get(1, 0),
                ellipse.getEigenVectors().get(0, 0)
            )).createTransformedShape(
                new Ellipse2D.Double(-a, -b, 2 * a, 2 * b)
            )
        );
    }

    public Shape getFrom(Point zTarget, double radius, int height) {
        return AffineTransform.getTranslateInstance(zTarget.get(0), height - zTarget.get(1)).createTransformedShape(
            new Ellipse2D.Double(-radius, -radius, 2 * radius, 2 * radius)
        );
    }
}
