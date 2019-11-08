package fr.univ_nantes.stats.model_deviation;

import Jama.Matrix;
import fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import fr.univ_nantes.stats.model_deviation.model.estimated.affine.AffineConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.estimated.rigid.RigidConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.truth.isotropic.TrueModelConfidenceEllipseFactory;
import picocli.CommandLine;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.registration.AffineTransformationComputer;
import plugins.perrine.easyclemv0.registration.RigidTransformationComputer;
import javax.inject.Inject;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.Color;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import plugins.perrine.easyclemv0.registration.likelihood.dimension2.general.Rigid2DGeneralMaxLikelihoodComputer;
import plugins.perrine.easyclemv0.registration.likelihood.dimension2.isotropic.Rigid2DIsotropicMaxLikelihoodComputer;
import plugins.perrine.easyclemv0.transformation.Similarity;
import plugins.perrine.easyclemv0.transformation.Transformation;
import plugins.perrine.easyclemv0.transformation.schema.TransformationType;

@Command(
    name = "model_deviation",
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true
)
public class Main {

    private TestTransformationFactory testTransformationFactory;
    private TestFiducialSetFactory testFiducialSetFactory;
    private AffineTransformationComputer affineTransformationComputer;
    private RigidTransformationComputer rigidTransformationComputer;
    private Rigid2DGeneralMaxLikelihoodComputer rigid2DGeneralMaxLikelihoodComputer;
    private Rigid2DIsotropicMaxLikelihoodComputer rigid2DIsotropicMaxLikelihoodComputer;

    private AffineConfidenceEllipseFactory affineConfidenceEllipseFactory;
    private RigidConfidenceEllipseFactory rigidConfidenceEllipseFactory;
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

        Shape affineEllipse = affineConfidenceEllipseFactory.getFrom(zSource, current, alpha, height);
        image.draw(image.center(affineEllipse, zTargetWithoutNoise), Color.BLUE);
        image.fill(image.center(getRectangle(affineTransformationComputer.compute(current).apply(zSource)), zTargetWithoutNoise), Color.BLUE);

        Shape rigidEllipse = rigidConfidenceEllipseFactory.getFrom(zSource, current, alpha, height);
        image.draw(image.center(rigidEllipse, zTargetWithoutNoise), Color.ORANGE);
        image.fill(image.center(getRectangle(rigidTransformationComputer.compute(current).apply(zSource)), zTargetWithoutNoise), Color.ORANGE);

        Shape trueEllipse = trueModelConfidenceEllipseFactory.getFrom(zTargetWithoutNoise, current, alpha, height, noiseCovariance);
        image.draw(image.center(trueEllipse, zTargetWithoutNoise), Color.WHITE);
        image.fill(image.center(getRectangle(zTargetWithoutNoise), zTargetWithoutNoise), Color.WHITE);

        image.write(outputFilePath);
    }

    private Rectangle getRectangle(Point point) {
        return new Rectangle((int) point.get(0), (int) (height - point.get(1)), 5, 5);
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

        int affineCounter = 0;
        int rigidCounter = 0;
        int trueCounter = 0;

        double affineArea = 0;
        double rigidArea = 0;
        double trueArea = 0;

        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, 2).getArray();

        for (int i = 0; i < N; i++) {
            Transformation simpleRotationTransformation = getRandomTransformation(transformationType);

            Point zSource = testFiducialSetFactory.getRandomPoint(range);
            FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
                simpleRotationTransformation, n, range
            );
            testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), noiseCovariance);
            Point zTarget = testFiducialSetFactory.addGaussianNoise(simpleRotationTransformation.apply(zSource), noiseCovariance);

            Shape affineEllipse = affineConfidenceEllipseFactory.getFrom(zSource, current, alpha, height);
            Shape rigidEllipse = rigidConfidenceEllipseFactory.getFrom(zSource, current, alpha, height);
            Shape trueEllipse = trueModelConfidenceEllipseFactory.getFrom(simpleRotationTransformation.apply(zSource), current, alpha, height, noiseCovariance);

            if (affineEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
                affineCounter++;
            }
            if (rigidEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
                rigidCounter++;
            }
            if (trueEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
                trueCounter++;
            }

            affineArea += Math.PI * affineEllipse.getBounds2D().getWidth() / 2 * affineEllipse.getBounds2D().getHeight() / 2;
            rigidArea += Math.PI * rigidEllipse.getBounds2D().getWidth() / 2 * rigidEllipse.getBounds2D().getHeight() / 2;
            trueArea += Math.PI * trueEllipse.getBounds2D().getWidth() / 2 * trueEllipse.getBounds2D().getHeight() / 2;
        }

        System.out.println(String.format("Affine model : %.3f %%", (double) affineCounter / N * 100));
        System.out.println(String.format("Affine area : %.3f", affineArea / N));
        System.out.println(String.format("Rigid model : %.3f %%", (double) rigidCounter / N * 100));
        System.out.println(String.format("Rigid area : %.3f", rigidArea / N));
        System.out.println(String.format("True model : %.3f %%", (double) trueCounter / N * 100));
        System.out.println(String.format("True area : %.3f", trueArea / N));
    }

    @Command
    public void solver() {
        int[] range = new int[]{width, height};
        double[][] noiseCovariance = new Matrix(noiseCovarianceValues, 2).getArray();
        Similarity simpleRotationTransformation = testTransformationFactory.getRandomSimpleRotationTransformation(2);

        FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
            simpleRotationTransformation, n, range
        );
        testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), noiseCovariance);
        Similarity shonemann = rigidTransformationComputer.compute(current);
        Similarity isotropicMaximumLikelihood = rigid2DIsotropicMaxLikelihoodComputer.compute(current);
        Similarity generalMaximumLikelihood = rigid2DGeneralMaxLikelihoodComputer.compute(current);

        System.out.println("True transformation");
        simpleRotationTransformation.getHomogeneousMatrix().print(1,5);

        System.out.println("Schonemann transformation");
        shonemann.getHomogeneousMatrix().print(1,5);

        System.out.println("General Maximum likelihood transformation");
        generalMaximumLikelihood.getHomogeneousMatrix().print(1,5);

        System.out.println("Isotropic Maximum likelihood transformation");
        isotropicMaximumLikelihood.getHomogeneousMatrix().print(1,5);
    }

    public static void main(String ... args){
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
    public void setAffineTransformationComputer(AffineTransformationComputer affineTransformationComputer) {
        this.affineTransformationComputer = affineTransformationComputer;
    }

    @Inject
    public void setRigidTransformationComputer(RigidTransformationComputer rigidTransformationComputer) {
        this.rigidTransformationComputer = rigidTransformationComputer;
    }

    @Inject
    public void setRigid2DGeneralMaxLikelihoodComputer(Rigid2DGeneralMaxLikelihoodComputer rigid2DMaxLikelihoodComputer) {
        this.rigid2DGeneralMaxLikelihoodComputer = rigid2DMaxLikelihoodComputer;
    }

    @Inject
    public void setRigid2DIsotropicMaxLikelihoodComputer(Rigid2DIsotropicMaxLikelihoodComputer rigid2DMaxLikelihoodComputer) {
        this.rigid2DIsotropicMaxLikelihoodComputer = rigid2DMaxLikelihoodComputer;
    }

    @Inject
    public void setAffineConfidenceEllipseFactory(AffineConfidenceEllipseFactory affineConfidenceEllipseFactory) {
        this.affineConfidenceEllipseFactory = affineConfidenceEllipseFactory;
    }

    @Inject
    public void setRigidConfidenceEllipseFactory(RigidConfidenceEllipseFactory rigidConfidenceEllipseFactory) {
        this.rigidConfidenceEllipseFactory = rigidConfidenceEllipseFactory;
    }

    @Inject
    public void setIsotropicConfidenceEllipseFactory(TrueModelConfidenceEllipseFactory trueModelConfidenceEllipseFactory) {
        this.trueModelConfidenceEllipseFactory = trueModelConfidenceEllipseFactory;
    }
}
