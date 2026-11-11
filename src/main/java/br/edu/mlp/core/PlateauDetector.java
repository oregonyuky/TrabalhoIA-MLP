package br.edu.mlp.core;

/** Desvio padrão populacional da janela completa mais recente, sem bibliotecas de IA. */
public final class PlateauDetector {
    private final double[] window;
    private final double threshold;
    private int count;
    private int next;

    public PlateauDetector(int windowSize, double threshold) {
        if (windowSize < 10) throw new IllegalArgumentException("A janela do platô deve conter ao menos 10 épocas.");
        if (!Double.isFinite(threshold) || threshold < 0) throw new IllegalArgumentException("O limiar de platô deve ser finito e não negativo.");
        window = new double[windowSize]; this.threshold = threshold;
    }
    public boolean add(double error) {
        if (!Double.isFinite(error) || error < 0) throw new IllegalArgumentException("O erro deve ser finito e não negativo.");
        window[next] = error; next = (next + 1) % window.length;
        count = Math.min(count + 1, window.length);
        return count == window.length && standardDeviation() <= threshold;
    }
    public double standardDeviation() {
        if (count == 0) return Double.NaN;
        double mean = 0, sumSquares = 0;
        for (int i = 0; i < count; i++) {
            double difference = window[i] - mean;
            mean += difference / (i + 1);
            sumSquares += difference * (window[i] - mean);
        }
        return Math.sqrt(Math.max(0, sumSquares / count));
    }
    /** Inicia nova janela após a decisão do usuário, para oferecer nova decisão se persistir. */
    public void reset() { count = 0; next = 0; }
}
