package fr.univ_nantes.stats.model_deviation;

import dagger.Component;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DCovarianceMaxLikelihoodComputerModule;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DMaxLikelihoodComputerModule;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
    Rigid2DMaxLikelihoodComputerModule.class,
    Rigid2DCovarianceMaxLikelihoodComputerModule.class,
})
public interface MainComponent {
    void inject(Main main);
}
