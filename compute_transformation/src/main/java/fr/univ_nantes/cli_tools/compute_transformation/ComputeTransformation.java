package fr.univ_nantes.cli_tools.compute_transformation;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import plugins.fr.univ_nantes.ec_clem.ec_clem.fiducialset.FiducialSet;
import plugins.fr.univ_nantes.ec_clem.ec_clem.fiducialset.dataset.Dataset;
import plugins.fr.univ_nantes.ec_clem.ec_clem.registration.RegistrationParameter;
import plugins.fr.univ_nantes.ec_clem.ec_clem.storage.dataset.CsvToDatasetFileReader;
import plugins.fr.univ_nantes.ec_clem.ec_clem.storage.transformation.csv.TransformationToCsvFormatter;
import plugins.fr.univ_nantes.ec_clem.ec_clem.transformation.RegistrationParameterFactory;
import plugins.fr.univ_nantes.ec_clem.ec_clem.transformation.schema.TransformationSchema;
import plugins.fr.univ_nantes.ec_clem.ec_clem.transformation.schema.TransformationType;
import javax.inject.Inject;
import java.nio.file.Path;

@Command(
    name = "compute_transformation",
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    versionProvider = ManifestVersionProvider.class
)
public class ComputeTransformation implements Runnable {
    private RegistrationParameterFactory registrationParameterFactory;
    private CsvToDatasetFileReader csvToDatasetFileReader;
    private TransformationToCsvFormatter transformationToCsvFormatter;

    @Option(
        names = {"--transformation-model"},
        description = "Transformation model.\nValid values : ${COMPLETION-CANDIDATES}.",
        required = true
    )
    private TransformationType transformationModel;

    @Option(
        names = {"--source-dataset"},
        description = "Input source dataset file.",
        required = true
    )
    private Path sourceDatasetFilePath;

    @Option(
        names = {"--target-dataset"},
        description = "Input target dataset file.",
        required = true
    )
    private Path targetDatasetFilePath;

    public ComputeTransformation() {
        DaggerComputeTransformationComponent.create().inject(this);
    }

    public static void main(String... args) { // bootstrap the application
        System.exit(new CommandLine(new ComputeTransformation()).execute(args));
    }

    @Override
    public void run() {
        Dataset sourceDataset = csvToDatasetFileReader.read(sourceDatasetFilePath.toFile());
        Dataset targetDataset = csvToDatasetFileReader.read(targetDatasetFilePath.toFile());
        FiducialSet fiducialSet = new FiducialSet(sourceDataset, targetDataset);
        RegistrationParameter registrationParameters = registrationParameterFactory.getFrom(
            new TransformationSchema(fiducialSet, transformationModel, null, null, null)
        );
        System.out.println(transformationToCsvFormatter.format(registrationParameters.getTransformation()));
    }

    @Inject
    public void setRegistrationParameterFactory(RegistrationParameterFactory registrationParameterFactory) {
        this.registrationParameterFactory = registrationParameterFactory;
    }

    @Inject
    public void setCsvToDatasetFileReader(CsvToDatasetFileReader csvToDatasetFileReader) {
        this.csvToDatasetFileReader = csvToDatasetFileReader;
    }

    @Inject
    public void setTransformationToCsvFormatter(TransformationToCsvFormatter transformationToCsvFormatter) {
        this.transformationToCsvFormatter = transformationToCsvFormatter;
    }
}
