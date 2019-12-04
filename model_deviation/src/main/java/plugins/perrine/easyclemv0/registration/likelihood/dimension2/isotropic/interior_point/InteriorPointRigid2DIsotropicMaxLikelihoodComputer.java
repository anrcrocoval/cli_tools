package plugins.perrine.easyclemv0.registration.likelihood.dimension2.isotropic.interior_point;

import org.coinor.Ipopt;
import plugins.fr.univ_nantes.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.isotropic.ConstrainedOptimProblem;
import plugins.perrine.easyclemv0.registration.likelihood.dimension2.IpoptSolver;
import javax.inject.Inject;

public class InteriorPointRigid2DIsotropicMaxLikelihoodComputer extends Rigid2DMaxLikelihoodComputer {

    @Inject
    public InteriorPointRigid2DIsotropicMaxLikelihoodComputer() {
        super();
        DaggerInteriorPointRigid2DIsotropicLikelihoodComputerComponent.create().inject(this);
    }

    @Override
    protected double[] optimize(FiducialSet fiducialSet) {
        Ipopt ipopt = new IpoptSolver(new ConstrainedOptimProblem(fiducialSet));
        ipopt.OptimizeNLP();
        return ipopt.getVariableValues();
    }
}
