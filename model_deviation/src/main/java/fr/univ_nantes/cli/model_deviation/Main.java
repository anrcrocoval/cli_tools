package fr.univ_nantes.cli.model_deviation;

import Jama.Matrix;
import fr.univ_nantes.cli.model_deviation.model.ShapeEllipseFactory;
import fr.univ_nantes.cli.model_deviation.model.truth.isotropic.TrueModelConfidenceEllipseFactory;
import icy.sequence.DimensionId;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import picocli.CommandLine;
import javax.inject.Inject;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.Color;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
import plugins.fr.univ_nantes.ec_clem.fiducialset.dataset.point.PointFactory;
import plugins.fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import plugins.fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import plugins.fr.univ_nantes.ec_clem.registration.AffineRegistrationParameterComputer;
import plugins.fr.univ_nantes.ec_clem.registration.RegistrationParameter;
import plugins.fr.univ_nantes.ec_clem.registration.RigidRegistrationParameterComputer;
//import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.Rigid2DMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.roi.PointType;
import plugins.fr.univ_nantes.ec_clem.sequence.DimensionSize;
import plugins.fr.univ_nantes.ec_clem.sequence.SequenceSize;
import plugins.fr.univ_nantes.ec_clem.storage.*;
import plugins.fr.univ_nantes.ec_clem.storage.dataset.CsvToDatasetFileReader;
import plugins.fr.univ_nantes.ec_clem.storage.dataset.DatasetToCsvFormatter;
import plugins.fr.univ_nantes.ec_clem.storage.transformation.CsvToMatrixFileReader;
import plugins.fr.univ_nantes.ec_clem.storage.transformation.TransformationToCsvFormatter;
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

//    private Rigid2DMaxLikelihoodComputer anisotripicRigidTransformationComputer;
    private ConfidenceEllipseFactory confidenceEllipseFactory;
    private ShapeEllipseFactory shapeEllipseFactory;
    private TrueModelConfidenceEllipseFactory trueModelConfidenceEllipseFactory;
    private CovarianceEstimatorFactory covarianceEstimatorFactory;
    private RegistrationParameterFactory registrationParameterFactory;

    private DatasetToCsvFormatter datasetToCsvFormatter;
    private CsvToDatasetFileReader csvToDatasetFileReader;
    private TransformationToCsvFormatter transformationToCsvFormatter;
    private CsvToMatrixFileReader csvToMatrixFileReader;

    private CovarianceMatrixComputer covarianceMatrixComputer;
    private PointFactory pointFactory;

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

        Shape trueEllipse = shapeEllipseFactory.getFrom(
            trueModelConfidenceEllipseFactory.getFrom(zTargetWithoutNoise, current, noiseCovariance, alpha)
        );
        image.draw(image.center(trueEllipse, zTargetWithoutNoise), Color.WHITE);
        image.fill(image.center(getRectangle(zTargetWithoutNoise), zTargetWithoutNoise), Color.WHITE);

        image.write(outputFilePath);
    }

    private Rectangle getRectangle(Point point) {
        int width = 10;
        int height = 10;
        return new Rectangle((int) point.get(0), (int) point.get(1), width, height);
    }

    private SequenceSize getSequenceSize() {
        SequenceSize sequenceSize = new SequenceSize();
        sequenceSize.add(new DimensionSize(DimensionId.X, width, 1));
        sequenceSize.add(new DimensionSize(DimensionId.Y, width, 1));
        return sequenceSize;
    }

    private double getEllipseArea(Shape shape) {
        double val = (shape.getBounds2D().getWidth() / 2d) * (shape.getBounds2D().getHeight() / 2d) * Math.PI;
        if(Double.isNaN(val)) {
            System.out.println(String.format("\nw: %f, h: %f", shape.getBounds2D().getWidth(), shape.getBounds2D().getHeight()));
        }
        return val;
    }

    @Command
    public void bias(
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
                    names = { "-N" },
                    description = "Number of iterrations. Default : ${DEFAULT-VALUE}.",
                    defaultValue = "1"
            ) int N
    ) {
        int[] range = new int[]{width, height};
        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, range.length).getArray();
        CompletionService<RegistrationParameter> completionService = new ExecutorCompletionService<>(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        );
        AffineTransformation transformation = new AffineTransformation(csvToMatrixFileReader.read(transformationFilePath.toFile()));
        Dataset sourceDataset = csvToDatasetFileReader.read(sourceDatasetFilePath.toFile());
        Dataset targetDataset = transformation.apply(sourceDataset);
        final FiducialSet fiducialSet = new FiducialSet(sourceDataset, targetDataset);

        for (int k = 0; k < N; k++) {
            completionService.submit(() -> {
                FiducialSet clone = fiducialSet.clone();
                testFiducialSetFactory.addGaussianNoise(clone.getTargetDataset(), noiseCovariance);
                testFiducialSetFactory.addGaussianNoise(clone.getSourceDataset(), noiseCovariance);
                TransformationSchema transformationSchema = new TransformationSchema(clone, transformationModel, noiseModel, getSequenceSize(), getSequenceSize());
                RegistrationParameter compute = registrationParameterFactory.getFrom(transformationSchema);
                return compute;
            });
        }
        Matrix avg = Matrix.identity(range.length + 1, range.length + 1);
        for (int k = 0; k < N; k++) {
            try {
                RegistrationParameter registrationParameter = completionService.take().get();
                Matrix homogeneousMatrix = ((AffineTransformation) registrationParameter.getTransformation()).getHomogeneousMatrix();
                avg.plusEquals(homogeneousMatrix);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        avg.timesEquals(1d / (double) N);
        avg.print(1,5);
        AffineTransformation biased = new AffineTransformation(avg);
        Dataset apply = biased.apply(fiducialSet.getSourceDataset());
        Matrix minus = fiducialSet.getTargetDataset().getMatrix().minus(apply.getMatrix());
        minus.print(1,5);
//        Dataset minusDataset = new Dataset(minus, PointType.ERROR);
//        minusDataset.getBarycentre().getMatrix().print(1,5);
    }

    @Command
    public void likelihood(
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
            names = { "-N" },
            description = "Number of iterrations. Default : ${DEFAULT-VALUE}.",
            defaultValue = "1000"
        ) int N
    ) {
        int[] range = new int[]{width, height};
        LikelihoodRatioTest likelihoodRatioTest = new LikelihoodRatioTest();
        CompletionService<Void> completionService = new ExecutorCompletionService<>(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        );
        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, range.length).getArray();
        AffineTransformation transformation = new AffineTransformation(csvToMatrixFileReader.read(transformationFilePath.toFile()));
        Dataset sourceDataset = csvToDatasetFileReader.read(sourceDatasetFilePath.toFile());
        Dataset targetDataset = transformation.apply(sourceDataset);
        final FiducialSet fiducialSet = new FiducialSet(sourceDataset, targetDataset);

        System.out.println("pvalue");

        for (int k = 0; k < N; k++) {
            completionService.submit(() -> {
                FiducialSet clone = fiducialSet.clone();
                testFiducialSetFactory.addGaussianNoise(clone.getTargetDataset(), noiseCovariance);
                RegistrationParameter computeAffine = affineTransformationComputer.compute(clone);
                RegistrationParameter computeRigid = rigidTransformationComputer.compute(clone);
                System.out.println(
                    String.format(
                        Locale.US,
                    "%f", likelihoodRatioTest.test(5, computeRigid.getLogLikelihood(), computeAffine.getLogLikelihood())
                    )
                );
                return null;
            });
        }
        for (int k = 0; k < N; k++) {
            try {
                completionService.take().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
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
        System.out.println(datasetToCsvFormatter.format(dataset));
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
        System.out.println(datasetToCsvFormatter.format(dataset));
    }

    @Command
    public void generateTestDataset() {
        int[] range = new int[]{width, height};
        Dataset testSourceDataset = new Dataset(range.length, PointType.NOT_FIDUCIAL);
        for(int i = 0; i < n; i++) {
            testSourceDataset.addPoint(
                testFiducialSetFactory.getRandomPoint(range)
            );
        }
        System.out.println(datasetToCsvFormatter.format(testSourceDataset));
    }

    @Command
    public void generateTransformation(
        @Option(
            names = {"--transformation"},
            description = "Transformation type.\nValid values : ${COMPLETION-CANDIDATES}.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "RIGID"
        ) TransformationType transformationType
    ) {
        Transformation transformation = getRandomTransformation(transformationType);
        System.out.println(transformationToCsvFormatter.format(transformation));
    }

    @Command
    public void generateImageFromCsv(
        @Option(
            names = {"-i", "--input"},
            description = "Input file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./source.csv"
        ) Path[] inputFilePath,
        @Option(
            names = {"-o", "--output"},
            description = "Output file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./image.png"
        ) Path outputFilePath
    ) {
        Image image = new Image(width, height);
        for(int k = 0; k < inputFilePath.length; k++) {
            Dataset sourceDataset = csvToDatasetFileReader.read(inputFilePath[k].toFile());
            for(int i = 0; i < sourceDataset.getN(); i++) {
                image.fill(getRectangle(sourceDataset.getPoint(i)), colors[k % 12]);
            }
        }
        image.write(outputFilePath);
    }

    private static Color[] colors = new Color[] {
        Color.BLACK,
        Color.WHITE,
        Color.ORANGE,
        Color.YELLOW,
        Color.RED,
        Color.PINK,
        Color.GREEN,
        Color.BLUE,
        Color.CYAN,
        Color.LIGHT_GRAY,
        Color.MAGENTA
    };

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
            names = {"--test-source-dataset"},
            description = "Input test source dataset file.\nDefault : ${DEFAULT-VALUE}.",
            defaultValue = "./test_source_dataset.csv"
        ) Path testSourceDatasetFilePath
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

        Dataset testSourceDataset = csvToDatasetFileReader.read(testSourceDatasetFilePath.toFile());
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
                testFiducialSetFactory.addGaussianNoise(clone.getSourceDataset(), noiseCovariance);

                Dataset targetTestClone = testTargetDataset.clone();
                Dataset sourceTestClone = testSourceDataset.clone();
                testFiducialSetFactory.addGaussianNoise(targetTestClone, noiseCovariance);
                testFiducialSetFactory.addGaussianNoise(sourceTestClone, noiseCovariance);

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

                for(int i = 0; i < targetTestClone.getN(); i++) {
                    Point sourceTestPoint = sourceTestClone.getPoint(i);
                    Point targetTestPoint = targetTestClone.getPoint(i);
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

        List<Point> list = pointFactory.getFrom(sourceDataset);
        System.out.println("i,model,method,n,%in,area.mean,area.sd,nearest");
        for(int i = 0; i < testSourceDataset.getN(); i++) {
            Point current = testSourceDataset.getPoint(i);
            double distance = current.getDistance(current.getNearest(list));
            System.out.println(String.format(
                Locale.US,
                "%d,\"%s\",\"analytic\",%d,%f,%f,%f,%f",
                i,
                transformationModel.toString().toLowerCase(),
                fiducialSet.getN(),
                ellipsesFromRegression[i].getRatio() * 100,
                ellipsesFromRegression[i].getArea(),
                ellipsesFromRegression[i].getAreaSd(),
                distance
            ));
            System.out.println(String.format(
                Locale.US,
                "%d,\"%s\",\"leave_one_out\",%d,%f,%f,%f,%f",
                i,
                transformationModel.toString().toLowerCase(),
                fiducialSet.getN(),
                disksFromLoo[i].getRatio() * 100,
                disksFromLoo[i].getArea(),
                disksFromLoo[i].getAreaSd(),
                distance
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

//    @Inject
//    public void setRigid2DGeneralMaxLikelihoodComputer(@Named("ipopt_general") Rigid2DMaxLikelihoodComputer solver) {
//        this.anisotripicRigidTransformationComputer = solver;
//    }

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
    public void setDatasetToCsvFileFormatter(DatasetToCsvFormatter datasetToCsvFormatter) {
        this.datasetToCsvFormatter = datasetToCsvFormatter;
    }

    @Inject
    public void setCsvToDatasetFileReader(CsvToDatasetFileReader csvToDatasetFileReader) {
        this.csvToDatasetFileReader = csvToDatasetFileReader;
    }

    @Inject
    public void setTransformationToCsvFormatter(TransformationToCsvFormatter transformationToCsvFormatter) {
        this.transformationToCsvFormatter = transformationToCsvFormatter;
    }

    @Inject
    public void setCsvToMatrixFileReader(CsvToMatrixFileReader csvToMatrixFileReader) {
        this.csvToMatrixFileReader = csvToMatrixFileReader;
    }

    @Inject
    public void setCovarianceMatrixComputer(CovarianceMatrixComputer covarianceMatrixComputer) {
        this.covarianceMatrixComputer = covarianceMatrixComputer;
    }

    @Inject
    public void setPointFactory(PointFactory pointFactory) {
        this.pointFactory = pointFactory;
    }
}
