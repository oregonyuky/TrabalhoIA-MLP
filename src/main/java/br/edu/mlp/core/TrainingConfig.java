package br.edu.mlp.core;

import java.util.Objects;

public record TrainingConfig(int hidden, Activation activation, double learningRate,
                             int maxEpochs, double minError, long seed,
                             int plateauWindow, double plateauThreshold) {
    public TrainingConfig {
        if (hidden < 1) throw new IllegalArgumentException("A camada oculta deve conter ao menos um neurônio.");
        Objects.requireNonNull(activation, "Escolha a função de transferência.");
        if (!Double.isFinite(learningRate) || learningRate <= 0 || learningRate > 1) throw new IllegalArgumentException("A taxa de aprendizagem deve satisfazer 0 < η ≤ 1.");
        if (maxEpochs < 1) throw new IllegalArgumentException("Informe ao menos uma época.");
        if (!Double.isFinite(minError) || minError < 0) throw new IllegalArgumentException("O erro mínimo deve ser finito e não negativo.");
        if (plateauWindow < 10) throw new IllegalArgumentException("A janela do platô deve ter ao menos 10 épocas.");
        if (!Double.isFinite(plateauThreshold) || plateauThreshold < 0) throw new IllegalArgumentException("O limiar do platô deve ser finito e não negativo.");
    }
}
