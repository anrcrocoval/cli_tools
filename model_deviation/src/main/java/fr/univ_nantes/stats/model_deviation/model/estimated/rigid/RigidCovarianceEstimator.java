package fr.univ_nantes.stats.model_deviation.model.estimated.rigid;

import Jama.Matrix;
import fr.univ_nantes.stats.model_deviation.model.estimated.CovarianceEstimator;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.matrix.MatrixUtil;
import plugins.perrine.easyclemv0.transformation.Similarity;
import plugins.perrine.easyclemv0.transformation.Transformation;
import javax.inject.Inject;

public class RigidCovarianceEstimator implements CovarianceEstimator {

    private MatrixUtil matrixUtil;

    @Inject
    public RigidCovarianceEstimator(MatrixUtil matrixUtil) {
        this.matrixUtil = matrixUtil;
    }

    @Override
    public Matrix getCovariance(Transformation transformation, FiducialSet fiducialSet, Point z) {
        Matrix residuals = fiducialSet.getTargetDataset().getMatrix().minus(
            transformation.apply(fiducialSet.getSourceDataset()).getMatrix()
        );
        Matrix lambda = residuals.transpose().times(residuals).times((double) 1 / (fiducialSet.getN()));
        Matrix lambdaInv = matrixUtil.pseudoInverse(lambda);

        Matrix jtt = lambdaInv.times(-1 * fiducialSet.getN());
        Matrix jto = new Matrix(2, 1, 0);
        Matrix joo = new Matrix(1, 1, 0);

        RotationParameters2D rotationParameters2D = new RotationParameters2D((Similarity) transformation);
        Matrix tmp = new Matrix(new double[][]{
            { Math.sin(rotationParameters2D.getTheta()), Math.cos(rotationParameters2D.getTheta()) },
            { -1 * Math.cos(rotationParameters2D.getTheta()), Math.sin(rotationParameters2D.getTheta()) }
        });
        Matrix tmp2 = new Matrix(new double[][]{
            { Math.cos(rotationParameters2D.getTheta()), -1 * Math.sin(rotationParameters2D.getTheta()) },
            { -1 * Math.sin(rotationParameters2D.getTheta()), Math.cos(rotationParameters2D.getTheta()) }
        });
        for(int i = 0; i < fiducialSet.getN(); i++) {
            Matrix y = new Matrix(new double[][]{
                { fiducialSet.getTargetDataset().getMatrix().get(i, 1) },
                { fiducialSet.getTargetDataset().getMatrix().get(i, 0) }
            });
            Matrix x = new Matrix(new double[][]{
                { fiducialSet.getSourceDataset().getMatrix().get(i, 1) },
                { fiducialSet.getSourceDataset().getMatrix().get(i, 0) }
            });
            jto.plusEquals(lambdaInv.times(tmp.times(x)));
            joo.plusEquals(
                tmp.times(x).transpose().times(lambdaInv).times(tmp.times(x)).plus(
                    tmp2.times(x).transpose().times(lambdaInv).times(y.minus(transformation.apply(new Point(x)).getMatrix()))
                ));
        }

        Matrix J = new Matrix(3, 3);
        J.setMatrix(0, 1, 0, 1, jtt.times(-1));
        J.setMatrix(0, 1, 2, 2, jto.times(-1));
        J.setMatrix(2, 2, 0, 1, jto.transpose().times(-1));
        J.setMatrix(2, 2, 2, 2, joo.times(-1));

        Matrix sigma = matrixUtil.pseudoInverse(J);

        Matrix E = new Matrix(2,2);
        E.set(0, 0,
            z.get(1) * z.get(1) * sigma.get(2, 2) - 2 * z.get(1) * sigma.get(0, 2) + sigma.get(0, 0)
        );
        E.set(0, 1,
            -1 * z.get(0) * z.get(1) * sigma.get(2, 2) - z.get(1) * sigma.get(1, 2) + z.get(0) * sigma.get(0, 2) + sigma.get(0, 1)
        );
        E.set(1, 0, E.get(0, 1) * -1);
        E.set(1, 1,
            z.get(0) * z.get(0) * sigma.get(2, 2) + 2 * z.get(0) * sigma.get(1, 2) + sigma.get(1, 1)
        );
        return E.plus(lambda);
    }
}
