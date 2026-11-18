package br.edu.mlp.core;

import java.util.Objects;

/** Configurações utilizadas durante o treinamento da rede. */
public record ConfiguracaoTreino(int neurôniosOcultos, Ativacao ativacao, double taxaAprendizagem,
                                 int maximoEpocas, double erroMinimo, long semente,
                                 int tamanhoJanelaPlato, double limitePlato) {
    public ConfiguracaoTreino {
        if (neurôniosOcultos < 1) throw new IllegalArgumentException("A camada oculta deve conter ao menos um neurônio.");
        Objects.requireNonNull(ativacao, "Escolha a função de transferência.");
        if (!Double.isFinite(taxaAprendizagem) || taxaAprendizagem <= 0 || taxaAprendizagem > 1) throw new IllegalArgumentException("A taxa de aprendizagem deve satisfazer 0 < η ≤ 1.");
        if (maximoEpocas < 1) throw new IllegalArgumentException("Informe ao menos uma época.");
        if (!Double.isFinite(erroMinimo) || erroMinimo < 0) throw new IllegalArgumentException("O erro mínimo deve ser finito e não negativo.");
        if (tamanhoJanelaPlato < 10) throw new IllegalArgumentException("A janela do platô deve ter ao menos 10 épocas.");
        if (!Double.isFinite(limitePlato) || limitePlato < 0) throw new IllegalArgumentException("O limiar do platô deve ser finito e não negativo.");
    }
}
