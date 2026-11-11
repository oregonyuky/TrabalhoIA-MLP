package br.edu.mlp.core;

import java.util.Arrays;
import java.util.List;

/** Ajuste feito exclusivamente no treino; teste reutiliza estes mesmos extremos. */
public final class MinMaxNormalizer {
    private final double[] minimum;
    private final double[] maximum;

    public MinMaxNormalizer(List<Sample> training) {
        if (training.isEmpty()) throw new IllegalArgumentException("O treino não pode estar vazio.");
        int dimensions = training.getFirst().values().length;
        minimum = new double[dimensions]; maximum = new double[dimensions];
        Arrays.fill(minimum, Double.POSITIVE_INFINITY);
        Arrays.fill(maximum, Double.NEGATIVE_INFINITY);
        for (Sample sample : training) {
            double[] values = sample.values();
            if (values.length != dimensions) throw new IllegalArgumentException("Dimensões inconsistentes.");
            for (int i = 0; i < dimensions; i++) {
                minimum[i] = Math.min(minimum[i], values[i]);
                maximum[i] = Math.max(maximum[i], values[i]);
            }
        }
    }

    public double[] transform(double[] values) {
        if (values.length != minimum.length) throw new IllegalArgumentException("Quantidade incorreta de atributos.");
        double[] normalized = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) throw new IllegalArgumentException("Os atributos devem ser finitos.");
            if (maximum[i] == minimum[i]) { normalized[i] = 0.0; continue; }
            // Clip de valores de teste fora do intervalo aprendido, sem ajustar os extremos.
            if (values[i] <= minimum[i]) normalized[i] = 0.0;
            else if (values[i] >= maximum[i]) normalized[i] = 1.0;
            else {
                double range = maximum[i] - minimum[i];
                normalized[i] = Double.isFinite(range) ? (values[i] - minimum[i]) / range
                    : (values[i] / 2.0 - minimum[i] / 2.0) / (maximum[i] / 2.0 - minimum[i] / 2.0);
            }
        }
        return normalized;
    }
    public double[] minimum() { return minimum.clone(); }
    public double[] maximum() { return maximum.clone(); }
}
