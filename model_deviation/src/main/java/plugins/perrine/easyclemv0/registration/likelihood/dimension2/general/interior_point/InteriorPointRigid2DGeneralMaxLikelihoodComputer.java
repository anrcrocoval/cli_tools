package plugins.perrine.easyclemv0.registration.likelihood.dimension2.general.interior_point;

import org.coinor.Ipopt;
import plugins.fr.univ_nantes.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.general.BaseOptimProblem;
import plugins.perrine.easyclemv0.registration.likelihood.dimension2.IpoptSolver;

import javax.inject.Inject;

public class InteriorPointRigid2DGeneralMaxLikelihoodComputer extends Rigid2DMaxLikelihoodComputer {

    @Inject
    public InteriorPointRigid2DGeneralMaxLikelihoodComputer() {
        super();
        DaggerInteriorPointRigid2DGeneralLikelihoodComputerComponent.create().inject(this);
    }

    @Override
    protected double[] optimize(FiducialSet fiducialSet) {
        Ipopt ipopt = new IpoptSolver(new BaseOptimProblem(fiducialSet));
        ipopt.OptimizeNLP();
        return ipopt.getVariableValues();
    }
}
