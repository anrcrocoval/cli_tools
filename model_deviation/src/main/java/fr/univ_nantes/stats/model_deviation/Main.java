package fr.univ_nantes.stats.model_deviation;

import Jama.Matrix;
import fr.univ_nantes.stats.model_deviation.model.ShapeEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.truth.isotropic.TrueModelConfidenceEllipseFactory;
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
import java.util.Arrays;
import java.util.concurrent.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import plugins.fr.univ_nantes.ec_clem.error.CovarianceMatrixComputer;
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
import plugins.fr.univ_nantes.ec_clem.roi.PointType;
import plugins.fr.univ_nantes.ec_clem.sequence.DimensionSize;
import plugins.fr.univ_nantes.ec_clem.sequence.SequenceSize;
import plugins.fr.univ_nantes.ec_clem.transformation.AffineTransformation;
import plugins.fr.univ_nantes.ec_clem.transformation.RegistrationParameterFactory;
import plugins.fr.univ_nantes.ec_clem.transformation.Transformation;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.NoiseModel;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationSchema;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationType;

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

    private DatasetToCsvWriter datasetToCsvWriter;
    private CsvToDatasetFileReader csvToDatasetFileReader;
    private MatrixToCsvWriter matrixToCsvWriter;
    private CsvToMatrixFileReader csvToMatrixFileReader;

    private CovarianceMatrixComputer covarianceMatrixComputer;

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
        names = {"--transformation-model"},
        description = "Transformation model.\nValid values : ${COMPLETION-CANDIDATES}.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "RIGID"
    )
    private TransformationType transformationModel;

    @Option(
        names = {"--noise-model"},
        description = "Noise model.\nValid values : ${COMPLETION-CANDIDATES}.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "ISOTROPIC"
    )
    private NoiseModel noiseModel;

    @Option(
        names = {"--transformation"},
        description = "Transformation type.\nValid values : ${COMPLETION-CANDIDATES}.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "RIGID"
    )
    private TransformationType transformationType;

    @Option(
        names = "--noise-covariance",
        description = "Noise covariance matrix.\nDefault : ${DEFAULT-VALUE}.",
        arity = "4"
    )
    private double[] noiseCovarianceValues;

    public Main() {
        DaggerMainComponent.create().inject(this);
    }

    private AffineTransformation getRandomTransformation(TransformationType transformationType) {
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
            )
        );
        image.draw(image.center(affineEllipse, zTargetWithoutNoise), Color.BLUE);
        image.fill(image.center(getRectangle(affineTransformationComputer.compute(current).getTransformation().apply(zSource)), zTargetWithoutNoise), Color.BLUE);

        Shape rigidEllipse = shapeEllipseFactory.getFrom(
            confidenceEllipseFactory.getFrom(
                zSource,
                new TransformationSchema(current, TransformationType.RIGID, NoiseModel.ISOTROPIC, getSequenceSize(), getSequenceSize()),
                alpha
            )
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
            trueModelConfidenceEllipseFactory.getFrom(zTargetWithoutNoise, current, noiseCovariance, alpha)
        );
        image.draw(image.center(trueEllipse, zTargetWithoutNoise), Color.WHITE);
        image.fill(image.center(getRectangle(zTargetWithoutNoise), zTargetWithoutNoise), Color.WHITE);

        image.write(outputFilePath);
    }

    private Rectangle getRectangle(Point point) {
        return new Rectangle((int) point.get(0), (int) (point.get(1)), 5, 5);
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
            defaultValue = "10000"
        ) int N
    ) {
        int[] range = new int[]{width, height};

        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, 2).getArray();
        DescriptiveStatistics stat = new DescriptiveStatistics(50);

        CompletionService<SimulationResult> completionService = new ExecutorCompletionService<>(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()
        ));

        Transformation simpleRotationTransformation = getRandomTransformation(transformationType);
        Dataset sourceDatset = getUniformConfiguration(n, range);
        Dataset targetDataset = simpleRotationTransformation.apply(sourceDatset);
        final FiducialSet fiducialSet = new FiducialSet(sourceDatset, targetDataset);
        Point zSource = testFiducialSetFactory.getRandomPoint(range);

        for (int i = 0; i < N; i++) {
            completionService.submit(() -> {
                FiducialSet current = fiducialSet.clone();
                testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), noiseCovariance);
                Point zTarget = testFiducialSetFactory.addGaussianNoise(simpleRotationTransformation.apply(zSource), noiseCovariance);
                Shape affineEllipse = shapeEllipseFactory.getFrom(
                    confidenceEllipseFactory.getFrom(
                        zSource,
                        new TransformationSchema(current, TransformationType.AFFINE, NoiseModel.ANISOTROPIC, getSequenceSize(), getSequenceSize()),
                        alpha
                    )
                );
                Shape rigidEllipse = shapeEllipseFactory.getFrom(
                    confidenceEllipseFactory.getFrom(
                        zSource,
                        new TransformationSchema(current, TransformationType.RIGID, NoiseModel.ISOTROPIC, getSequenceSize(), getSequenceSize()),
                        alpha
                    )
                );
//                Shape anisotropicRigidEllipse = shapeEllipseFactory.getFrom(
//                    confidenceEllipseFactory.getFrom(
//                        zSource,
//                        new TransformationSchema(current, TransformationType.RIGID, NoiseModel.ANISOTROPIC, getSequenceSize(), getSequenceSize()),
//                        alpha
//                    ),
//                    height
//                );
                Shape trueEllipse = shapeEllipseFactory.getFrom(
                    trueModelConfidenceEllipseFactory.getFrom(simpleRotationTransformation.apply(zSource), current, noiseCovariance, alpha)
                );
                return new SimulationResult(
                    affineEllipse.contains(zTarget.get(0), zTarget.get(1)),
                    rigidEllipse.contains(zTarget.get(0), zTarget.get(1)),
//                    anisotropicRigidEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1))),
                    false,
                    trueEllipse.contains(zTarget.get(0), zTarget.get(1)),
                    getEllipseArea(affineEllipse),
                    getEllipseArea(rigidEllipse),
//                    getEllipseArea(anisotropicRigidEllipse),
                    0,
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

        System.out.println(String.format("Affine model : %.4f %%", (double) affineCounter / N * 100));
        System.out.println(String.format("Affine area : %.4f", affineArea.getResult()));
        System.out.println(String.format("Rigid isotropic model : %.4f %%", (double) rigidCounter / N * 100));
        System.out.println(String.format("Rigid isotropic area : %.4f", rigidArea.getResult()));
        System.out.println(String.format("Rigid anisotropic model : %.4f %%", (double) anisotropicRigidCounter / N * 100));
        System.out.println(String.format("Rigid anisotropic area : %.4f", anisotropicRigidArea.getResult()));
        System.out.println(String.format("True model : %.4f %%", (double) trueCounter / N * 100));
        System.out.println(String.format("True area : %.4f", trueArea.getResult()));
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

        FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
            simpleRotationTransformation, n, range
        );

        testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), noiseCovariance);

        RegistrationParameter computeAnisotropicRigid = anisotripicRigidTransformationComputer.compute(current);

//        System.out.println("Isotropic rigid (ipopt)");
//        RegistrationParameter computeIsotropicRigid = isotripicRigidTransformationComputer.compute(current);
//        ((AffineTransformation) computeIsotropicRigid.getTransformation()).getHomogeneousMatrix().print(1,5);
//        computeIsotropicRigid.getNoiseCovariance().print(1,5);
//        System.out.println(computeIsotropicRigid.getLogLikelihood());

        RegistrationParameter computeAffine = affineTransformationComputer.compute(current);
        RegistrationParameter computeRigid = rigidTransformationComputer.compute(current);

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

    private Dataset getUniformConfiguration(int n, int[] range) {
        Dataset dataset = new Dataset(range.length, PointType.FIDUCIAL);
        for(int i = 0; i < n; i++) {
            dataset.addPoint(testFiducialSetFactory.getRandomPoint(range));
        }
        return dataset;
    }

    private Dataset getGaussianConfiguration(int n, int[] range, double[][] configurationCovariance) {
        Point center = new Point(Arrays.stream(range).mapToDouble((i) -> (double) i / 2).toArray());
        Dataset dataset = new Dataset(range.length, PointType.FIDUCIAL);
        while(dataset.getN() < n) {
            Point newPoint = testFiducialSetFactory.addGaussianNoise(center, configurationCovariance);
            if(isPointInRange(newPoint, range)) {
                dataset.addPoint(newPoint);
            }
        }
        return dataset;
    }

    private boolean isPointInRange(Point point, int[] range) {
        boolean isNewPointInRange = true;
        for(int i = 0; i < range.length; i++) {
            isNewPointInRange = isNewPointInRange && (point.get(i) >= 0 && point.get(i) <= range[i]);
        }
        return isNewPointInRange;
    }

    @Command
    public void generateUniformDataset() {
        int[] range = new int[]{width, height};
        Dataset dataset = getUniformConfiguration(n, range);
        System.out.println(datasetToCsvWriter.write(dataset));
    }

    @Command
    public void generateOutlierDataset(
        @Option(
            names = "--fiducial-configuration-covariance",
            description = "Configuration covariance matrix.\nDefault : ${DEFAULT-VALUE}.",
            arity = "4"
        ) double[] configurationCovarianceValues
    ) {
        int[] range = new int[]{width, height};
        double[][] configurationCovariance = new Matrix(configurationCovarianceValues, range.length).getArray();
        Dataset dataset = getGaussianConfiguration(n, range, configurationCovariance);
        System.out.println(datasetToCsvWriter.write(dataset));
    }

    @Command
    public void generateTransformation(
        @Option(
            names = {"--transformation"},
            description = "Transformation type.\nValid values : ${COMPLETION-CANDIDATES}.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "RIGID"
        ) TransformationType transformationType
    ) {
        AffineTransformation transformation = getRandomTransformation(transformationType);
        System.out.println(matrixToCsvWriter.write(transformation.getHomogeneousMatrix()));
    }

    @Command
    public void generateImageFromCsv(
        @Option(
            names = {"-i", "--input"},
            description = "Input file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./source.csv"
        ) Path inputFilePath,
        @Option(
            names = {"-o", "--output"},
            description = "Output file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./image.png"
        ) Path outputFilePath
    ) {
        Dataset sourceDataset = csvToDatasetFileReader.read(inputFilePath.toFile());
        Image image = new Image(width, height);
        for(int i = 0; i < sourceDataset.getN(); i++) {
            image.fill(getRectangle(sourceDataset.getPoint(i)), Color.GREEN);
        }
        image.write(outputFilePath);
    }

    @Command
    public void leaveOneOutSimulation(
        @Option(
            names = { "-N" },
            description = "Number of iterrations. Default : ${DEFAULT-VALUE}.",
            defaultValue = "1000"
        ) int N,
        @Option(
            names = {"--transformation"},
            description = "Input transformation file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./transformation.csv"
        ) Path transformationFilePath,
        @Option(
            names = {"--source-dataset"},
            description = "Input source dataset file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./source_dataset.csv"
        ) Path sourceDatasetFilePath,
        @Option(
            names = {"-o", "--output"},
            description = "Output file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./image.png"
        ) Path outputFilePath
    ) {
        int[] range = new int[]{width, height};

        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, range.length).getArray();
        CompletionService<Void> completionService = new ExecutorCompletionService<>(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        );
        AffineTransformation transformation = new AffineTransformation(csvToMatrixFileReader.read(transformationFilePath.toFile()));
        Dataset sourceDataset = csvToDatasetFileReader.read(sourceDatasetFilePath.toFile());
        Dataset targetDataset = transformation.apply(sourceDataset);
        final FiducialSet fiducialSet = new FiducialSet(sourceDataset, targetDataset);

        Dataset testSourceDataset = new Dataset(range.length, PointType.NOT_FIDUCIAL);
        testSourceDataset.addPoint(new Point(new double[] { (double) width / 2, (double) height / 2 }));
        testSourceDataset.addPoint(new Point(new double[] { 0, 0 }));
        testSourceDataset.addPoint(new Point(new double[] { width, height }));
        testSourceDataset.addPoint(new Point(new double[] { width, 0 }));
        testSourceDataset.addPoint(new Point(new double[] { 0, height }));
        testSourceDataset.addPoint(new Point(new double[] { width * 2, 0 }));
        testSourceDataset.addPoint(new Point(new double[] { 0, height * 2 }));
        testSourceDataset.addPoint(new Point(new double[] { -width, -height }));
        testSourceDataset.addPoint(new Point(new double[] { width * 2, height * 2 }));
        Dataset testTargetDataset = transformation.apply(testSourceDataset);

        ShapeStat[] ellipsesFromRegression = new ShapeStat[testTargetDataset.getN()];
        ShapeStat[] ellipsesFromLoo = new ShapeStat[testTargetDataset.getN()];
        ShapeStat[] disksFromLoo = new ShapeStat[testTargetDataset.getN()];
        for(int i = 0; i < testTargetDataset.getN(); i++) {
            ellipsesFromRegression[i] = new ShapeStat();
            ellipsesFromLoo[i] = new ShapeStat();
            disksFromLoo[i] = new ShapeStat();
        }

        for (int k = 0; k < N; k++) {
            completionService.submit(() -> {
                FiducialSet clone = fiducialSet.clone();
                testFiducialSetFactory.addGaussianNoise(clone.getTargetDataset(), noiseCovariance);

                Dataset testClone = testTargetDataset.clone();
                testFiducialSetFactory.addGaussianNoise(testClone, noiseCovariance);

                Dataset registrationError = new Dataset(range.length, PointType.ERROR);
                double[] registrationErrorDistance = new double[clone.getN()];

                for(int i = 0; i < clone.getN(); i++) {
                    Point excludedSourcePoint = clone.getSourceDataset().getPoint(i);
                    Point excludedTargetPoint = clone.getTargetDataset().getPoint(i);
                    clone.remove(i);

                    TransformationSchema transformationSchema = new TransformationSchema(clone, transformationModel, noiseModel, getSequenceSize(), getSequenceSize());
                    RegistrationParameter compute = registrationParameterFactory.getFrom(transformationSchema);
                    Point predictedExcludedTargetPoint = compute.getTransformation().apply(excludedSourcePoint);
                    registrationError.addPoint(excludedTargetPoint.minus(predictedExcludedTargetPoint));
                    registrationErrorDistance[i] = excludedTargetPoint.getDistance(predictedExcludedTargetPoint);

                    clone.add(i, excludedSourcePoint, excludedTargetPoint);
                }
                Matrix looCovariance = covarianceMatrixComputer.compute(registrationError.getMatrix());
                Percentile percentile = new Percentile();
                percentile.setData(registrationErrorDistance);
                double evaluate = percentile.evaluate(alpha * 100);

                TransformationSchema transformationSchema = new TransformationSchema(clone, transformationModel, noiseModel, getSequenceSize(), getSequenceSize());
                RegistrationParameter compute = registrationParameterFactory.getFrom(transformationSchema);

                for(int i = 0; i < testClone.getN(); i++) {
                    Point sourceTestPoint = testSourceDataset.getPoint(i);
                    Point targetTestPoint = testClone.getPoint(i);
                    Point predictedTargetPoint = compute.getTransformation().apply(sourceTestPoint);

                    Shape ellipseLoo = shapeEllipseFactory.getFrom(
                        confidenceEllipseFactory.getFrom(
                            predictedTargetPoint,
                            clone,
                            looCovariance,
                            alpha
                        )
                    );
                    ellipsesFromLoo[i].updateCounter(ellipseLoo.contains(targetTestPoint.get(0),  targetTestPoint.get(1)));
                    ellipsesFromLoo[i].updateArea(getEllipseArea(ellipseLoo));

                    Shape diskLoo = shapeEllipseFactory.getFrom(
                        predictedTargetPoint,
                        evaluate
                    );
                    disksFromLoo[i].updateCounter(diskLoo.contains(targetTestPoint.get(0), targetTestPoint.get(1)));
                    disksFromLoo[i].updateArea(getEllipseArea(diskLoo));

                    Shape shape = shapeEllipseFactory.getFrom(
                        confidenceEllipseFactory.getFrom(
                            predictedTargetPoint,
                            clone,
                            covarianceEstimatorFactory.getFrom(transformationModel).getCovariance(transformationSchema, sourceTestPoint),
                            alpha
                        )
                    );
                    ellipsesFromRegression[i].updateCounter(shape.contains(targetTestPoint.get(0), targetTestPoint.get(1)));
                    ellipsesFromRegression[i].updateArea(getEllipseArea(shape));
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

        System.out.println("i, %in, area, loo.cov.%in, loo.cov.area, loo.circle.%in, loo.circle.area");
        for(int i = 0; i < testSourceDataset.getN(); i++) {
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

    @Inject
    public void setDatasetToCsvFileWriter(DatasetToCsvWriter datasetToCsvWriter) {
        this.datasetToCsvWriter = datasetToCsvWriter;
    }

    @Inject
    public void setCsvToDatasetFileReader(CsvToDatasetFileReader csvToDatasetFileReader) {
        this.csvToDatasetFileReader = csvToDatasetFileReader;
    }

    @Inject
    public void setMatrixToCsvWriter(MatrixToCsvWriter matrixToCsvWriter) {
        this.matrixToCsvWriter = matrixToCsvWriter;
    }

    @Inject
    public void setCsvToMatrixFileReader(CsvToMatrixFileReader csvToMatrixFileReader) {
        this.csvToMatrixFileReader = csvToMatrixFileReader;
    }

    @Inject
    public void setCovarianceMatrixComputer(CovarianceMatrixComputer covarianceMatrixComputer) {
        this.covarianceMatrixComputer = covarianceMatrixComputer;
    }
}
