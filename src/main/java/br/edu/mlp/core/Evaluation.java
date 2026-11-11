package br.edu.mlp.core;

import java.util.List;

/** Matriz com linhas reais e colunas previstas; decisão por maior saída (argmax). */
public final class Evaluation {
    private final int[][] matrix;
    private final List<String> classes;
    private final int total;
    private final int correct;

    private Evaluation(int[][] matrix, List<String> classes, int total, int correct) {
        this.matrix = matrix; this.classes = List.copyOf(classes); this.total = total; this.correct = correct;
    }
    public static Evaluation evaluate(Network network, List<EncodedSample> samples, List<String> classes) {
        if (samples.isEmpty()) throw new IllegalArgumentException("A base de teste não pode estar vazia.");
        if (network.outputs() != classes.size()) throw new IllegalArgumentException("As classes não correspondem às saídas da rede.");
        int[][] matrix = new int[classes.size()][classes.size()];
        int correct = 0;
        for (EncodedSample sample : samples) {
            if (sample.classIndex() >= classes.size()) throw new IllegalArgumentException("Classe de teste inválida.");
            double[] output = network.predict(sample.input());
            int predicted = argmax(output);
            matrix[sample.classIndex()][predicted]++;
            if (predicted == sample.classIndex()) correct++;
        }
        return new Evaluation(matrix, classes, samples.size(), correct);
    }
    public static int argmax(double[] output) {
        if (output.length == 0) throw new IllegalArgumentException("A saída não pode estar vazia.");
        int best = 0;
        for (int i = 0; i < output.length; i++) {
            if (!Double.isFinite(output[i])) throw new ArithmeticException("Saída não finita na avaliação.");
            if (output[i] > output[best]) best = i;
        }
        return best;
    }
    public int[][] confusionMatrix() {
        int[][] copy = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) copy[i] = matrix[i].clone();
        return copy;
    }
    public List<String> classes() { return classes; }
    public int total() { return total; }
    public int correct() { return correct; }
    public double accuracy() { return (double) correct / total; }
    public double precision(int classIndex) {
        int predicted = 0;
        for (int[] row : matrix) predicted += row[classIndex];
        return predicted == 0 ? 0.0 : (double) matrix[classIndex][classIndex] / predicted;
    }
    public double recall(int classIndex) {
        int actual = 0;
        for (int count : matrix[classIndex]) actual += count;
        return actual == 0 ? 0.0 : (double) matrix[classIndex][classIndex] / actual;
    }
}
