package br.edu.mlp.core;

/** Entrada normalizada e saída desejada one-hot (um neurônio por classe). */
public record EncodedSample(double[] input, double[] target, int classIndex) {
    public EncodedSample {
        input = input.clone();
        target = target.clone();
        if (input.length == 0 || target.length == 0 || classIndex < 0 || classIndex >= target.length) {
            throw new IllegalArgumentException("Dimensões ou classe inválidas na amostra codificada.");
        }
        for (double value : input) if (!Double.isFinite(value)) throw new IllegalArgumentException("Entrada não finita.");
        for (double value : target) if (!Double.isFinite(value)) throw new IllegalArgumentException("Saída desejada não finita.");
    }
    @Override public double[] input() { return input.clone(); }
    @Override public double[] target() { return target.clone(); }
}
