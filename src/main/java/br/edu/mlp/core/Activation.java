package br.edu.mlp.core;

/** Funções de ativação e suas derivadas usadas nas camadas da rede. */
public enum Activation {
    LINEAR("Linear (net / 10)") {
        @Override
        public double apply(double net) {
            return net / 10.0;
        }

        @Override
        public double derivative(double output) {
            return 0.1;
        }
    },
    LOGISTIC("Logística") {
        @Override
        public double apply(double net) {
            // Evita overflow ao calcular exp(-net) com valores muito negativos.
            if (net >= 0) {
                return 1.0 / (1.0 + Math.exp(-net));
            }

            double exponential = Math.exp(net);
            return exponential / (1.0 + exponential);
        }

        @Override
        public double derivative(double output) {
            return output * (1.0 - output);
        }
    },
    TANH("Tangente hiperbólica") {
        @Override
        public double apply(double net) {
            return Math.tanh(net);
        }

        @Override
        public double derivative(double output) {
            return 1.0 - output * output;
        }
    };

    private final String label;

    Activation(String label) {
        this.label = label;
    }

    /** Calcula a saída da função a partir do potencial líquido. */
    public abstract double apply(double net);

    /** Calcula a derivada usando i = f(net), conforme a implementação da rede. */
    public abstract double derivative(double output);

    /** Nome amigável em português da função de ativação. */
    public double aplicar(double potencialLiquido) {
        return apply(potencialLiquido);
    }

    /** Derivada em português que reutiliza a implementação existente. */
    public double derivada(double saida) {
        return derivative(saida);
    }

    @Override
    public String toString() {
        return label;
    }
}
