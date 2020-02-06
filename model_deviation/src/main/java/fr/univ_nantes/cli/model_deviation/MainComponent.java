package fr.univ_nantes.cli.model_deviation;

import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component()
public interface MainComponent {
    void inject(Main main);
}
