package br.edu.mlp.core;

/** Funções e derivadas especificadas no enunciado, usadas nas duas camadas. */
public enum Activation {
    LINEAR("Linear (net / 10)") {
        public double apply(double net) { return net / 10.0; }
        public double derivative(double output) { return 0.1; }
    },
    LOGISTIC("Logística") {
        public double apply(double net) {
            // Esta forma evita overflow em exp(-net) para net muito negativo.
            if (net >= 0) return 1.0 / (1.0 + Math.exp(-net));
            double exponential = Math.exp(net);
            return exponential / (1.0 + exponential);
        }
        public double derivative(double output) { return output * (1.0 - output); }
    },
    TANH("Tangente hiperbólica") {
        public double apply(double net) { return Math.tanh(net); }
        public double derivative(double output) { return 1.0 - output * output; }
    };

    private final String label;
    Activation(String label) { this.label = label; }
    public abstract double apply(double net);
    /** A derivada recebe i = f(net), conforme a tabela do PDF. */
    public abstract double derivative(double output);
    @Override public String toString() { return label; }
}
