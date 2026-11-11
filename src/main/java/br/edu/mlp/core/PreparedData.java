package br.edu.mlp.core;

import java.util.List;

public record PreparedData(Dataset trainingRaw, Dataset testRaw,
                           List<EncodedSample> training, List<EncodedSample> test,
                           MinMaxNormalizer normalizer) {
    public PreparedData {
        training = List.copyOf(training); test = List.copyOf(test);
    }
    public List<String> headers() { return trainingRaw.headers(); }
    public List<String> classes() { return trainingRaw.classes(); }
    public int inputCount() { return headers().size(); }
    public int outputCount() { return classes().size(); }
}
