package fr.univ_nantes.stats.model_deviation;

import Jama.Matrix;
import fr.univ_nantes.stats.model_deviation.model.ShapeEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.truth.isotropic.TrueModelConfidenceEllipseFactory;
import icy.sequence.DimensionId;
import picocli.CommandLine;
import javax.inject.Inject;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.Color;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import plugins.fr.univ_nantes.ec_clem.error.ellipse.ConfidenceEllipseFactory;
import plugins.fr.univ_nantes.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.fiducialset.dataset.point.Point;
import plugins.fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import plugins.fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import plugins.fr.univ_nantes.ec_clem.registration.AffineTransformationComputer;
import plugins.fr.univ_nantes.ec_clem.registration.RigidTransformationComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.general.conjugate_gradient.ConjugateGradientRigid2DGeneralMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.sequence.DimensionSize;
import plugins.fr.univ_nantes.ec_clem.sequence.SequenceSize;
import plugins.fr.univ_nantes.ec_clem.transformation.Similarity;
import plugins.fr.univ_nantes.ec_clem.transformation.Transformation;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationSchema;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationType;
import plugins.perrine.easyclemv0.registration.likelihood.dimension2.general.interior_point.InteriorPointRigid2DGeneralMaxLikelihoodComputer;
import plugins.perrine.easyclemv0.registration.likelihood.dimension2.isotropic.interior_point.InteriorPointRigid2DIsotropicMaxLikelihoodComputer;

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
    private InteriorPointRigid2DGeneralMaxLikelihoodComputer interiorPointRigid2DGeneralMaxLikelihoodComputer;
    private ConjugateGradientRigid2DGeneralMaxLikelihoodComputer conjugateGradientRigid2DGeneralMaxLikelihoodComputer;
    private InteriorPointRigid2DIsotropicMaxLikelihoodComputer interiorPointRigid2DIsotropicMaxLikelihoodComputer;
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
                new TransformationSchema(current, TransformationType.AFFINE, getSequenceSize(), getSequenceSize()),
                alpha
            ),
            height
        );
        image.draw(image.center(affineEllipse, zTargetWithoutNoise), Color.BLUE);
        image.fill(image.center(getRectangle(affineTransformationComputer.compute(current).apply(zSource)), zTargetWithoutNoise), Color.BLUE);

        Shape rigidEllipse = shapeEllipseFactory.getFrom(
            confidenceEllipseFactory.getFrom(
                zSource,
                new TransformationSchema(current, TransformationType.RIGID, getSequenceSize(), getSequenceSize()),
                alpha
            ),
            height
        );
        image.draw(image.center(rigidEllipse, zTargetWithoutNoise), Color.ORANGE);
        image.fill(image.center(getRectangle(rigidTransformationComputer.compute(current).apply(zSource)), zTargetWithoutNoise), Color.ORANGE);

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

            Shape affineEllipse = shapeEllipseFactory.getFrom(
                confidenceEllipseFactory.getFrom(
                    zSource,
                    new TransformationSchema(current, TransformationType.AFFINE, getSequenceSize(), getSequenceSize()),
                    alpha
                ),
                height
            );
            Shape rigidEllipse = shapeEllipseFactory.getFrom(
                confidenceEllipseFactory.getFrom(
                    zSource,
                    new TransformationSchema(current, TransformationType.RIGID, getSequenceSize(), getSequenceSize()),
                    alpha
                ),
                height
            );
            Shape trueEllipse = shapeEllipseFactory.getFrom(
                trueModelConfidenceEllipseFactory.getFrom(simpleRotationTransformation.apply(zSource), current, noiseCovariance, alpha),
                height
            );

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
        Similarity isotropicMaximumLikelihood = interiorPointRigid2DIsotropicMaxLikelihoodComputer.compute(current);
        Similarity generalMaximumLikelihood = interiorPointRigid2DGeneralMaxLikelihoodComputer.compute(current);
        Similarity generalMaximumLikelihood2 = conjugateGradientRigid2DGeneralMaxLikelihoodComputer.compute(current);

        System.out.println("True transformation");
        simpleRotationTransformation.getHomogeneousMatrix().print(1,5);

        System.out.println("Schonemann transformation");
        shonemann.getHomogeneousMatrix().print(1,5);

        System.out.println("General Maximum likelihood transformation");
        generalMaximumLikelihood.getHomogeneousMatrix().print(1,5);

        System.out.println("General Maximum likelihood 2 transformation");
        generalMaximumLikelihood2.getHomogeneousMatrix().print(1,5);

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
    public void setInteriorPointRigid2DGeneralMaxLikelihoodComputer(InteriorPointRigid2DGeneralMaxLikelihoodComputer rigid2DMaxLikelihoodComputer) {
        this.interiorPointRigid2DGeneralMaxLikelihoodComputer = rigid2DMaxLikelihoodComputer;
    }

    @Inject
    public void setInteriorPointRigid2DIsotropicMaxLikelihoodComputer(InteriorPointRigid2DIsotropicMaxLikelihoodComputer rigid2DMaxLikelihoodComputer2) {
        this.interiorPointRigid2DIsotropicMaxLikelihoodComputer = rigid2DMaxLikelihoodComputer2;
    }

    @Inject
    public void setConjugateGradientRigid2DGeneralMaxLikelihoodComputer(ConjugateGradientRigid2DGeneralMaxLikelihoodComputer rigid2DMaxLikelihoodComputer) {
        this.conjugateGradientRigid2DGeneralMaxLikelihoodComputer = rigid2DMaxLikelihoodComputer;
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
