package br.edu.mlp.core;

import java.util.List;

/** Dados brutos e codificados usados no treinamento e teste. */
public record DadosPreparados(ConjuntoDados treinoBruto, ConjuntoDados testeBruto,
                              List<AmostraCodificada> treino, List<AmostraCodificada> teste,
                              NormalizadorMinMax normalizador) {
    public DadosPreparados {
        treino = List.copyOf(treino);
        teste = List.copyOf(teste);
    }

    public List<String> atributos() { return treinoBruto.atributos(); }
    public List<String> classes() { return treinoBruto.classes(); }
    public int quantidadeEntradas() { return atributos().size(); }
    public int quantidadeSaidas() { return classes().size(); }
}
