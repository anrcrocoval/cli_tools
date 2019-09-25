package fr.univ_nantes.stats.model_deviation.model.estimated.affine;

import Jama.Matrix;
import fr.univ_nantes.stats.model_deviation.model.estimated.CovarianceEstimator;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.matrix.MatrixUtil;
import plugins.perrine.easyclemv0.transformation.Transformation;
import javax.inject.Inject;

public class AffineCovarianceEstimator implements CovarianceEstimator {

    private MatrixUtil matrixUtil;

    @Inject
    public AffineCovarianceEstimator(MatrixUtil matrixUtil) {
        this.matrixUtil = matrixUtil;
    }

    @Override
    public Matrix getCovariance(Transformation transformation, FiducialSet fiducialSet, Point zSource) {
        double coeff = (
            zSource.getMatrix().transpose().times(
                matrixUtil.pseudoInverse(
                    fiducialSet.getSourceDataset().getMatrix().transpose().times(
                        fiducialSet.getSourceDataset().getMatrix()
                    )
                )
            ).times(zSource.getMatrix()).get(0, 0) + 1
        );

        Matrix residuals = fiducialSet.getTargetDataset().getMatrix().minus(
            transformation.apply(fiducialSet.getSourceDataset()).getMatrix()
        );

        return residuals.transpose().times(residuals)
            .times((double) 1 / (fiducialSet.getN() - fiducialSet.getSourceDataset().getDimension() - fiducialSet.getSourceDataset().getDimension()))
            .times(coeff);
    }
}
