package fr.univ_nantes.stats.model_deviation.model.estimated;

import Jama.Matrix;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.transformation.Transformation;

public interface CovarianceEstimator {
    Matrix getCovariance(Transformation transformation, FiducialSet fiducialSet, Point z);
}
