package fr.univ_nantes.stats.mean_error;

import Jama.Matrix;
import fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import picocli.CommandLine;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.Dataset;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.registration.AffineTransformationComputer;
import plugins.perrine.easyclemv0.transformation.AffineTransformation;
import plugins.perrine.easyclemv0.transformation.Similarity;

import javax.inject.Inject;
import java.util.concurrent.*;

@CommandLine.Command(name = "mean_error")
public class Main implements Runnable {

    private TestTransformationFactory testTransformationFactory;
    private TestFiducialSetFactory testFiducialSetFactory;
    private AffineTransformationComputer affineTransformationComputer;

    @CommandLine.Option(
        names = { "-n" },
        description = "Number of iterations, default : ${DEFAULT-VALUE}",
        defaultValue = "100"
    )
    private int n;

    @CommandLine.Option(
        names = { "-p" },
        description = "Number of points, default : ${DEFAULT-VALUE}",
        defaultValue = "50"
    )
    private int iter;

    @CommandLine.Option(
        names = {"-h", "--help"},
        usageHelp = true,
        description = "display this help message"
    )
    private boolean usageHelpRequested;

    public Main() {
        DaggerMainComponent.create().inject(this);
    }

    @Override
    public void run() {
        double angle = 38;
        Similarity simpleRotationTransformation = testTransformationFactory.getSimpleRotationTransformation(angle);
        FiducialSet randomFromTransformationFiducialSet = testFiducialSetFactory.getGaussianAroundCenterOfGravityFromTransformation(
                simpleRotationTransformation, n * n + 1
        );
        Matrix error = new Matrix(iter * n, 4);
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CompletionService<Runnable> completionService = new ExecutorCompletionService<>(executorService);
        for(int i = 0; i < iter; i++) {
            final int finalI = i;
            for(int j = 0; j < n; j++) {
                final int finalJ = j;
                completionService.submit(() -> {
                    int nbIter = (finalI + 1) * (finalI + 1);
                    int nbPoints = (finalJ + 1) * (finalJ + 1);
                    Matrix currentError = new Matrix(nbIter, 3);
                    for(int current = 0; current < nbIter; current++) {
                        FiducialSet currentFiducialSet = new FiducialSet(
                                randomFromTransformationFiducialSet.getSourceDataset().clone(),
                                randomFromTransformationFiducialSet.getTargetDataset().clone()
                        );
                        testFiducialSetFactory.addGaussianNoise(
                                currentFiducialSet.getTargetDataset()
                        );
                        Point targetRemovedPoint = currentFiducialSet.getTargetDataset().removePoint(0);
                        Point sourceRemovedPoint = currentFiducialSet.getSourceDataset().removePoint(0);

                        for(int k = n * n; k > nbPoints; k--) {
                            currentFiducialSet.getTargetDataset().removePoint(0);
                            currentFiducialSet.getSourceDataset().removePoint(0);
                        }

                        FiducialSet finalCurrentFiducialSet = new FiducialSet(
                                currentFiducialSet.getSourceDataset(),
                                currentFiducialSet.getTargetDataset()
                        );
                        AffineTransformation compute = affineTransformationComputer.compute(finalCurrentFiducialSet);
                        Matrix minus = randomFromTransformationFiducialSet.getTargetDataset().getPoint(0).getMatrix().minus(
                                new Point(compute.apply(sourceRemovedPoint).getMatrix()).getMatrix()
                        );
                        currentError.setMatrix(current, current, 0, 2, minus.transpose());
                    }

                    Dataset d = new Dataset(currentError);
                    Mean mean = new Mean();
                    mean.clear();
                    Variance variance = new Variance();
                    variance.clear();

                    for(int p = 0; p < d.getN(); p++) {
                        double sumOfSquare = d.getPoint(p).getSumOfSquare();
                        mean.increment(sumOfSquare);
                        variance.increment(sumOfSquare);
                    }

                    error.set(finalI * n + finalJ, 0, nbIter);
                    error.set(finalI * n + finalJ, 1, nbPoints);
                    error.set(finalI * n + finalJ, 2, mean.getResult());
                    error.set(finalI * n + finalJ, 3, variance.getResult());
                }, null);
            }
        }

        for(int i = 0; i < iter; i++) {
            for(int j = 0; j < n; j++) {
                try {
                    completionService.take().get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }
        error.print(1,5);
    }

    public static void main(String ... args){
        int exitCode = new CommandLine(new Main()).execute(args);
        assert exitCode == 0;
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
