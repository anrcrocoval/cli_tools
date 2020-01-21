package fr.univ_nantes.stats.model_deviation;

import Jama.Matrix;
import fr.univ_nantes.stats.model_deviation.model.ShapeEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.truth.isotropic.TrueModelConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.DaggerMainComponent;
import icy.sequence.DimensionId;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.util.Pair;
import picocli.CommandLine;
import javax.inject.Inject;
import javax.inject.Named;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.Color;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import plugins.fr.univ_nantes.ec_clem.error.ellipse.ConfidenceEllipseFactory;
import plugins.fr.univ_nantes.ec_clem.error.ellipse.CovarianceEstimatorFactory;
import plugins.fr.univ_nantes.ec_clem.error.likelihood_ratio.LikelihoodRatioTest;
import plugins.fr.univ_nantes.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.fiducialset.dataset.Dataset;
import plugins.fr.univ_nantes.ec_clem.fiducialset.dataset.point.Point;
import plugins.fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import plugins.fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import plugins.fr.univ_nantes.ec_clem.registration.AffineRegistrationParameterComputer;
import plugins.fr.univ_nantes.ec_clem.registration.RegistrationParameter;
import plugins.fr.univ_nantes.ec_clem.registration.RigidRegistrationParameterComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.general.conjugate_gradient.ConjugateGradientRigid2DGeneralMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.roi.PointType;
import plugins.fr.univ_nantes.ec_clem.sequence.DimensionSize;
import plugins.fr.univ_nantes.ec_clem.sequence.SequenceSize;
import plugins.fr.univ_nantes.ec_clem.transformation.AffineTransformation;
import plugins.fr.univ_nantes.ec_clem.transformation.RegistrationParameterFactory;
import plugins.fr.univ_nantes.ec_clem.transformation.Transformation;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.NoiseModel;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationSchema;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationType;

import static java.lang.Double.max;
import static java.lang.Math.sqrt;

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
    private CovarianceEstimatorFactory covarianceEstimatorFactory;
    private RegistrationParameterFactory registrationParameterFactory;

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
        names = {"-nm", "--noise-model"},
        description = "Noise model.\nValid values : ${COMPLETION-CANDIDATES}.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "ISOTROPIC"
    )
    private NoiseModel noiseModel;

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

//        System.out.println("Isotropic rigid (ipopt)");
//        RegistrationParameter computeIsotropicRigid = isotripicRigidTransformationComputer.compute(current);
//        ((AffineTransformation) computeIsotropicRigid.getTransformation()).getHomogeneousMatrix().print(1,5);
//        computeIsotropicRigid.getNoiseCovariance().print(1,5);
//        System.out.println(computeIsotropicRigid.getLogLikelihood());

        System.out.println("Anisotropic affine");
        RegistrationParameter computeAffine = affineTransformationComputer.compute(current);
        ((AffineTransformation) computeAffine.getTransformation()).getHomogeneousMatrix().print(1,5);
        computeAffine.getNoiseCovariance().print(1,5);
        System.out.println(computeAffine.getLogLikelihood());

        System.out.println("Isotropic rigid (schonnemann)");
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

//        System.out.println("Isotropic rigid (ipopt) / Anisotropic rigid");
//        System.out.println(
//            String.format(
//                "pvalue: %f",
//                likelihoodRatioTest.test(2, computeIsotropicRigid.getLogLikelihood(), computeAnisotropicRigid.getLogLikelihood())
//            )
//        );

        System.out.println("Isotropic rigid (schonnemann) / Anisotropic rigid");
        System.out.println(
            String.format(
                "pvalue: %f",
                likelihoodRatioTest.test(2, computeRigid.getLogLikelihood(), computeAnisotropicRigid.getLogLikelihood())
            )
        );

//        System.out.println("Isotropic rigid (ipopt) / Anisotropic affine");
//        System.out.println(
//            String.format(
//                "pvalue: %f",
//                likelihoodRatioTest.test(5, computeIsotropicRigid.getLogLikelihood(), computeAffine.getLogLikelihood())
//            )
//        );

        System.out.println("Isotropic rigid (schonnemann) / Anisotropic affine");
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

        double[] distanceList = new double[current.getN()];
        double[] predictedDistanceList = new double[current.getN()];
        double[] predictedAreaList = new double[current.getN()];
        Matrix[] covarianceList = new Matrix[current.getN()];
        Dataset registrationError = new Dataset(2, PointType.ERROR);
        for(int i = 0; i < current.getN(); i++) {
            FiducialSet clone = current.clone();
            Point excludedSource = clone.getSourceDataset().getPoint(i);
            Point excludedTarget = clone.getTargetDataset().getPoint(i);
            clone.remove(i);

//            RegistrationParameter compute = anisotripicRigidTransformationComputer.compute(clone);
//            RegistrationParameter compute = affineTransformationComputer.compute(clone);
            TransformationSchema transformationSchema = new TransformationSchema(clone, transformationType, noiseModel, getSequenceSize(), getSequenceSize());
            RegistrationParameter compute = registrationParameterFactory.getFrom(transformationSchema);
            Point predictedTarget = compute.getTransformation().apply(excludedSource);
            double distance = excludedTarget.getDistance(predictedTarget);
            Point minus = excludedTarget.minus(predictedTarget);
            registrationError.addPoint(minus);

            Shape ellipse = shapeEllipseFactory.getFrom(
                confidenceEllipseFactory.getFrom(
                    excludedSource,
                    transformationSchema,
                    alpha
                ),
                height
            );

            covarianceList[i] = covarianceEstimatorFactory.getFrom(transformationType)
                .getCovariance(transformationSchema, excludedSource);


            double predictedDistance = max(ellipse.getBounds2D().getWidth(), ellipse.getBounds2D().getHeight()) / 2d;
            double predictedArea = Math.PI * ellipse.getBounds2D().getWidth() / 2d * ellipse.getBounds2D().getHeight() / 2d;
            distanceList[i] = distance;
            predictedDistanceList[i] = predictedDistance;
            predictedAreaList[i] = predictedArea;
//            System.out.println(String.format("Observed: %f, predicted: %f, predicted area: %f", distance, predictedDistance, predictedArea));
        }
        Percentile percentile = new Percentile();
        percentile.setData(distanceList);
        Percentile predictedDistancePercentile = new Percentile();
        predictedDistancePercentile.setData(predictedDistanceList);
        Percentile predictedAreaListPercentile = new Percentile();
        predictedAreaListPercentile.setData(predictedAreaList);

        System.out.println("Percentile, Observed error, Observed area");
        System.out.println(String.format("50%%, %f, %f", percentile.evaluate(50), Math.PI * percentile.evaluate(50) * percentile.evaluate(50)));
        System.out.println(String.format("80%%, %f, %f", percentile.evaluate(80), Math.PI * percentile.evaluate(80) * percentile.evaluate(80)));
        System.out.println(String.format("95%%, %f, %f", percentile.evaluate(95), Math.PI * percentile.evaluate(95) * percentile.evaluate(95)));
        System.out.println(String.format("99%%, %f, %f", percentile.evaluate(99), Math.PI * percentile.evaluate(99) * percentile.evaluate(99)));
        System.out.println(String.format("100%%, %f, %f", percentile.evaluate(100), Math.PI * percentile.evaluate(100) * percentile.evaluate(100)));
        System.out.println("");
        System.out.println("Percentile, Ellipse major axis length, Ellipse area");
        System.out.println(String.format("50%%, %f, %f", predictedDistancePercentile.evaluate(50), predictedAreaListPercentile.evaluate(50)));
        System.out.println(String.format("80%%, %f, %f", predictedDistancePercentile.evaluate(80), predictedAreaListPercentile.evaluate(80)));
        System.out.println(String.format("95%%, %f, %f", predictedDistancePercentile.evaluate(95), predictedAreaListPercentile.evaluate(95)));
        System.out.println(String.format("99%%, %f, %f", predictedDistancePercentile.evaluate(99), predictedAreaListPercentile.evaluate(99)));
        System.out.println(String.format("100%%, %f, %f", predictedDistancePercentile.evaluate(100), predictedAreaListPercentile.evaluate(100)));
        System.out.println("");

        Matrix times = registrationError.getMatrix().transpose().times(registrationError.getMatrix()).times(1 / (double) current.getN());
        System.out.println("Leave one out registration error covariance:");
        times.print(1,5);
        System.out.println("Frobenius norm of difference with noise covariance:");
        System.out.println(times.minus(new Matrix(noiseCovarianceValues, 2)).normF());
        System.out.println("");

        System.out.println("Estimated registration error covariance:");
        covarianceList[0].print(1,5);
        System.out.println("Frobenius norm of difference with noise covariance:");
        System.out.println(covarianceList[0].minus(new Matrix(noiseCovarianceValues, 2)).normF());
    }

    private Dataset getUniformConfiguration(int n, int[] range) {
        Dataset dataset = new Dataset(range.length, PointType.FIDUCIAL);
        for(int i = 0; i < n; i++) {
            dataset.addPoint(testFiducialSetFactory.getRandomPoint(range));
        }
        return dataset;
    }

    @Command
    public void leaveOneOutSimulation(
        @Option(
            names = { "-N" },
            description = "Number of iterrations. Default : ${DEFAULT-VALUE}.",
            defaultValue = "1000"
        ) int N
    ) {
        int[] range = new int[]{width, height};

        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, 2).getArray();
        CompletionService<Void> completionService = new ExecutorCompletionService<>(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        );
        Transformation transformation = getRandomTransformation(transformationType);
        Dataset targetDataset = getUniformConfiguration(n, range);
        Dataset sourceDatset = transformation.apply(targetDataset);
        final FiducialSet fiducialSet = new FiducialSet(sourceDatset, targetDataset);

        ShapeStat[] ellipsesFromRegression = new ShapeStat[n];
        ShapeStat[] ellipsesFromLoo = new ShapeStat[n];
        ShapeStat[] disksFromLoo = new ShapeStat[n];
        for(int i = 0; i < n; i++) {
            ellipsesFromRegression[i] = new ShapeStat();
            ellipsesFromLoo[i] = new ShapeStat();
            disksFromLoo[i] = new ShapeStat();
        }

        for (int K = 0; K < N; K++) {
            final int k = K;
            completionService.submit(() -> {
                FiducialSet clone = fiducialSet.clone();
                testFiducialSetFactory.addGaussianNoise(clone.getSourceDataset(), noiseCovariance);
                Dataset registrationError = new Dataset(range.length, PointType.ERROR);
                double[] registrationErrorDistance = new double[n];
                Dataset predictedTarget = new Dataset(range.length, PointType.NOT_FIDUCIAL);
                for(int i = 0; i < clone.getN(); i++) {
                    Point excludedSource = clone.getSourceDataset().getPoint(i);
                    Point excludedTarget = clone.getTargetDataset().getPoint(i);
                    clone.remove(i);

                    TransformationSchema transformationSchema = new TransformationSchema(clone, transformationType, noiseModel, getSequenceSize(), getSequenceSize());
                    RegistrationParameter compute = registrationParameterFactory.getFrom(transformationSchema);
                    Point apply = compute.getTransformation().apply(excludedSource);
                    predictedTarget.addPoint(apply);
                    registrationError.addPoint(excludedTarget.minus(apply));
                    registrationErrorDistance[i] = excludedTarget.getDistance(apply);

                    Shape shape = shapeEllipseFactory.getFrom(
                        confidenceEllipseFactory.getFrom(
                            apply,
                            clone,
                            covarianceEstimatorFactory.getFrom(transformationType).getCovariance(transformationSchema, excludedSource),
                            alpha
                        ),
                        height
                    );
                    ellipsesFromRegression[i].updateCounter(shape.contains(excludedTarget.get(0), (int) (height - excludedTarget.get(1))));
                    ellipsesFromRegression[i].updateArea(getEllipseArea(shape));
                    clone.add(i, excludedSource, excludedTarget);
                }

                Matrix looCovariance = registrationError.getMatrix().transpose().times(registrationError.getMatrix()).times((double) 1 / n);
                Percentile percentile = new Percentile();
                percentile.setData(registrationErrorDistance);
                double evaluate = percentile.evaluate(alpha * 100);
                percentile.setData(null);
                for(int i = 0; i < clone.getN(); i++) {
                    Point excludedSource = clone.getSourceDataset().getPoint(i);
                    Point excludedTarget = clone.getTargetDataset().getPoint(i);
                    clone.remove(i);

                    Shape ellipseLoo = shapeEllipseFactory.getFrom(
                        confidenceEllipseFactory.getFrom(
                            predictedTarget.getPoint(i),
                            clone,
                            looCovariance,
                            alpha
                        ),
                        height
                    );
                    ellipsesFromLoo[i].updateCounter(ellipseLoo.contains(excludedTarget.get(0), (int) (height - excludedTarget.get(1))));
                    ellipsesFromLoo[i].updateArea(getEllipseArea(ellipseLoo));

                    Shape diskLoo = shapeEllipseFactory.getFrom(
                        predictedTarget.getPoint(i),
                        evaluate,
                        height
                    );
                    disksFromLoo[i].updateCounter(diskLoo.contains(excludedTarget.get(0), (int) (height - excludedTarget.get(1))));
                    disksFromLoo[i].updateArea(getEllipseArea(diskLoo));

                    clone.add(i, excludedSource, excludedTarget);
                }
                return null;
            });

        }
        for(int k = 0; k < N; k++) {
            try {
                Void take = completionService.take().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Point, %in reg, area reg, %in loo-cov, area loo-cov, %in loo-circle, area loo-circle");
        for(int i = 0; i < fiducialSet.getN(); i++) {
            System.out.println(String.format(
                "%d, %f, %f, %f, %f, %f, %f",
                i,
                ellipsesFromRegression[i].getRatio(), ellipsesFromRegression[i].getArea(),
                ellipsesFromLoo[i].getRatio(), ellipsesFromLoo[i].getArea(),
                disksFromLoo[i].getRatio(), disksFromLoo[i].getArea()
            ));
        }
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

    @Inject
    public void setCovarianceEstimatorFactory(CovarianceEstimatorFactory covarianceEstimatorFactory) {
        this.covarianceEstimatorFactory = covarianceEstimatorFactory;
    }

    @Inject
    public void setRegistrationParameterFactory(RegistrationParameterFactory registrationParameterFactory) {
        this.registrationParameterFactory = registrationParameterFactory;
    }
}
