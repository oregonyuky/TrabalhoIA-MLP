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

/** Prepara dados separados ou divide uma única base em treino e teste. */
public final class DataPreparation {
    private DataPreparation() {
    }

    public static PreparedData loadSeparate(Path trainingFile, Path testFile) throws IOException {
        if (Files.isSameFile(trainingFile, testFile)) {
            throw new IllegalArgumentException("Escolha arquivos diferentes para treino e teste; para um arquivo, use a divisão percentual.");
        }

        Dataset treino = CsvReader.read(trainingFile);
        Dataset teste = CsvReader.read(testFile);
        return preparar(treino, teste);
    }

    /** Nome em português para carregar bases de treino e teste separadas. */
    public static PreparedData carregarArquivosSeparados(Path arquivoTreino, Path arquivoTeste) throws IOException {
        return loadSeparate(arquivoTreino, arquivoTeste);
    }

    /** Embaralha e garante ao menos uma amostra de cada classe no treino. */
    public static PreparedData split(Path file, double trainingFraction, long seed) throws IOException {
        if (!Double.isFinite(trainingFraction) || trainingFraction <= 0 || trainingFraction >= 1) {
            throw new IllegalArgumentException("O percentual de treino deve ser maior que 0 e menor que 100.");
        }

        Dataset fonte = CsvReader.read(file);
        int quantidadeTreino = (int) Math.round(fonte.samples().size() * trainingFraction);
        if (quantidadeTreino < fonte.classes().size() || quantidadeTreino >= fonte.samples().size()) {
            throw new IllegalArgumentException("Esse percentual não permite treino com todas as classes e teste não vazio. Ajuste o percentual ou adicione amostras.");
        }

        List<Sample> embaralhadas = new ArrayList<>(fonte.samples());
        Random aleatorio = new Random(seed);
        Collections.shuffle(embaralhadas, aleatorio);

        List<Sample> treino = new ArrayList<>();
        List<Sample> restantes = new ArrayList<>();
        Set<String> classesRepresentadas = new HashSet<>();

        for (Sample amostra : embaralhadas) {
            if (classesRepresentadas.add(amostra.label())) {
                treino.add(amostra);
            } else {
                restantes.add(amostra);
            }
        }

        int faltantes = quantidadeTreino - treino.size();
        treino.addAll(restantes.subList(0, faltantes));
        List<Sample> teste = new ArrayList<>(restantes.subList(faltantes, restantes.size()));

        Collections.shuffle(treino, aleatorio);
        Collections.shuffle(teste, aleatorio);

        return preparar(new Dataset(fonte.headers(), treino, fonte.classes()),
                new Dataset(fonte.headers(), teste, fonte.classes()));
    }

    public static PreparedData dividir(Path arquivo, double percentualTreino, long semente) throws IOException {
        return split(arquivo, percentualTreino, semente);
    }

    private static PreparedData preparar(Dataset treino, Dataset teste) {
        if (!treino.headers().equals(teste.headers())) {
            throw new IllegalArgumentException("Treino e teste devem ter os mesmos nomes e a mesma ordem de atributos.");
        }

        for (Sample amostra : teste.samples()) {
            if (!treino.classes().contains(amostra.label())) {
                throw new IllegalArgumentException("A classe '" + amostra.label()
                        + "' do teste não aparece no treino.");
            }
        }

        MinMaxNormalizer normalizador = new MinMaxNormalizer(treino.samples());
        return new PreparedData(treino, teste,
                codificar(treino, treino.classes(), normalizador),
                codificar(teste, treino.classes(), normalizador), normalizador);
    }

    private static List<EncodedSample> codificar(Dataset dados, List<String> classes,
                                                  MinMaxNormalizer normalizador) {
        List<EncodedSample> codificadas = new ArrayList<>();

        for (Sample amostra : dados.samples()) {
            int indiceClasse = classes.indexOf(amostra.label());
            double[] saidaDesejada = new double[classes.size()];
            saidaDesejada[indiceClasse] = 1.0;
            codificadas.add(new EncodedSample(
                    normalizador.transform(amostra.values()),
                    saidaDesejada,
                    indiceClasse));
        }

        return List.copyOf(codificadas);
    }
}
