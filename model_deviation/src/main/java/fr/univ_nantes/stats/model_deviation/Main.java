package fr.univ_nantes.stats.model_deviation;

import Jama.Matrix;
import fr.univ_nantes.stats.model_deviation.model.ShapeEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.truth.isotropic.TrueModelConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.DaggerMainComponent;
import icy.sequence.DimensionId;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import picocli.CommandLine;
import javax.inject.Inject;
import javax.inject.Named;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.Color;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import plugins.fr.univ_nantes.ec_clem.error.ellipse.ConfidenceEllipseFactory;
import plugins.fr.univ_nantes.ec_clem.error.likelihood_ratio.LikelihoodRatioTest;
import plugins.fr.univ_nantes.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.fiducialset.dataset.point.Point;
import plugins.fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import plugins.fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import plugins.fr.univ_nantes.ec_clem.registration.AffineRegistrationParameterComputer;
import plugins.fr.univ_nantes.ec_clem.registration.RegistrationParameter;
import plugins.fr.univ_nantes.ec_clem.registration.RigidRegistrationParameterComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.general.conjugate_gradient.ConjugateGradientRigid2DGeneralMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.sequence.DimensionSize;
import plugins.fr.univ_nantes.ec_clem.sequence.SequenceSize;
import plugins.fr.univ_nantes.ec_clem.transformation.AffineTransformation;
import plugins.fr.univ_nantes.ec_clem.transformation.Transformation;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.NoiseModel;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationSchema;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationType;

import static java.lang.Double.max;

@Command(
    name = "model_deviation",
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true
)
public class Main {

    private TestTransformationFactory testTransformationFactory;
    private TestFiducialSetFactory testFiducialSetFactory;
    private AffineRegistrationParameterComputer affineTransformationComputer;
    private RigidRegistrationParameterComputer rigidTransformationComputer;
//    private ConjugateGradientRigid2DGeneralMaxLikelihoodComputer anisotripicRigidTransformationComputer;

    private Rigid2DMaxLikelihoodComputer anisotripicRigidTransformationComputer;
    private Rigid2DMaxLikelihoodComputer isotripicRigidTransformationComputer;
    private ConfidenceEllipseFactory confidenceEllipseFactory;
    private ShapeEllipseFactory shapeEllipseFactory;
    private TrueModelConfidenceEllipseFactory trueModelConfidenceEllipseFactory;

    @Option(
        names = { "-n" },
        description = "Number of points.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "10"
    )
    private int n;

    @Option(
        names = { "-a", "--alpha" },
        description = "Significance threshold.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "0.95"
    )
    private float alpha;

    @Option(
        names = {"--width"},
        description = "Image width.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "512"
    )
    private int width;

    @Option(
        names = {"--height"},
        description = "Image height.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "512"
    )
    private int height;

    @Option(
        names = {"-m", "--model"},
        description = "Transformation model.\nValid values : ${COMPLETION-CANDIDATES}.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "RIGID"
    )
    private TransformationType transformationType;

    @Option(
        names = "-S",
        description = "Noise covariance matrix.\nDefault : ${DEFAULT-VALUE}.",
        arity = "4"
    )
    private double[] noiseCovarianceValues;

    public Main() {
        DaggerMainComponent.create().inject(this);
    }

    private Transformation getRandomTransformation(TransformationType transformationType) {
        switch (transformationType) {
            case RIGID: return testTransformationFactory.getRandomSimpleRotationTransformation(2);
            case SIMILARITY: return testTransformationFactory.getRandomSimpleSimilarityTransformation2D();
            case AFFINE: return testTransformationFactory.getRandomSimpleAffineTransformation2D();
            default: throw new IllegalArgumentException("Not implemented");
        }
    }

    @Command
    public void image(
        @Option(
            names = {"-o", "--output"},
            description = "Output file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./image.png"
        ) Path outputFilePath
    ) {
        Image image = new Image(width, height);
        int[] range = new int[]{width, height};
        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, 2).getArray();

        Transformation simpleRotationTransformation = getRandomTransformation(transformationType);
        Point zSource = testFiducialSetFactory.getRandomPoint(range);
        Point zTargetWithoutNoise = simpleRotationTransformation.apply(zSource);
        Point zTarget = testFiducialSetFactory.addGaussianNoise(zTargetWithoutNoise, noiseCovariance);

        FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
            simpleRotationTransformation, n, range
        );

        testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), noiseCovariance);

        image.fill(image.center(getRectangle(zSource), zTargetWithoutNoise), Color.GREEN);
        image.fill(image.center(getRectangle(zTarget), zTargetWithoutNoise), Color.RED);


        Shape affineEllipse = shapeEllipseFactory.getFrom(
            confidenceEllipseFactory.getFrom(
                zSource,
                new TransformationSchema(current, TransformationType.AFFINE, NoiseModel.ANISOTROPIC, getSequenceSize(), getSequenceSize()),
                alpha
            ),
            height
        );
        image.draw(image.center(affineEllipse, zTargetWithoutNoise), Color.BLUE);
        image.fill(image.center(getRectangle(affineTransformationComputer.compute(current).getTransformation().apply(zSource)), zTargetWithoutNoise), Color.BLUE);

        Shape rigidEllipse = shapeEllipseFactory.getFrom(
            confidenceEllipseFactory.getFrom(
                zSource,
                new TransformationSchema(current, TransformationType.RIGID, NoiseModel.ISOTROPIC, getSequenceSize(), getSequenceSize()),
                alpha
            ),
            height
        );
        image.draw(image.center(rigidEllipse, zTargetWithoutNoise), Color.ORANGE);
        image.fill(image.center(getRectangle(rigidTransformationComputer.compute(current).getTransformation().apply(zSource)), zTargetWithoutNoise), Color.ORANGE);

//        Shape anisotropicRigidEllipse = shapeEllipseFactory.getFrom(
//            confidenceEllipseFactory.getFrom(
//                zSource,
//                new TransformationSchema(current, TransformationType.RIGID, NoiseModel.ANISOTROPIC, getSequenceSize(), getSequenceSize()),
//                alpha
//            ),
//            height
//        );
//        image.draw(image.center(anisotropicRigidEllipse, zTargetWithoutNoise), Color.CYAN);
//        image.fill(image.center(getRectangle(anisotripicRigidTransformationComputer.compute(current).getTransformation().apply(zSource)), zTargetWithoutNoise), Color.CYAN);

        Shape trueEllipse = shapeEllipseFactory.getFrom(
            trueModelConfidenceEllipseFactory.getFrom(zTargetWithoutNoise, current, noiseCovariance, alpha),
            height
        );
        image.draw(image.center(trueEllipse, zTargetWithoutNoise), Color.WHITE);
        image.fill(image.center(getRectangle(zTargetWithoutNoise), zTargetWithoutNoise), Color.WHITE);

        image.write(outputFilePath);
    }

    private Rectangle getRectangle(Point point) {
        return new Rectangle((int) point.get(0), (int) (height - point.get(1)), 5, 5);
    }

    private SequenceSize getSequenceSize() {
        SequenceSize sequenceSize = new SequenceSize();
        sequenceSize.add(new DimensionSize(DimensionId.X, width, 1));
        sequenceSize.add(new DimensionSize(DimensionId.Y, width, 1));
        return sequenceSize;
    }

    @Command
    public void simulation(
        @Option(
            names = { "-N" },
            description = "Number of iterrations. Default : ${DEFAULT-VALUE}.",
            defaultValue = "1000000"
        ) int N
    ) {
        int[] range = new int[]{width, height};

        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, 2).getArray();
        DescriptiveStatistics stat = new DescriptiveStatistics(50);

        CompletionService<SimulationResult> completionService = new ExecutorCompletionService<>(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()
        ));

        for (int i = 0; i < N; i++) {
            completionService.submit(() -> {
                Transformation simpleRotationTransformation = getRandomTransformation(transformationType);
                Point zSource = testFiducialSetFactory.getRandomPoint(range);
                FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
                    simpleRotationTransformation, n, range
                );
                testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), noiseCovariance);
                Point zTarget = testFiducialSetFactory.addGaussianNoise(simpleRotationTransformation.apply(zSource), noiseCovariance);
                Shape affineEllipse = shapeEllipseFactory.getFrom(
                    confidenceEllipseFactory.getFrom(
                        zSource,
                        new TransformationSchema(current, TransformationType.AFFINE, NoiseModel.ANISOTROPIC, getSequenceSize(), getSequenceSize()),
                        alpha
                    ),
                    height
                );
                Shape rigidEllipse = shapeEllipseFactory.getFrom(
                    confidenceEllipseFactory.getFrom(
                        zSource,
                        new TransformationSchema(current, TransformationType.RIGID, NoiseModel.ISOTROPIC, getSequenceSize(), getSequenceSize()),
                        alpha
                    ),
                    height
                );
                Shape anisotropicRigidEllipse = shapeEllipseFactory.getFrom(
                    confidenceEllipseFactory.getFrom(
                        zSource,
                        new TransformationSchema(current, TransformationType.RIGID, NoiseModel.ANISOTROPIC, getSequenceSize(), getSequenceSize()),
                        alpha
                    ),
                    height
                );
                Shape trueEllipse = shapeEllipseFactory.getFrom(
                    trueModelConfidenceEllipseFactory.getFrom(simpleRotationTransformation.apply(zSource), current, noiseCovariance, alpha),
                    height
                );
                return new SimulationResult(
                    affineEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1))),
                    rigidEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1))),
                    anisotropicRigidEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1))),
                    trueEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1))),
                    getEllipseArea(affineEllipse),
                    getEllipseArea(rigidEllipse),
                    getEllipseArea(anisotropicRigidEllipse),
                    getEllipseArea(trueEllipse)
                );
            });

//            if (affineEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
//                affineCounter++;
//            }
//            if (rigidEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
//                rigidCounter++;
//            }
//            if (anisotropicRigidEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
//                anisotropicRigidCounter++;
//            }
//            if (trueEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
//                trueCounter++;
//            }

//            affineArea += getEllipseArea(affineEllipse);
//            rigidArea += getEllipseArea(rigidEllipse);
//            anisotropicRigidArea += getEllipseArea(anisotropicRigidEllipse);
//            trueArea += getEllipseArea(trueEllipse);
        }

        int affineCounter = 0;
        int rigidCounter = 0;
        int anisotropicRigidCounter = 0;
        int trueCounter = 0;

        Mean affineArea = new Mean();
        Mean rigidArea = new Mean();
        Mean anisotropicRigidArea = new Mean();
        Mean trueArea = new Mean();
        for (int i = 0; i < N; i++) {
            double start = System.currentTimeMillis();
            System.out.print(String.format(
                "\rIteration %d / %d, %s i/s, ATA %s",
                i,
                N,
                Duration.ofMillis((long) stat.getMean()),
                Duration.ofMillis((long) stat.getMean() * (N - i))
            ));
            try {
                SimulationResult simulationResult = completionService.take().get();
                if(simulationResult.isAffineEllipseContainsPoint()) {
                    affineCounter++;
                }
                if(simulationResult.isRigidEllipseContainsPoint()) {
                    rigidCounter++;
                }
                if(simulationResult.isAnisotropicRigidEllipseContainsPoint()) {
                    anisotropicRigidCounter++;
                }
                if(simulationResult.isTrueEllipseContainsPoint()) {
                    trueCounter++;
                }
                affineArea.increment(simulationResult.getAffineArea());
                rigidArea.increment(simulationResult.getRigidArea());
                anisotropicRigidArea.increment(simulationResult.getAnisotropicRigidArea());
                trueArea.increment(simulationResult.getTrueArea());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            double end = System.currentTimeMillis();
            stat.addValue(end - start);
        }

        System.out.println(String.format("Affine model : %.3f %%", (double) affineCounter / N * 100));
        System.out.println(String.format("Affine area : %.3f", affineArea.getResult()));
        System.out.println(String.format("Rigid isotropic model : %.3f %%", (double) rigidCounter / N * 100));
        System.out.println(String.format("Rigid isotropic area : %.3f", rigidArea.getResult()));
        System.out.println(String.format("Rigid anisotropic model : %.3f %%", (double) anisotropicRigidCounter / N * 100));
        System.out.println(String.format("Rigid anisotropic area : %.3f", anisotropicRigidArea.getResult()));
        System.out.println(String.format("True model : %.3f %%", (double) trueCounter / N * 100));
        System.out.println(String.format("True area : %.3f", trueArea.getResult()));
    }

    private double getEllipseArea(Shape shape) {
        double val = (shape.getBounds2D().getWidth() / 2d) * (shape.getBounds2D().getHeight() / 2d) * Math.PI;
        if(Double.isNaN(val)) {
            System.out.println(String.format("\nw: %f, h: %f", shape.getBounds2D().getWidth(), shape.getBounds2D().getHeight()));
        }
        return val;
    }

    @Command
    public void likelihood() {
        int[] range = new int[]{width, height};
        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, 2).getArray();

        AffineTransformation simpleRotationTransformation = (AffineTransformation) getRandomTransformation(transformationType);
        simpleRotationTransformation.getHomogeneousMatrix().print(1,5);

        FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
            simpleRotationTransformation, n, range
        );

        testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), noiseCovariance);

        System.out.println("Anisotropic rigid");
        RegistrationParameter computeAnisotropicRigid = anisotripicRigidTransformationComputer.compute(current);
        ((AffineTransformation) computeAnisotropicRigid.getTransformation()).getHomogeneousMatrix().print(1,5);
        computeAnisotropicRigid.getNoiseCovariance().print(1,5);
        System.out.println(computeAnisotropicRigid.getLogLikelihood());

        System.out.println("Isotropic rigid (ipopt)");
        RegistrationParameter computeIsotropicRigid = isotripicRigidTransformationComputer.compute(current);
        ((AffineTransformation) computeIsotropicRigid.getTransformation()).getHomogeneousMatrix().print(1,5);
        computeIsotropicRigid.getNoiseCovariance().print(1,5);
        System.out.println(computeIsotropicRigid.getLogLikelihood());

        System.out.println("Anisotropic affine");
        RegistrationParameter computeAffine = affineTransformationComputer.compute(current);
        ((AffineTransformation) computeAffine.getTransformation()).getHomogeneousMatrix().print(1,5);
        computeAffine.getNoiseCovariance().print(1,5);
        System.out.println(computeAffine.getLogLikelihood());

        System.out.println("Isotropic rigid (schonnemann + ipopt)");
        RegistrationParameter computeRigid = rigidTransformationComputer.compute(current);
        ((AffineTransformation) computeRigid.getTransformation()).getHomogeneousMatrix().print(1,5);
        computeRigid.getNoiseCovariance().print(1,5);
        System.out.println(computeRigid.getLogLikelihood());

        LikelihoodRatioTest likelihoodRatioTest = new LikelihoodRatioTest();
        System.out.println("Anisotropic rigid / Anisotropic affine");
        System.out.println(
            String.format(
                "pvalue: %f",
                likelihoodRatioTest.test(3, computeAnisotropicRigid.getLogLikelihood(), computeAffine.getLogLikelihood())
            )
        );

        System.out.println("Isotropic rigid (ipopt) / Anisotropic rigid");
        System.out.println(
            String.format(
                "pvalue: %f",
                likelihoodRatioTest.test(2, computeIsotropicRigid.getLogLikelihood(), computeAnisotropicRigid.getLogLikelihood())
            )
        );

        System.out.println("Isotropic rigid (schonnemann + ipopt) / Anisotropic rigid");
        System.out.println(
            String.format(
                "pvalue: %f",
                likelihoodRatioTest.test(2, computeRigid.getLogLikelihood(), computeAnisotropicRigid.getLogLikelihood())
            )
        );

        System.out.println("Isotropic rigid (ipopt) / Anisotropic affine");
        System.out.println(
            String.format(
                "pvalue: %f",
                likelihoodRatioTest.test(5, computeIsotropicRigid.getLogLikelihood(), computeAffine.getLogLikelihood())
            )
        );

        System.out.println("Isotropic rigid (schonnemann + ipopt) / Anisotropic affine");
        System.out.println(
            String.format(
                "pvalue: %f",
                likelihoodRatioTest.test(5, computeRigid.getLogLikelihood(), computeAffine.getLogLikelihood())
            )
        );
}

    @Command
    public void leaveOneOut() {
        int[] range = new int[]{width, height};
        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, 2).getArray();

        AffineTransformation simpleRotationTransformation = (AffineTransformation) getRandomTransformation(transformationType);
        simpleRotationTransformation.getHomogeneousMatrix().print(1,5);

        FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
            simpleRotationTransformation, n, range
        );
        testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), noiseCovariance);

        double[] list = new double[current.getN()];
        for(int i = 0; i < current.getN(); i++) {
            FiducialSet clone = current.clone();
            Point excludedSource = clone.getSourceDataset().getPoint(i);
            Point excludedTarget = clone.getTargetDataset().getPoint(i);
            clone.remove(i);

//            RegistrationParameter compute = anisotripicRigidTransformationComputer.compute(clone);
            RegistrationParameter compute = affineTransformationComputer.compute(clone);
            Point predictedTarget = compute.getTransformation().apply(excludedSource);
            double distance = excludedTarget.getDistance(predictedTarget);

            Shape ellipse = shapeEllipseFactory.getFrom(
                confidenceEllipseFactory.getFrom(
                    excludedSource,
                    new TransformationSchema(clone, TransformationType.AFFINE, NoiseModel.ANISOTROPIC, getSequenceSize(), getSequenceSize()),
                    alpha
                ),
                height
            );
            double predictedDistance = max(ellipse.getBounds2D().getWidth(), ellipse.getBounds2D().getHeight()) / 2d;
            list[i] = distance;
            System.out.println(String.format("Observed: %f, predicted: %f", distance, predictedDistance));
        }
        Percentile percentile = new Percentile();
        percentile.setData(list);
        System.out.println(String.format("50%%: %f", percentile.evaluate(50)));
        System.out.println(String.format("80%%: %f", percentile.evaluate(80)));
        System.out.println(String.format("95%%: %f", percentile.evaluate(95)));
        System.out.println(String.format("99%%: %f", percentile.evaluate(99)));
        System.out.println(String.format("100%%: %f", percentile.evaluate(100)));
    }

    public static void main(String ... args) {
        new CommandLine(new Main()).execute(args);
        System.exit(0);
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
    public void setAffineRegistrationParameterComputer(AffineRegistrationParameterComputer affineTransformationComputer) {
        this.affineTransformationComputer = affineTransformationComputer;
    }

    @Inject
    public void setRigidRegistrationParameterComputer(RigidRegistrationParameterComputer rigidTransformationComputer) {
        this.rigidTransformationComputer = rigidTransformationComputer;
    }

    @Inject
    public void setRigid2DGeneralMaxLikelihoodComputer(@Named("ipopt_general") Rigid2DMaxLikelihoodComputer solver) {
        this.anisotripicRigidTransformationComputer = solver;
    }

    @Inject
    public void setRigid2DConstrainedMaxLikelihoodComputer(@Named("ipopt_constrained") Rigid2DMaxLikelihoodComputer solver) {
        this.isotripicRigidTransformationComputer = solver;
    }

    @Inject
    public void setConfidenceEllipseFactory(ConfidenceEllipseFactory confidenceEllipseFactory) {
        this.confidenceEllipseFactory = confidenceEllipseFactory;
    }

    @Inject
    public void setShapeEllipseFactory(ShapeEllipseFactory shapeEllipseFactory) {
        this.shapeEllipseFactory = shapeEllipseFactory;
    }

    @Inject
    public void setIsotropicConfidenceEllipseFactory(TrueModelConfidenceEllipseFactory trueModelConfidenceEllipseFactory) {
        this.trueModelConfidenceEllipseFactory = trueModelConfidenceEllipseFactory;
    }
}
