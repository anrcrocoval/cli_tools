package fr.univ_nantes.stats.model_deviation;

import dagger.Module;
import dagger.Provides;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.general.interior_point.InteriorPointRigid2DGeneralMaxLikelihoodComputer;

import javax.inject.Named;

@Module
public class MainModule {

    @Provides
    @Named("ipopt_general")
    public Rigid2DMaxLikelihoodComputer getIpoptGeneralSolver(InteriorPointRigid2DGeneralMaxLikelihoodComputer solver) {
        return solver;
    }
}
