package br.edu.mlp.core;

public record Sample(double[] values, String label) {
    public Sample {
        if (values == null || values.length == 0) throw new IllegalArgumentException("A amostra precisa de atributos.");
        values = values.clone();
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Os atributos devem ser números finitos.");
        }
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A classe não pode estar vazia.");
        label = label.strip();
    }
    @Override public double[] values() { return values.clone(); }
}
