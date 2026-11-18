package br.edu.mlp.core;

import java.util.Arrays;
import java.util.List;

/** Normaliza os atributos usando o intervalo mínimo e máximo calculado no treino. */
public final class MinMaxNormalizer {
    private final double[] minimo;
    private final double[] maximo;

    public MinMaxNormalizer(List<Sample> amostrasTreino) {
        if (amostrasTreino.isEmpty()) {
            throw new IllegalArgumentException("O treino não pode estar vazio.");
        }

        int quantidadeAtributos = amostrasTreino.getFirst().values().length;
        minimo = new double[quantidadeAtributos];
        maximo = new double[quantidadeAtributos];
        Arrays.fill(minimo, Double.POSITIVE_INFINITY);
        Arrays.fill(maximo, Double.NEGATIVE_INFINITY);

        for (Sample amostra : amostrasTreino) {
            double[] valores = amostra.values();
            if (valores.length != quantidadeAtributos) {
                throw new IllegalArgumentException("Dimensões inconsistentes.");
            }

            for (int indice = 0; indice < quantidadeAtributos; indice++) {
                minimo[indice] = Math.min(minimo[indice], valores[indice]);
                maximo[indice] = Math.max(maximo[indice], valores[indice]);
            }
        }
    }

    public double[] transform(double[] valores) {
        if (valores.length != minimo.length) {
            throw new IllegalArgumentException("Quantidade incorreta de atributos.");
        }

        double[] normalizados = new double[valores.length];
        for (int indice = 0; indice < valores.length; indice++) {
            if (!Double.isFinite(valores[indice])) {
                throw new IllegalArgumentException("Os atributos devem ser finitos.");
            }

            if (maximo[indice] == minimo[indice]) {
                normalizados[indice] = 0.0;
            } else if (valores[indice] <= minimo[indice]) {
                normalizados[indice] = 0.0;
            } else if (valores[indice] >= maximo[indice]) {
                normalizados[indice] = 1.0;
            } else {
                double intervalo = maximo[indice] - minimo[indice];
                normalizados[indice] = Double.isFinite(intervalo)
                        ? (valores[indice] - minimo[indice]) / intervalo
                        : (valores[indice] / 2.0 - minimo[indice] / 2.0)
                        / (maximo[indice] / 2.0 - minimo[indice] / 2.0);
            }
        }
        return normalizados;
    }

    public double[] transformar(double[] valores) {
        return transform(valores);
    }

    public double[] minimum() {
        return minimo.clone();
    }

    public double[] maximum() {
        return maximo.clone();
    }

    public double[] minimo() {
        return minimum();
    }

    public double[] maximo() {
        return maximum();
    }
}
