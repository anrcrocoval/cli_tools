package fr.univ_nantes.stats.model_deviation;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import org.apache.commons.math3.distribution.FDistribution;
import picocli.CommandLine;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.matrix.MatrixUtil;
import plugins.perrine.easyclemv0.registration.AffineTransformationComputer;
import plugins.perrine.easyclemv0.registration.RigidTransformationComputer;
import plugins.perrine.easyclemv0.registration.TransformationComputer;
import plugins.perrine.easyclemv0.transformation.Similarity;
import plugins.perrine.easyclemv0.transformation.Transformation;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "model_deviation")
public class Main implements Runnable {

    private TestTransformationFactory testTransformationFactory;
    private TestFiducialSetFactory testFiducialSetFactory;
    private AffineTransformationComputer affineTransformationComputer;
    private RigidTransformationComputer rigidTransformationComputer;
    private MatrixUtil matrixUtil;

    @CommandLine.Option(
        names = { "-n" },
        description = "Number of points. Default : ${DEFAULT-VALUE}.",
        defaultValue = "10"
    )
    private int n;

    @CommandLine.Option(
        names = { "-v" },
        description = "Noise variance. Default : ${DEFAULT-VALUE}.",
        defaultValue = "10"
    )
    private int v;

    @CommandLine.Option(
        names = {"-h", "--help"},
        usageHelp = true,
        description = "Display this help message."
    )
    private boolean help;

    private int[] range = new int[]{1024, 1024};

    public Main() {
        DaggerMainComponent.create().inject(this);
    }

    @Override
    public void run() {
        int width = range[0];
        int height = range[1];
        double angle = 38;
        Similarity simpleRotationTransformation = testTransformationFactory.getSimpleRotationTransformation(angle, range.length);
        FiducialSet randomFromTransformationFiducialSet = testFiducialSetFactory.getRandomFromTransformation(
            simpleRotationTransformation, n, range
        );
        testFiducialSetFactory.addGaussianNoise(randomFromTransformationFiducialSet.getTargetDataset(), v);
        FDistribution fisher = new FDistribution(
            randomFromTransformationFiducialSet.getTargetDataset().getDimension(),
            n - randomFromTransformationFiducialSet.getTargetDataset().getDimension() - randomFromTransformationFiducialSet.getSourceDataset().getDimension()
        );
        Point z = testFiducialSetFactory.getRandomPoint(range);
        double rightHand = z.getMatrix().transpose().times(
            matrixUtil.pseudoInverse(
                randomFromTransformationFiducialSet.getSourceDataset().getMatrix().transpose().times(
                    randomFromTransformationFiducialSet.getSourceDataset().getMatrix()
                )
            )
        ).times(z.getMatrix()).times(
            (float) (
                randomFromTransformationFiducialSet.getTargetDataset().getDimension()
                    * (n - randomFromTransformationFiducialSet.getSourceDataset().getDimension() - 1)
            ) / (
                n
                    - randomFromTransformationFiducialSet.getSourceDataset().getDimension()
                    - randomFromTransformationFiducialSet.getTargetDataset().getDimension()
            ) * fisher.inverseCumulativeProbability(0.95)
        ).get(0, 0);

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setColor(Color.GRAY);
        g2d.fillRect(0, 0, width, height);

        draw(
            getShapeList(affineTransformationComputer, randomFromTransformationFiducialSet, z, rightHand, height),
            g2d,
            Color.BLUE
        );

        draw(
            getShapeList(rigidTransformationComputer, randomFromTransformationFiducialSet, z, rightHand, height),
            g2d,
            Color.ORANGE
        );

        g2d.setColor(Color.RED);
        for(int i = 0; i < randomFromTransformationFiducialSet.getN(); i++) {
            Point p = randomFromTransformationFiducialSet.getTargetDataset().getPoint(i);
            g2d.fillRect((int) p.get(0), (int) (height - p.get(1)), 5, 5);
        }

        g2d.setColor(Color.GREEN);
        for(int i = 0; i < randomFromTransformationFiducialSet.getN(); i++) {
            Point p = randomFromTransformationFiducialSet.getSourceDataset().getPoint(i);
            g2d.fillRect((int) p.get(0), (int) (height - p.get(1)), 5, 5);
        }

        g2d.setColor(Color.WHITE);
        g2d.drawRect((int) simpleRotationTransformation.apply(z).get(0), (int) (height - simpleRotationTransformation.apply(z).get(1)), 1, 1);

        g2d.dispose();
        File file = new File("image.png");
        try {
            ImageIO.write(bufferedImage, "png", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Shape> getShapeList(TransformationComputer transformationComputer, FiducialSet fiducialSet, Point z, double rightHand, int height) {
        Transformation transformation = transformationComputer.compute(fiducialSet);
        List<Shape> shapeList = new ArrayList<>();
        shapeList.add(getConfidenceEllipse(transformation, fiducialSet, z, rightHand, height));
        shapeList.add(getEstimatedPoint(transformation, z, height));
        return shapeList;
    }

    private void draw(List<Shape> shapelist, Graphics2D graphic, Color color) {
        graphic.setColor(color);
        for(Shape s : shapelist) {
            graphic.draw(s);
        }
    }

    private Shape getConfidenceEllipse(Transformation transformation, FiducialSet fiducialSet, Point z, double rightHand, int height) {
        Matrix covarianceEstimate = getCovarianceMatrixEstimate(transformation, fiducialSet);
        EigenvalueDecomposition eigenvalueDecomposition = new EigenvalueDecomposition(covarianceEstimate.times(n));
        double[] eigenValues = eigenvalueDecomposition.getRealEigenvalues();
        double a = Math.sqrt(eigenValues[0] * rightHand);
        double b = Math.sqrt(eigenValues[1] * rightHand);
        return AffineTransform.getTranslateInstance(transformation.apply(z).get(0), height - transformation.apply(z).get(1)).createTransformedShape(
            AffineTransform.getRotateInstance(-1 * Math.atan2(
                eigenvalueDecomposition.getV().get(1, 0),
                eigenvalueDecomposition.getV().get(0, 0)
            )).createTransformedShape(
                new Ellipse2D.Double(-a, -b, 2 * a, 2 * b)
            )
        );
    }

    private Shape getEstimatedPoint(Transformation transformation, Point z, int height) {
        Point apply = transformation.apply(z);
        return new Rectangle((int) apply.get(0), (int) (height - apply.get(1)), 1, 1);
    }

    private Matrix getCovarianceMatrixEstimate(Transformation transformation, FiducialSet fiducialSet) {
        Matrix residuals = fiducialSet.getTargetDataset().getMatrix().minus(
            transformation.apply(fiducialSet.getSourceDataset()).getMatrix()
        );
        return residuals.transpose().times(residuals).times(1 / (double) (n - fiducialSet.getSourceDataset().getDimension() - 1));
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

    @Inject
    public void setRigidTransformationComputer(RigidTransformationComputer rigidTransformationComputer) {
        this.rigidTransformationComputer = rigidTransformationComputer;
    }

    @Inject
    public void setMatrixUtil(MatrixUtil matrixUtil) {
        this.matrixUtil = matrixUtil;
    }
}
