package fr.univ_nantes.stats.model_deviation.model.estimated.rigid;

import plugins.perrine.easyclemv0.transformation.Similarity;

public class RotationParameters2D {

    private double theta;

    public RotationParameters2D(Similarity similarity) {
        if(
            (similarity.getR().getRowDimension() !=
            similarity.getR().getColumnDimension()) ||
            similarity.getR().getColumnDimension() != 2
        ) {
            throw new RuntimeException("Use this class with 2D rotations");
        }
        theta = Math.atan2(similarity.getR().get(1, 0), similarity.getR().get(0, 0));
    }

    public double getTheta() {
        return theta;
    }
}
