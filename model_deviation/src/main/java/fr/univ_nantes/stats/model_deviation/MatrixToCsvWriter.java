package fr.univ_nantes.stats.model_deviation;

import Jama.Matrix;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import javax.inject.Inject;
import java.io.IOException;

public class MatrixToCsvWriter {

    @Inject
    public MatrixToCsvWriter() {}

    public String write(Matrix matrix) {
        StringBuilder stringBuilder = new StringBuilder();
        try (CSVPrinter printer = new CSVPrinter(stringBuilder, CSVFormat.DEFAULT)) {
            for(int i = 0; i < matrix.getRowDimension(); i++) {
                for(int j = 0; j < matrix.getRowDimension(); j++) {
                    printer.print(matrix.get(i, j));
                }
                printer.println();
            }
            printer.flush();
            return stringBuilder.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
