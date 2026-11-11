package br.edu.mlp.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class DataPreparation {
    private DataPreparation() { }

    public static PreparedData loadSeparate(Path trainingFile, Path testFile) throws IOException {
        if (java.nio.file.Files.isSameFile(trainingFile, testFile)) {
            throw new IllegalArgumentException("Escolha arquivos diferentes para treino e teste; para um arquivo, use a divisão percentual.");
        }
        Dataset training = CsvReader.read(trainingFile);
        Dataset test = CsvReader.read(testFile);
        return prepare(training, test);
    }

    /** Embaralha e garante ao menos uma amostra de cada classe no treino. */
    public static PreparedData split(Path file, double trainingFraction, long seed) throws IOException {
        if (!Double.isFinite(trainingFraction) || trainingFraction <= 0 || trainingFraction >= 1) {
            throw new IllegalArgumentException("O percentual de treino deve ser maior que 0 e menor que 100.");
        }
        Dataset source = CsvReader.read(file);
        int trainingCount = (int) Math.round(source.samples().size() * trainingFraction);
        if (trainingCount < source.classes().size() || trainingCount >= source.samples().size()) {
            throw new IllegalArgumentException("Esse percentual não permite treino com todas as classes e teste não vazio. Ajuste o percentual ou adicione amostras.");
        }
        List<Sample> shuffled = new ArrayList<>(source.samples());
        Random random = new Random(seed);
        Collections.shuffle(shuffled, random);
        List<Sample> training = new ArrayList<>();
        List<Sample> remaining = new ArrayList<>();
        Set<String> represented = new HashSet<>();
        for (Sample sample : shuffled) {
            if (represented.add(sample.label())) training.add(sample);
            else remaining.add(sample);
        }
        int needed = trainingCount - training.size();
        training.addAll(remaining.subList(0, needed));
        List<Sample> test = new ArrayList<>(remaining.subList(needed, remaining.size()));
        Collections.shuffle(training, random);
        Collections.shuffle(test, random);
        return prepare(new Dataset(source.headers(), training, source.classes()),
            new Dataset(source.headers(), test, source.classes()));
    }

    private static PreparedData prepare(Dataset training, Dataset test) {
        if (!training.headers().equals(test.headers())) {
            throw new IllegalArgumentException("Treino e teste devem ter os mesmos nomes e a mesma ordem de atributos.");
        }
        for (Sample sample : test.samples()) {
            if (!training.classes().contains(sample.label())) {
                throw new IllegalArgumentException("A classe '" + sample.label() + "' do teste não aparece no treino.");
            }
        }
        MinMaxNormalizer normalizer = new MinMaxNormalizer(training.samples());
        return new PreparedData(training, test, encode(training, training.classes(), normalizer),
            encode(test, training.classes(), normalizer), normalizer);
    }

    private static List<EncodedSample> encode(Dataset data, List<String> classes, MinMaxNormalizer normalizer) {
        List<EncodedSample> encoded = new ArrayList<>();
        for (Sample sample : data.samples()) {
            int classIndex = classes.indexOf(sample.label());
            double[] target = new double[classes.size()];
            target[classIndex] = 1.0;
            encoded.add(new EncodedSample(normalizer.transform(sample.values()), target, classIndex));
        }
        return List.copyOf(encoded);
    }
}
