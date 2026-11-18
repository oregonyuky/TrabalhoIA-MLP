package br.edu.mlp.core;

import java.util.Arrays;
import java.util.List;

/** Normaliza atributos usando os extremos calculados no treino. */
public final class NormalizadorMinMax {
    private final double[] minimo;
    private final double[] maximo;

    public NormalizadorMinMax(List<Amostra> amostrasTreino) {
        if (amostrasTreino.isEmpty()) throw new IllegalArgumentException("O treino não pode estar vazio.");
        int quantidade = amostrasTreino.getFirst().valores().length;
        minimo = new double[quantidade];
        maximo = new double[quantidade];
        Arrays.fill(minimo, Double.POSITIVE_INFINITY);
        Arrays.fill(maximo, Double.NEGATIVE_INFINITY);

        for (Amostra amostra : amostrasTreino) {
            double[] valores = amostra.valores();
            if (valores.length != quantidade) throw new IllegalArgumentException("Dimensões inconsistentes.");
            for (int indice = 0; indice < quantidade; indice++) {
                minimo[indice] = Math.min(minimo[indice], valores[indice]);
                maximo[indice] = Math.max(maximo[indice], valores[indice]);
            }
        }
    }

    public double[] transformar(double[] valores) {
        if (valores.length != minimo.length) throw new IllegalArgumentException("Quantidade incorreta de atributos.");
        double[] normalizados = new double[valores.length];
        for (int indice = 0; indice < valores.length; indice++) {
            if (!Double.isFinite(valores[indice])) throw new IllegalArgumentException("Os atributos devem ser finitos.");
            if (maximo[indice] == minimo[indice]) normalizados[indice] = 0.0;
            else if (valores[indice] <= minimo[indice]) normalizados[indice] = 0.0;
            else if (valores[indice] >= maximo[indice]) normalizados[indice] = 1.0;
            else {
                double intervalo = maximo[indice] - minimo[indice];
                normalizados[indice] = Double.isFinite(intervalo)
                        ? (valores[indice] - minimo[indice]) / intervalo
                        : (valores[indice] / 2.0 - minimo[indice] / 2.0)
                        / (maximo[indice] / 2.0 - minimo[indice] / 2.0);
            }
        }
        return normalizados;
    }

    public double[] minimo() { return minimo.clone(); }
    public double[] maximo() { return maximo.clone(); }
}
