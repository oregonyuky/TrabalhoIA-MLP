package br.edu.mlp.core;

import java.util.List;

public record ConjuntoDados(List<String> atributos, List<Amostra> amostras, List<String> classes) {
    public ConjuntoDados {
        atributos = List.copyOf(atributos); amostras = List.copyOf(amostras); classes = List.copyOf(classes);
        if (atributos.isEmpty() || amostras.isEmpty() || classes.isEmpty()) throw new IllegalArgumentException("A base deve conter atributos, amostras e classes.");
        for (Amostra amostra : amostras) {
            if (amostra.valores().length != atributos.size()) throw new IllegalArgumentException("Quantidade de atributos inconsistente.");
            if (!classes.contains(amostra.classe())) throw new IllegalArgumentException("Classe não cadastrada: " + amostra.classe());
        }
    }
    public int quantidadeEntradas() { return atributos.size(); }
}
