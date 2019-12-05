package fr.univ_nantes.stats.solver;

import Jama.Matrix;
import picocli.CommandLine;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import plugins.fr.univ_nantes.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import plugins.fr.univ_nantes.ec_clem.fixtures.transformation.TestTransformationFactory;
import plugins.fr.univ_nantes.ec_clem.registration.RigidTransformationComputer;
import plugins.fr.univ_nantes.ec_clem.registration.likelihood.dimension2.general.conjugate_gradient.ConjugateGradientRigid2DGeneralMaxLikelihoodComputer;
import plugins.fr.univ_nantes.ec_clem.transformation.Similarity;
import plugins.fr.univ_nantes.ec_clem.transformation.schema.TransformationType;
import plugins.perrine.easyclemv0.registration.likelihood.dimension2.general.interior_point.InteriorPointRigid2DGeneralMaxLikelihoodComputer;
import plugins.perrine.easyclemv0.registration.likelihood.dimension2.isotropic.interior_point.InteriorPointRigid2DIsotropicMaxLikelihoodComputer;

@Command(
    name = "solver",
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true
)
public class Main {

    private TestTransformationFactory testTransformationFactory;
    private TestFiducialSetFactory testFiducialSetFactory;
    private RigidTransformationComputer rigidTransformationComputer;
    private InteriorPointRigid2DGeneralMaxLikelihoodComputer interiorPointRigid2DGeneralMaxLikelihoodComputer;
    private ConjugateGradientRigid2DGeneralMaxLikelihoodComputer conjugateGradientRigid2DGeneralMaxLikelihoodComputer;
    private InteriorPointRigid2DIsotropicMaxLikelihoodComputer interiorPointRigid2DIsotropicMaxLikelihoodComputer;

    @Option(
        names = { "-n" },
        description = "Number of points.\nDefault : ${DEFAULT-VALUE}.",
        defaultValue = "10"
    )
    private int n;

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
}
