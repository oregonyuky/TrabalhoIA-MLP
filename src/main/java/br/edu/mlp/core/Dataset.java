package br.edu.mlp.core;

import java.util.List;

/** Os cabeçalhos contém somente as colunas de atributos, sem a coluna da classe. */
public record Dataset(List<String> headers, List<Sample> samples, List<String> classes) {
    public Dataset {
        headers = List.copyOf(headers);
        samples = List.copyOf(samples);
        classes = List.copyOf(classes);
        if (headers.isEmpty() || samples.isEmpty() || classes.isEmpty()) {
            throw new IllegalArgumentException("A base deve conter atributos, amostras e classes.");
        }
        for (Sample sample : samples) {
            if (sample.values().length != headers.size()) throw new IllegalArgumentException("Quantidade de atributos inconsistente.");
            if (!classes.contains(sample.label())) throw new IllegalArgumentException("Classe não cadastrada: " + sample.label());
        }
    }
    public int inputCount() { return headers.size(); }
}
