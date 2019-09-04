package fr.univ_nantes.stats.tre_half_normal;

import Jama.Matrix;
import fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import picocli.CommandLine;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.registration.AffineTransformationComputer;
import plugins.perrine.easyclemv0.transformation.AffineTransformation;
import plugins.perrine.easyclemv0.transformation.Similarity;
import javax.inject.Inject;
import java.util.concurrent.*;

@CommandLine.Command(name = "tre_half_normal")
public class Main implements Runnable {

    private TestTransformationFactory testTransformationFactory;
    private TestFiducialSetFactory testFiducialSetFactory;
    private AffineTransformationComputer affineTransformationComputer;

    @CommandLine.Option(
        names = { "-n" },
        description = "Number of points. Values will range from 1 to n². Default : ${DEFAULT-VALUE}.",
        defaultValue = "10"
    )
    private int n;

    @CommandLine.Option(
        names = { "-p" },
        description = "Number of points. Values will range from 1 to p³. Default : ${DEFAULT-VALUE}.",
        defaultValue = "10"
    )
    private int p;

    @CommandLine.Option(
            names = { "-s" },
            description = "Noise variance sigma². Default : ${DEFAULT-VALUE}.",
            defaultValue = "100"
    )
    private int s;

    @CommandLine.Option(
        names = {"-h", "--help"},
        usageHelp = true,
        description = "Display this help message."
    )
    private boolean help;

    public Main() {
        DaggerMainComponent.create().inject(this);
    }

    @Override
    public void run() {
        double angle = 38;
        Similarity simpleRotationTransformation = testTransformationFactory.getSimpleRotationTransformation(angle);
        FiducialSet randomFromTransformationFiducialSet = testFiducialSetFactory.getRandomFromTransformation(
                simpleRotationTransformation, n * n + 1
        );
        Matrix error = new Matrix(p * n, 6);
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CompletionService<Runnable> completionService = new ExecutorCompletionService<>(executorService);
        for(int i = 0; i < p; i++) {
            final int finalI = i;
            for(int j = 0; j < n; j++) {
                final int finalJ = j;
                completionService.submit(() -> {
                    int nbIter = (finalI + 1) * (finalI + 1) * (finalI + 1);
                    int nbPoints = (finalJ + 1) * (finalJ + 1);
                    Mean mean = new Mean();
                    Variance variance = new Variance();
                    Mean mean2 = new Mean();
                    Variance variance2 = new Variance();
                    for(int current = 0; current < nbIter; current++) {
                        FiducialSet currentFiducialSet = randomFromTransformationFiducialSet.clone();
                        testFiducialSetFactory.addGaussianNoise(
                                currentFiducialSet.getTargetDataset(), (float) Math.sqrt(s)
                        );
                        Point targetRemovedPoint = currentFiducialSet.getTargetDataset().removePoint(0);
                        Point sourceRemovedPoint = currentFiducialSet.getSourceDataset().removePoint(0);
                        for(int k = n * n; k > nbPoints; k--) {
                            currentFiducialSet.remove(0);
                        }
                        AffineTransformation compute = affineTransformationComputer.compute(currentFiducialSet);
                        Point minus = targetRemovedPoint.minus(compute.apply(sourceRemovedPoint));
                        mean.increment(minus.getSumOfSquare());
                        variance.increment(minus.getSumOfSquare());
                        mean2.increment(Math.sqrt(minus.getSumOfSquare()));
                        variance2.increment(Math.sqrt(minus.getSumOfSquare()));
                    }

                    error.set(finalI * n + finalJ, 0, nbIter);
                    error.set(finalI * n + finalJ, 1, nbPoints);
                    error.set(finalI * n + finalJ, 2, mean.getResult());
                    error.set(finalI * n + finalJ, 3, variance.getResult());
                    error.set(finalI * n + finalJ, 4, mean2.getResult());
                    error.set(finalI * n + finalJ, 5, variance2.getResult());
                }, null);
            }
        }

        for(int i = 0; i < p; i++) {
            for(int j = 0; j < n; j++) {
                try {
                    completionService.take().get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }
        executorService.shutdown();
        error.print(1,5);
    }

    public static void main(String ... args){
        new CommandLine(new Main()).execute(args);
    }

    @Inject
    public void setTestTransformationFactory(TestTransformationFactory testTransformationFactory) {
        this.testTransformationFactory = testTransformationFactory;
    }

    @Inject
    public void setTestFiducialSetFactory(TestFiducialSetFactory testFiducialSetFactory) {
        this.testFiducialSetFactory = testFiducialSetFactory;
    }

    @Inject
    public void setAffineTransformationComputer(AffineTransformationComputer affineTransformationComputer) {
        this.affineTransformationComputer = affineTransformationComputer;
    }
}
