package br.edu.mlp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class Rede {
    private final int entradas, ocultos, saidas;
    private final Ativacao ativacao;
    private final double[][] entradaOculta, ocultaSaida;

    public Rede(int entradas, int ocultos, int saidas, Ativacao ativacao, long semente) {
        if (entradas <= 0 || ocultos <= 0 || saidas <= 0) throw new IllegalArgumentException("Todas as camadas devem ter ao menos um neurônio.");
        this.entradas = entradas; this.ocultos = ocultos; this.saidas = saidas;
        this.ativacao = Objects.requireNonNull(ativacao, "Escolha a função de transferência.");
        entradaOculta = new double[ocultos][entradas + 1];
        ocultaSaida = new double[saidas][ocultos + 1];
        Random aleatorio = new Random(semente);
        inicializar(entradaOculta, aleatorio); inicializar(ocultaSaida, aleatorio);
    }

    private static void inicializar(double[][] pesos, Random aleatorio) {
        for (double[] linha : pesos) for (int indice = 0; indice < linha.length; indice++) linha[indice] = aleatorio.nextDouble() * 0.2 - 0.1;
    }

    public double[] prever(double[] entrada) { verificarEntrada(entrada); return propagar(propagar(entrada, entradaOculta), ocultaSaida); }

    private double[] propagar(double[] entrada, double[][] pesos) {
        double[] resultado = new double[pesos.length];
        for (int destino = 0; destino < pesos.length; destino++) {
            double liquido = pesos[destino][entrada.length];
            for (int origem = 0; origem < entrada.length; origem++) liquido += pesos[destino][origem] * entrada[origem];
            if (!Double.isFinite(liquido)) throw new ArithmeticException("O treino divergiu numericamente. Reduza a taxa de aprendizagem e reinicie.");
            resultado[destino] = ativacao.aplicar(liquido);
        }
        return resultado;
    }

    public double treinarEpoca(List<AmostraCodificada> amostras, double taxa, Random aleatorio) {
        if (amostras.isEmpty()) throw new IllegalArgumentException("O treino está vazio.");
        if (!Double.isFinite(taxa) || taxa <= 0 || taxa > 1) throw new IllegalArgumentException("A taxa de aprendizagem deve satisfazer 0 < η ≤ 1.");
        List<AmostraCodificada> embaralhadas = new ArrayList<>(amostras); Collections.shuffle(embaralhadas, Objects.requireNonNull(aleatorio));
        double erroTotal = 0;
        for (AmostraCodificada amostra : embaralhadas) {
            double[] entrada = amostra.entrada(); double[] desejada = amostra.saidaDesejada(); verificarEntrada(entrada);
            if (desejada.length != saidas) throw new IllegalArgumentException("Quantidade incorreta de saídas desejadas.");
            double[] valoresOcultos = propagar(entrada, entradaOculta); double[] valoresSaida = propagar(valoresOcultos, ocultaSaida);
            double[] deltaSaida = new double[saidas]; double[] deltaOculto = new double[ocultos];
            for (int indice = 0; indice < saidas; indice++) {
                double erro = desejada[indice] - valoresSaida[indice]; erroTotal += 0.5 * erro * erro;
                deltaSaida[indice] = erro * ativacao.derivada(valoresSaida[indice]);
            }
            for (int oculto = 0; oculto < ocultos; oculto++) {
                double propagado = 0;
                for (int saida = 0; saida < saidas; saida++) propagado += deltaSaida[saida] * ocultaSaida[saida][oculto];
                deltaOculto[oculto] = propagado * ativacao.derivada(valoresOcultos[oculto]);
            }
            atualizar(ocultaSaida, valoresOcultos, deltaSaida, taxa); atualizar(entradaOculta, entrada, deltaOculto, taxa);
        }
        double medio = erroTotal / amostras.size();
        if (!Double.isFinite(medio)) throw new ArithmeticException("O erro deixou de ser finito. Reduza a taxa de aprendizagem e reinicie.");
        return medio;
    }

    private static void atualizar(double[][] pesos, double[] origem, double[] delta, double taxa) {
        for (int destino = 0; destino < pesos.length; destino++) {
            for (int indice = 0; indice < origem.length; indice++) {
                pesos[destino][indice] += taxa * delta[destino] * origem[indice];
                if (!Double.isFinite(pesos[destino][indice])) throw new ArithmeticException("Pesos divergiram. Reduza η e reinicie.");
            }
            pesos[destino][origem.length] += taxa * delta[destino];
            if (!Double.isFinite(pesos[destino][origem.length])) throw new ArithmeticException("Bias divergiu. Reduza η e reinicie.");
        }
    }

    private void verificarEntrada(double[] entrada) {
        if (entrada.length != entradas) throw new IllegalArgumentException("Esperados " + entradas + " atributos, recebidos " + entrada.length + ".");
        for (double valor : entrada) if (!Double.isFinite(valor)) throw new IllegalArgumentException("As entradas devem ser finitas.");
    }

    public int quantidadeEntradas() { return entradas; }
    public int quantidadeOcultas() { return ocultos; }
    public int quantidadeSaidas() { return saidas; }
    public Ativacao ativacao() { return ativacao; }
    public double[][] pesosEntradaOculta() { return copiar(entradaOculta); }
    public double[][] pesosOcultaSaida() { return copiar(ocultaSaida); }
    private static double[][] copiar(double[][] matriz) { double[][] copia = new double[matriz.length][]; for (int indice = 0; indice < matriz.length; indice++) copia[indice] = matriz[indice].clone(); return copia; }
}
