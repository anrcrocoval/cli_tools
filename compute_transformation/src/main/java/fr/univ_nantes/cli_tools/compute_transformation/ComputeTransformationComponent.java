package fr.univ_nantes.cli_tools.compute_transformation;

import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component()
public interface ComputeTransformationComponent {
    void inject(ComputeTransformation main);
}
