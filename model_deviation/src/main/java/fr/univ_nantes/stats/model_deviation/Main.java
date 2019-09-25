package fr.univ_nantes.stats.model_deviation;

import fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import fr.univ_nantes.stats.model_deviation.model.estimated.affine.AffineConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.estimated.rigid.RigidConfidenceEllipseFactory;
import fr.univ_nantes.stats.model_deviation.model.truth.isotropic.IsotropicConfidenceEllipseFactory;
import picocli.CommandLine;
import plugins.perrine.easyclemv0.fiducialset.FiducialSet;
import plugins.perrine.easyclemv0.fiducialset.dataset.point.Point;
import plugins.perrine.easyclemv0.registration.AffineTransformationComputer;
import plugins.perrine.easyclemv0.registration.RigidTransformationComputer;
import plugins.perrine.easyclemv0.transformation.Similarity;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "model_deviation",
    mixinStandardHelpOptions = true
)
public class Main {

    private TestTransformationFactory testTransformationFactory;
    private TestFiducialSetFactory testFiducialSetFactory;
    private AffineTransformationComputer affineTransformationComputer;
    private RigidTransformationComputer rigidTransformationComputer;

    private AffineConfidenceEllipseFactory affineConfidenceEllipseFactory;
    private RigidConfidenceEllipseFactory rigidConfidenceEllipseFactory;
    private IsotropicConfidenceEllipseFactory isotropicConfidenceEllipseFactory;

    @Option(
        names = { "-n" },
        description = "Number of points. Default : ${DEFAULT-VALUE}.",
        defaultValue = "10"
    )
    private int n;

    @Option(
        names = { "-s", "--sigma" },
        description = "Noise standard deviation. Default : ${DEFAULT-VALUE}.",
        defaultValue = "10"
    )
    private int sigma;

    @Option(
        names = { "-a", "--alpha" },
        description = "Significance threshold. Default : ${DEFAULT-VALUE}.",
        defaultValue = "0.95"
    )
    private float alpha;

    @Option(
        names = { "-t", "--theta" },
        description = "Rotation angle. Default : ${DEFAULT-VALUE}.",
        defaultValue = "38"
    )
    private int theta;

    @Option(
        names = {"--width"},
        description = "Image width. Default : ${DEFAULT-VALUE}.",
        defaultValue = "512"
    )
    private int width;

    @Option(
        names = {"--height"},
        description = "Image height. Default : ${DEFAULT-VALUE}.",
        defaultValue = "512"
    )
    private int height;

    public Main() {
        DaggerMainComponent.create().inject(this);
    }

    @Command
    public void image() {
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setColor(Color.GRAY);
        g2d.fillRect(0, 0, width, height);
        int[] range = new int[]{width, height};
        Similarity simpleRotationTransformation = testTransformationFactory.getSimpleRotationTransformation(theta, 2);

        Point zSource = testFiducialSetFactory.getRandomPoint(range);
        FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
            simpleRotationTransformation, n, range
        );
        testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), sigma);

        Point zTarget = testFiducialSetFactory.addGaussianNoise(simpleRotationTransformation.apply(zSource), sigma);
        draw(getRectangle(zTarget), g2d, Color.RED);

        Shape affineEllipse = affineConfidenceEllipseFactory.getFrom(zSource, current, alpha, height);
        draw(affineEllipse, g2d, Color.BLUE);
        draw(getRectangle(affineTransformationComputer.compute(current).apply(zSource)), g2d, Color.BLUE);

        Shape rigidEllipse = rigidConfidenceEllipseFactory.getFrom(zSource, current, alpha, height);
        draw(rigidEllipse, g2d, Color.ORANGE);
        draw(getRectangle(rigidTransformationComputer.compute(current).apply(zSource)), g2d, Color.ORANGE);

        Shape trueEllipse = isotropicConfidenceEllipseFactory.getFrom(simpleRotationTransformation.apply(zSource), current, alpha, height, sigma);
        draw(trueEllipse, g2d, Color.WHITE);
        draw(getRectangle(simpleRotationTransformation.apply(zSource)), g2d, Color.WHITE);

        g2d.dispose();
        File file = new File("image.png");
        try {
            ImageIO.write(bufferedImage, "png", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void draw(Shape shape, Graphics2D graphic, Color color) {
        graphic.setColor(color);
        graphic.draw(shape);
    }

    private Rectangle getRectangle(Point point) {
        return new Rectangle((int) point.get(0), (int) (height - point.get(1)), 1, 1);
    }

    @Command
    public void simu(
        @Option(
            names = { "-N" },
            description = "Number of iterrations. Default : ${DEFAULT-VALUE}.",
            defaultValue = "1000000"
        ) int N
    ) {
        int[] range = new int[]{width, height};
        Similarity simpleRotationTransformation = testTransformationFactory.getSimpleRotationTransformation(theta, 2);

        int affineCounter = 0;
        int rigidCounter = 0;
        int trueCounter = 0;

        for (int i = 0; i < N; i++) {
            Point zSource = testFiducialSetFactory.getRandomPoint(range);
            FiducialSet current = testFiducialSetFactory.getRandomFromTransformation(
                simpleRotationTransformation, n, range
            );
            testFiducialSetFactory.addGaussianNoise(current.getTargetDataset(), sigma);
            Point zTarget = testFiducialSetFactory.addGaussianNoise(simpleRotationTransformation.apply(zSource), sigma);

            Shape affineEllipse = affineConfidenceEllipseFactory.getFrom(zSource, current, alpha, height);
            Shape rigidEllipse = rigidConfidenceEllipseFactory.getFrom(zSource, current, alpha, height);
            Shape trueEllipse = isotropicConfidenceEllipseFactory.getFrom(simpleRotationTransformation.apply(zSource), current, alpha, height, sigma);

            if (affineEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
                affineCounter++;
            }
            if (rigidEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
                rigidCounter++;
            }
            if (trueEllipse.contains(zTarget.get(0), (int) (height - zTarget.get(1)))) {
                trueCounter++;
            }
        }

        System.out.println(String.format("Affine model : %.3f %%", (double) affineCounter / N * 100));
        System.out.println(String.format("Rigid model : %.3f %%", (double) rigidCounter / N * 100));
        System.out.println(String.format("True model : %.3f %%", (double) trueCounter / N * 100));
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
    public void setAffineConfidenceEllipseFactory(AffineConfidenceEllipseFactory affineConfidenceEllipseFactory) {
        this.affineConfidenceEllipseFactory = affineConfidenceEllipseFactory;
    }

    @Inject
    public void setRigidConfidenceEllipseFactory(RigidConfidenceEllipseFactory rigidConfidenceEllipseFactory) {
        this.rigidConfidenceEllipseFactory = rigidConfidenceEllipseFactory;
    }

    @Inject
    public void setIsotropicConfidenceEllipseFactory(IsotropicConfidenceEllipseFactory isotropicConfidenceEllipseFactory) {
        this.isotropicConfidenceEllipseFactory = isotropicConfidenceEllipseFactory;
    }
}
