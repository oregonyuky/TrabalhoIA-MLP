package br.edu.mlp.core;

/** Amostra bruta contendo atributos e rótulo da classe. */
public record Amostra(double[] valores, String classe) {
    public Amostra {
        if (valores == null || valores.length == 0) {
            throw new IllegalArgumentException("A amostra precisa de atributos.");
        }
        valores = valores.clone();
        for (double valor : valores) {
            if (!Double.isFinite(valor)) throw new IllegalArgumentException("Os atributos devem ser números finitos.");
        }
        if (classe == null || classe.isBlank()) throw new IllegalArgumentException("A classe não pode estar vazia.");
        classe = classe.strip();
    }

    @Override public double[] valores() { return valores.clone(); }
}
