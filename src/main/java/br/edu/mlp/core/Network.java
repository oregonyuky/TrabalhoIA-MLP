package br.edu.mlp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** MLP autoral com uma camada oculta, bias e atualização padrão a padrão. */
public final class Network {
    private final int inputs;
    private final int hidden;   
    private final int outputs;
    private final Activation activation;
    // [neurônio destino][neurônio origem]; a última coluna contém o bias.
    private final double[][] inputHidden;
    private final double[][] hiddenOutput;

    public Network(int inputs, int hidden, int outputs, Activation activation, long seed) {
        if (inputs <= 0 || hidden <= 0 || outputs <= 0) throw new IllegalArgumentException("Todas as camadas devem ter ao menos um neurônio.");
        this.inputs = inputs; this.hidden = hidden; this.outputs = outputs;
        this.activation = Objects.requireNonNull(activation, "Escolha a função de transferência.");
        inputHidden = new double[hidden][inputs + 1];
        hiddenOutput = new double[outputs][hidden + 1];
        Random random = new Random(seed);
        initialize(inputHidden, random); initialize(hiddenOutput, random);
    }

    private static void initialize(double[][] weights, Random random) {
        for (double[] row : weights) for (int i = 0; i < row.length; i++) row[i] = random.nextDouble() * 0.2 - 0.1;
    }

    public double[] predict(double[] input) {
        checkInput(input);
        return forward(forward(input, inputHidden), hiddenOutput);
    }

    private double[] forward(double[] input, double[][] weights) {
        double[] result = new double[weights.length];
        for (int destination = 0; destination < weights.length; destination++) {
            double net = weights[destination][input.length];
            for (int origin = 0; origin < input.length; origin++) net += weights[destination][origin] * input[origin];
            if (!Double.isFinite(net)) throw new ArithmeticException("O treino divergiu numericamente. Reduza a taxa de aprendizagem e reinicie.");
            result[destination] = activation.apply(net);
        }
        return result;
    }

    /** Retorna a média de E = 1/2 * soma((desejado - saída)^2), antes de cada atualização online. */
    public double trainEpoch(List<EncodedSample> samples, double learningRate, Random random) {
        if (samples.isEmpty()) throw new IllegalArgumentException("O treino está vazio.");
        if (!Double.isFinite(learningRate) || learningRate <= 0 || learningRate > 1) {
            throw new IllegalArgumentException("A taxa de aprendizagem deve satisfazer 0 < η ≤ 1.");
        }
        List<EncodedSample> shuffled = new ArrayList<>(samples);
        Collections.shuffle(shuffled, Objects.requireNonNull(random));
        double totalError = 0;
        for (EncodedSample sample : shuffled) {
            double[] input = sample.input();
            double[] desired = sample.target();
            checkInput(input);
            if (desired.length != outputs) throw new IllegalArgumentException("Quantidade incorreta de saídas desejadas.");
            double[] hiddenValues = forward(input, inputHidden);
            double[] outputValues = forward(hiddenValues, hiddenOutput);
            double[] outputDelta = new double[outputs];
            double[] hiddenDelta = new double[hidden];
            for (int g = 0; g < outputs; g++) {
                double error = desired[g] - outputValues[g];
                totalError += 0.5 * error * error;
                outputDelta[g] = error * activation.derivative(outputValues[g]);
            }
            // Usa os pesos da propagação original, antes de atualizar a camada de saída.
            for (int c = 0; c < hidden; c++) {
                double propagated = 0;
                for (int g = 0; g < outputs; g++) propagated += outputDelta[g] * hiddenOutput[g][c];
                hiddenDelta[c] = propagated * activation.derivative(hiddenValues[c]);
            }
            update(hiddenOutput, hiddenValues, outputDelta, learningRate);
            update(inputHidden, input, hiddenDelta, learningRate);
        }
        double mean = totalError / samples.size();
        if (!Double.isFinite(mean)) throw new ArithmeticException("O erro deixou de ser finito. Reduza a taxa de aprendizagem e reinicie.");
        return mean;
    }

    private static void update(double[][] weights, double[] source, double[] delta, double rate) {
        for (int destination = 0; destination < weights.length; destination++) {
            for (int origin = 0; origin < source.length; origin++) {
                weights[destination][origin] += rate * delta[destination] * source[origin];
                if (!Double.isFinite(weights[destination][origin])) throw new ArithmeticException("Pesos divergiram. Reduza η e reinicie.");
            }
            weights[destination][source.length] += rate * delta[destination];
            if (!Double.isFinite(weights[destination][source.length])) throw new ArithmeticException("Bias divergiu. Reduza η e reinicie.");
        }
    }

    private void checkInput(double[] input) {
        if (input.length != inputs) throw new IllegalArgumentException("Esperados " + inputs + " atributos, recebidos " + input.length + ".");
        for (double value : input) if (!Double.isFinite(value)) throw new IllegalArgumentException("As entradas devem ser finitas.");
    }
    public int inputs() { return inputs; }
    public int hidden() { return hidden; }
    public int outputs() { return outputs; }
    public Activation activation() { return activation; }
    public double[][] weightsInputHidden() { return copy(inputHidden); }
    public double[][] weightsHiddenOutput() { return copy(hiddenOutput); }
    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) result[i] = matrix[i].clone();
        return result;
    }
}
