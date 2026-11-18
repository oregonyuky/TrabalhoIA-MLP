package br.edu.mlp.core;

public final class DetectorPlato {
    private final double[] janela;
    private final double limite;
    private int quantidade; private int proxima;

    public DetectorPlato(int tamanhoJanela, double limite) {
        if (tamanhoJanela < 10) throw new IllegalArgumentException("A janela do platô deve conter ao menos 10 épocas.");
        if (!Double.isFinite(limite) || limite < 0) throw new IllegalArgumentException("O limiar de platô deve ser finito e não negativo.");
        janela = new double[tamanhoJanela]; this.limite = limite;
    }

    public boolean adicionar(double erro) {
        if (!Double.isFinite(erro) || erro < 0) throw new IllegalArgumentException("O erro deve ser finito e não negativo.");
        janela[proxima] = erro; proxima = (proxima + 1) % janela.length;
        quantidade = Math.min(quantidade + 1, janela.length);
        return quantidade == janela.length && desvioPadrao() <= limite;
    }

    public double desvioPadrao() {
        if (quantidade == 0) return Double.NaN;
        double media = 0, somaQuadrados = 0;
        for (int indice = 0; indice < quantidade; indice++) {
            double diferenca = janela[indice] - media;
            media += diferenca / (indice + 1);
            somaQuadrados += diferenca * (janela[indice] - media);
        }
        return Math.sqrt(Math.max(0, somaQuadrados / quantidade));
    }
    public void reiniciar() { quantidade = 0; proxima = 0; }
}
