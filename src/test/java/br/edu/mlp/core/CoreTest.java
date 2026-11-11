package br.edu.mlp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @TempDir Path temporary;

    @Test void activationsMatchThePdfIncludingLinearScaleAndExtremeInputs() {
        assertEquals(0.7, Activation.LINEAR.apply(7), 1e-15);
        assertEquals(0.1, Activation.LINEAR.derivative(7), 1e-15);
        assertEquals(0.5, Activation.LOGISTIC.apply(0), 1e-15);
        assertEquals(0.25, Activation.LOGISTIC.derivative(0.5), 1e-15);
        assertEquals(0.0, Activation.TANH.apply(0), 1e-15);
        assertEquals(1.0, Activation.TANH.derivative(0), 1e-15);
        assertEquals(0.0, Activation.LOGISTIC.apply(-1000));
        assertEquals(1.0, Activation.LOGISTIC.apply(1000));
        assertEquals(-1.0, Activation.TANH.apply(-1000));
    }

    @Test void onlineBackpropagationMatchesNumericalGradientForEveryWeightAndBias() {
        for (Activation activation : Activation.values()) {
            Network network = new Network(2, 3, 2, activation, 42);
            double[] input = {0.2, 0.7};
            double[] target = {1, 0};
            double[][] beforeInput = network.weightsInputHidden();
            double[][] beforeOutput = network.weightsHiddenOutput();
            double expectedLoss = loss(input, target, beforeInput, beforeOutput, activation);
            double rate = 0.3;
            double reported = network.trainEpoch(List.of(new EncodedSample(input, target, 0)), rate, new Random(1));
            assertEquals(expectedLoss, reported, 1e-13);
            assertGradient(input, target, beforeInput, beforeOutput, activation, rate, network.weightsInputHidden(), true);
            assertGradient(input, target, beforeInput, beforeOutput, activation, rate, network.weightsHiddenOutput(), false);
        }
    }

    private static void assertGradient(double[] input, double[] target, double[][] first, double[][] second,
                                       Activation activation, double rate, double[][] actual, boolean checkFirst) {
        double[][] checked = checkFirst ? first : second;
        double epsilon = 1e-6;
        for (int row = 0; row < checked.length; row++) {
            for (int column = 0; column < checked[row].length; column++) {
                double original = checked[row][column];
                checked[row][column] = original + epsilon;
                double plus = loss(input, target, first, second, activation);
                checked[row][column] = original - epsilon;
                double minus = loss(input, target, first, second, activation);
                checked[row][column] = original;
                double gradient = (plus - minus) / (2 * epsilon);
                assertEquals(original - rate * gradient, actual[row][column], 1e-9,
                    activation + " camada " + checkFirst + " peso " + row + "," + column);
            }
        }
    }

    private static double loss(double[] input, double[] target, double[][] first, double[][] second, Activation activation) {
        double[] hidden = manualForward(input, first, activation);
        double[] output = manualForward(hidden, second, activation);
        double sum = 0;
        for (int i = 0; i < target.length; i++) sum += 0.5 * Math.pow(target[i] - output[i], 2);
        return sum;
    }

    private static double[] manualForward(double[] values, double[][] weights, Activation activation) {
        double[] output = new double[weights.length];
        for (int row = 0; row < weights.length; row++) {
            double net = weights[row][values.length];
            for (int col = 0; col < values.length; col++) net += weights[row][col] * values[col];
            output[row] = activation.apply(net);
        }
        return output;
    }

    @Test void networkLearnsNonLinearlySeparableXorAndIsReproducible() {
        List<EncodedSample> xor = List.of(
            new EncodedSample(new double[]{0, 0}, new double[]{1, 0}, 0),
            new EncodedSample(new double[]{0, 1}, new double[]{0, 1}, 1),
            new EncodedSample(new double[]{1, 0}, new double[]{0, 1}, 1),
            new EncodedSample(new double[]{1, 1}, new double[]{1, 0}, 0));
        Network first = new Network(2, 6, 2, Activation.LOGISTIC, 17);
        Network repeated = new Network(2, 6, 2, Activation.LOGISTIC, 17);
        Random orderOne = new Random(17), orderTwo = new Random(17);
        double error = 0;
        for (int epoch = 0; epoch < 15000; epoch++) {
            error = first.trainEpoch(xor, 0.5, orderOne);
            assertEquals(error, repeated.trainEpoch(xor, 0.5, orderTwo));
        }
        assertTrue(error < 0.01, "Erro XOR: " + error);
        Evaluation result = Evaluation.evaluate(first, xor, List.of("igual", "diferente"));
        assertEquals(1, result.accuracy());
        assertArrayEquals(new int[]{2, 0}, result.confusionMatrix()[0]);
        assertArrayEquals(new int[]{0, 2}, result.confusionMatrix()[1]);
    }

    @Test void normalizationUsesOnlyTrainingExtremesAndHandlesConstantColumns() throws Exception {
        Path train = csv("train.csv", "x,constante,classe\n10,7,A\n20,7,B\n");
        Path test = csv("test.csv", "x,constante,classe\n100,999,A\n15,-999,B\n");
        PreparedData prepared = DataPreparation.loadSeparate(train, test);
        assertArrayEquals(new double[]{10, 7}, prepared.normalizer().minimum());
        assertArrayEquals(new double[]{20, 7}, prepared.normalizer().maximum());
        assertArrayEquals(new double[]{0, 0}, prepared.training().get(0).input());
        assertArrayEquals(new double[]{1, 0}, prepared.test().get(0).input());
        assertArrayEquals(new double[]{0.5, 0}, prepared.test().get(1).input());
        assertArrayEquals(new double[]{0, 1}, prepared.test().get(1).target());
    }

    @Test void normalizationAlsoHandlesFiniteExtremesWhoseRangeOverflows() {
        MinMaxNormalizer normalizer = new MinMaxNormalizer(List.of(
            new Sample(new double[]{-Double.MAX_VALUE}, "A"),
            new Sample(new double[]{Double.MAX_VALUE}, "B")));
        assertArrayEquals(new double[]{0.5}, normalizer.transform(new double[]{0}));
    }

    @Test void csvHandlesBomQuotedHeadersEscapedQuotesWhitespaceAndDetectsDimensions() throws Exception {
        Path file = csv("quoted.csv", "\uFEFF\"x, primeiro\",y,classe\n1,2,\" CA \"\n3,4,\"C\"\"B\"\n\n");
        Dataset data = CsvReader.read(file);
        assertEquals(List.of("x, primeiro", "y"), data.headers());
        assertEquals(List.of("C\"B", "CA"), data.classes());
        assertEquals("CA", data.samples().get(0).label());
        assertEquals(2, data.inputCount());
    }

    @Test void csvRejectsMissingNonFiniteAndWrongColumnValues() throws Exception {
        for (String row : List.of("NaN,A", "Infinity,A", ",A", "1,", "1,2,A")) {
            Path file = csv("invalid.csv", "x,classe\n" + row + "\n");
            assertThrows(IllegalArgumentException.class, () -> CsvReader.read(file), row);
        }
    }

    @Test void splitIsReproducibleDisjointAndPreservesEveryClassInTraining() throws Exception {
        Path file = csv("all.csv", "id,classe\n1,A\n2,A\n3,A\n4,B\n5,B\n6,C\n7,C\n8,C\n9,C\n10,C\n");
        PreparedData first = DataPreparation.split(file, 0.6, 25);
        PreparedData repeated = DataPreparation.split(file, 0.6, 25);
        assertEquals(6, first.training().size());
        assertEquals(4, first.test().size());
        assertEquals(List.of("A", "B", "C"), first.classes());
        Set<String> labels = first.trainingRaw().samples().stream().map(Sample::label).collect(Collectors.toSet());
        assertEquals(Set.of("A", "B", "C"), labels);
        Set<Double> trainingIds = first.trainingRaw().samples().stream().map(s -> s.values()[0]).collect(Collectors.toSet());
        Set<Double> testIds = first.testRaw().samples().stream().map(s -> s.values()[0]).collect(Collectors.toSet());
        assertTrue(trainingIds.stream().noneMatch(testIds::contains));
        for (int i = 0; i < first.training().size(); i++) {
            assertArrayEquals(first.training().get(i).input(), repeated.training().get(i).input());
        }
        assertThrows(IllegalArgumentException.class, () -> DataPreparation.split(file, 0.1, 25));
    }

    @Test void separateFilesRejectUnknownTestClassDifferentHeadersAndSameFile() throws Exception {
        Path train = csv("training.csv", "x,classe\n1,A\n2,B\n");
        Path unknown = csv("unknown.csv", "x,classe\n3,C\n");
        Path headers = csv("headers.csv", "y,classe\n3,A\n");
        assertThrows(IllegalArgumentException.class, () -> DataPreparation.loadSeparate(train, unknown));
        assertThrows(IllegalArgumentException.class, () -> DataPreparation.loadSeparate(train, headers));
        assertThrows(IllegalArgumentException.class, () -> DataPreparation.loadSeparate(train, train));
    }

    @Test void plateauRequiresAFullWindowAndCanBeOfferedAgainAfterReset() {
        PlateauDetector detector = new PlateauDetector(10, 0.00001);
        for (int epoch = 0; epoch < 9; epoch++) assertFalse(detector.add(0.42));
        assertTrue(detector.add(0.42));
        assertEquals(0, detector.standardDeviation(), 1e-15);
        detector.reset();
        for (int epoch = 0; epoch < 9; epoch++) assertFalse(detector.add(0.42));
        assertTrue(detector.add(0.42));
        detector.reset();
        for (int epoch = 0; epoch < 10; epoch++) assertFalse(detector.add(epoch));
        assertThrows(IllegalArgumentException.class, () -> new PlateauDetector(9, 0));
    }

    @Test void confusionMatrixUsesActualRowsPredictedColumnsAndCorrectPrecisionRecall() {
        Network network = new Network(1, 2, 2, Activation.LOGISTIC, 10);
        double[] input = {0.5};
        int predicted = Evaluation.argmax(network.predict(input));
        int other = 1 - predicted;
        List<EncodedSample> samples = List.of(
            encoded(input, predicted), encoded(input, predicted), encoded(input, other));
        Evaluation result = Evaluation.evaluate(network, samples, List.of("A", "B"));
        assertEquals(2, result.confusionMatrix()[predicted][predicted]);
        assertEquals(1, result.confusionMatrix()[other][predicted]);
        assertEquals(2.0 / 3, result.accuracy(), 1e-15);
        assertEquals(2.0 / 3, result.precision(predicted), 1e-15);
        assertEquals(1, result.recall(predicted));
        assertEquals(0, result.precision(other));
        assertEquals(0, result.recall(other));
    }

    @Test void configurationRejectsInvalidRatesAndErrorThresholds() {
        for (double rate : new double[]{0, -0.5, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new TrainingConfig(5, Activation.LOGISTIC, rate, 10, 0.01, 42, 10, 0.00001));
        }
        assertDoesNotThrow(() -> new TrainingConfig(5, Activation.LOGISTIC, 1, 10, 0, 42, 10, 0));
    }

    private static EncodedSample encoded(double[] input, int classIndex) {
        double[] target = new double[2]; target[classIndex] = 1;
        return new EncodedSample(input, target, classIndex);
    }
    private Path csv(String name, String content) throws Exception {
        return Files.writeString(temporary.resolve(name), content, StandardCharsets.UTF_8);
    }
}
