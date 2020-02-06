package fr.univ_nantes.cli.model_deviation.model.truth.isotropic;

import Jama.Matrix;
import javax.inject.Inject;

public class IsotropicCovarianceFactory {

    @Inject
    public IsotropicCovarianceFactory() {}

    public Matrix getFrom(int dimension, double sigma) {
        return Matrix.identity(
            dimension,
            dimension
        ).times(sigma * sigma);
    }
}
