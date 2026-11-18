package br.edu.mlp.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Prepara dados separados ou realiza uma divisão aleatória. */
public final class PreparacaoDados {
    private PreparacaoDados() {}

    public static DadosPreparados carregarArquivosSeparados(Path arquivoTreino, Path arquivoTeste) throws IOException {
        if (Files.isSameFile(arquivoTreino, arquivoTeste)) {
            throw new IllegalArgumentException("Escolha arquivos diferentes para treino e teste.");
        }
        return preparar(LeitorCsv.ler(arquivoTreino), LeitorCsv.ler(arquivoTeste));
    }

    public static DadosPreparados dividir(Path arquivo, double fracaoTreino, long semente) throws IOException {
        if (!Double.isFinite(fracaoTreino) || fracaoTreino <= 0 || fracaoTreino >= 1) {
            throw new IllegalArgumentException("A fração de treino deve ser maior que 0 e menor que 1.");
        }

        ConjuntoDados fonte = LeitorCsv.ler(arquivo);
        int quantidadeTreino = (int) Math.round(fonte.amostras().size() * fracaoTreino);
        if (quantidadeTreino < fonte.classes().size() || quantidadeTreino >= fonte.amostras().size()) {
            throw new IllegalArgumentException("A divisão não permite todas as classes no treino e dados no teste.");
        }

        Random aleatorio = new Random(semente);
        List<Amostra> embaralhadas = new ArrayList<>(fonte.amostras());
        Collections.shuffle(embaralhadas, aleatorio);

        List<Amostra> treino = new ArrayList<>();
        List<Amostra> restantes = new ArrayList<>();
        Set<String> representadas = new HashSet<>();
        for (Amostra amostra : embaralhadas) {
            if (representadas.add(amostra.classe())) treino.add(amostra);
            else restantes.add(amostra);
        }

        int faltantes = quantidadeTreino - treino.size();
        treino.addAll(restantes.subList(0, faltantes));
        List<Amostra> teste = new ArrayList<>(restantes.subList(faltantes, restantes.size()));
        Collections.shuffle(treino, aleatorio);
        Collections.shuffle(teste, aleatorio);

        ConjuntoDados dadosTreino = new ConjuntoDados(fonte.atributos(), treino, fonte.classes());
        ConjuntoDados dadosTeste = new ConjuntoDados(fonte.atributos(), teste, fonte.classes());
        return preparar(dadosTreino, dadosTeste);
    }

    private static DadosPreparados preparar(ConjuntoDados treino, ConjuntoDados teste) {
        if (!treino.atributos().equals(teste.atributos())) {
            throw new IllegalArgumentException("Treino e teste devem ter os mesmos atributos e na mesma ordem.");
        }
        for (Amostra amostra : teste.amostras()) {
            if (!treino.classes().contains(amostra.classe())) {
                throw new IllegalArgumentException("A classe '" + amostra.classe() + "' do teste não aparece no treino.");
            }
        }

        NormalizadorMinMax normalizador = new NormalizadorMinMax(treino.amostras());
        return new DadosPreparados(treino, teste,
                codificar(treino, treino.classes(), normalizador),
                codificar(teste, treino.classes(), normalizador), normalizador);
    }

    private static List<AmostraCodificada> codificar(ConjuntoDados dados, List<String> classes,
                                                     NormalizadorMinMax normalizador) {
        List<AmostraCodificada> codificadas = new ArrayList<>();
        for (Amostra amostra : dados.amostras()) {
            int indice = classes.indexOf(amostra.classe());
            double[] desejada = new double[classes.size()];
            desejada[indice] = 1.0;
            codificadas.add(new AmostraCodificada(normalizador.transformar(amostra.valores()), desejada, indice));
        }
        return List.copyOf(codificadas);
    }
}
