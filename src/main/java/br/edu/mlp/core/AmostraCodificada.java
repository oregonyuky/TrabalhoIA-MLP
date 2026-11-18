package br.edu.mlp.core;

/** Entrada normalizada e saída desejada em formato codificado. */
public record AmostraCodificada(double[] entrada, double[] saidaDesejada, int indiceClasse) {
    public AmostraCodificada {
        entrada = entrada.clone();
        saidaDesejada = saidaDesejada.clone();
        if (entrada.length == 0 || saidaDesejada.length == 0
                || indiceClasse < 0 || indiceClasse >= saidaDesejada.length) {
            throw new IllegalArgumentException("Dimensões ou classe inválidas na amostra codificada.");
        }
        for (double valor : entrada) if (!Double.isFinite(valor)) throw new IllegalArgumentException("Entrada não finita.");
        for (double valor : saidaDesejada) if (!Double.isFinite(valor)) throw new IllegalArgumentException("Saída desejada não finita.");
    }

    @Override public double[] entrada() { return entrada.clone(); }
    @Override public double[] saidaDesejada() { return saidaDesejada.clone(); }
}
