package br.edu.mlp.core;

import java.util.List;

public final class Avaliacao {
    private final int[][] matriz; private final List<String> classes; private final int total, acertos;
    private Avaliacao(int[][] matriz, List<String> classes, int total, int acertos) { this.matriz = matriz; this.classes = List.copyOf(classes); this.total = total; this.acertos = acertos; }

    public static Avaliacao avaliar(Rede rede, List<AmostraCodificada> amostras, List<String> classes) {
        if (amostras.isEmpty()) throw new IllegalArgumentException("A base de teste não pode estar vazia.");
        if (rede.quantidadeSaidas() != classes.size()) throw new IllegalArgumentException("As classes não correspondem às saídas da rede.");
        int[][] matriz = new int[classes.size()][classes.size()]; int acertos = 0;
        for (AmostraCodificada amostra : amostras) {
            if (amostra.indiceClasse() >= classes.size()) throw new IllegalArgumentException("Classe de teste inválida.");
            int prevista = indiceMaiorValor(rede.prever(amostra.entrada())); matriz[amostra.indiceClasse()][prevista]++;
            if (prevista == amostra.indiceClasse()) acertos++;
        }
        return new Avaliacao(matriz, classes, amostras.size(), acertos);
    }

    public static int indiceMaiorValor(double[] saidas) {
        if (saidas.length == 0) throw new IllegalArgumentException("A saída não pode estar vazia.");
        int melhor = 0;
        for (int indice = 0; indice < saidas.length; indice++) {
            if (!Double.isFinite(saidas[indice])) throw new ArithmeticException("Saída não finita na avaliação.");
            if (saidas[indice] > saidas[melhor]) melhor = indice;
        }
        return melhor;
    }

    public int[][] matrizConfusao() { int[][] copia = new int[matriz.length][]; for (int linha = 0; linha < matriz.length; linha++) copia[linha] = matriz[linha].clone(); return copia; }
    public List<String> classes() { return classes; } public int total() { return total; } public int acertos() { return acertos; }
    public double acuracia() { return (double) acertos / total; }
    public double precisao(int indiceClasse) { int previstas = 0; for (int[] linha : matriz) previstas += linha[indiceClasse]; return previstas == 0 ? 0.0 : (double) matriz[indiceClasse][indiceClasse] / previstas; }
    public double cobertura(int indiceClasse) { int reais = 0; for (int quantidade : matriz[indiceClasse]) reais += quantidade; return reais == 0 ? 0.0 : (double) matriz[indiceClasse][indiceClasse] / reais; }
}
