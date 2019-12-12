package plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.isotropic.interior_point;

import org.coinor.Ipopt;
import plugins.fr.univ_nantes.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.matrix.MatrixUtil;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.isotropic.ConstrainedOptimProblem;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.IpoptSolver;
import javax.inject.Inject;

public class InteriorPointRigid2DIsotropicMaxLikelihoodComputer extends Rigid2DMaxLikelihoodComputer {

    @Inject
    public InteriorPointRigid2DIsotropicMaxLikelihoodComputer(MatrixUtil matrixUtil) {
        super(matrixUtil);
//        DaggerInteriorPointRigid2DIsotropicLikelihoodComputerComponent.create().inject(this);
    }

    @Override
    protected double[] optimize(FiducialSet fiducialSet) {
        ConstrainedOptimProblem optimProblem = new ConstrainedOptimProblem(fiducialSet);
        Ipopt ipopt = new IpoptSolver(optimProblem);
        ipopt.OptimizeNLP();
        optimProblem.close();
        return ipopt.getVariableValues();
    }
}
