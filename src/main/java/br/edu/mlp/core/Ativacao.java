package br.edu.mlp.core;

/** Funções de ativação usadas pela rede neural. */
public enum Ativacao {
    LINEAR("Linear (resultado líquido / 10)") {
        @Override public double aplicar(double liquido) { return liquido / 10.0; }
        @Override public double derivada(double saida) { return 0.1; }
    },
    LOGISTICA("Logística") {
        @Override public double aplicar(double liquido) {
            if (liquido >= 0) return 1.0 / (1.0 + Math.exp(-liquido));
            double exponencial = Math.exp(liquido);
            return exponencial / (1.0 + exponencial);
        }
        @Override public double derivada(double saida) { return saida * (1.0 - saida); }
    },
    TANH("Tangente hiperbólica") {
        @Override public double aplicar(double liquido) { return Math.tanh(liquido); }
        @Override public double derivada(double saida) { return 1.0 - saida * saida; }
    };

    private final String rotulo;
    Ativacao(String rotulo) { this.rotulo = rotulo; }
    public abstract double aplicar(double liquido);
    public abstract double derivada(double saida);
    @Override public String toString() { return rotulo; }
}
