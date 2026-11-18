package br.edu.mlp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Rede MLP com uma camada oculta, bias e treinamento padrão a padrão. */
public final class Rede {
    private final int quantidadeEntradas;
    private final int quantidadeOcultas;
    private final int quantidadeSaidas;
    private final Ativacao ativacao;
    private final double[][] pesosEntradaOculta;
    private final double[][] pesosOcultaSaida;

    public Rede(int quantidadeEntradas, int quantidadeOcultas, int quantidadeSaidas,
                Ativacao ativacao, long semente) {
        if (quantidadeEntradas <= 0 || quantidadeOcultas <= 0 || quantidadeSaidas <= 0) {
            throw new IllegalArgumentException("Todas as camadas devem ter ao menos um neurônio.");
        }
        this.quantidadeEntradas = quantidadeEntradas;
        this.quantidadeOcultas = quantidadeOcultas;
        this.quantidadeSaidas = quantidadeSaidas;
        this.ativacao = Objects.requireNonNull(ativacao, "Escolha a função de transferência.");
        pesosEntradaOculta = new double[quantidadeOcultas][quantidadeEntradas + 1];
        pesosOcultaSaida = new double[quantidadeSaidas][quantidadeOcultas + 1];
        Random aleatorio = new Random(semente);
        inicializar(pesosEntradaOculta, aleatorio);
        inicializar(pesosOcultaSaida, aleatorio);
    }

    private static void inicializar(double[][] pesos, Random aleatorio) {
        for (double[] linha : pesos) {
            for (int indice = 0; indice < linha.length; indice++) {
                linha[indice] = aleatorio.nextDouble() * 0.2 - 0.1;
            }
        }
    }

    public double[] prever(double[] entrada) {
        verificarEntrada(entrada);
        double[] saidasOcultas = propagar(entrada, pesosEntradaOculta);
        return propagar(saidasOcultas, pesosOcultaSaida);
    }

    private double[] propagar(double[] entrada, double[][] pesos) {
        double[] resultado = new double[pesos.length];
        for (int destino = 0; destino < pesos.length; destino++) {
            double liquido = pesos[destino][entrada.length];
            for (int origem = 0; origem < entrada.length; origem++) {
                liquido += pesos[destino][origem] * entrada[origem];
            }
            if (!Double.isFinite(liquido)) {
                throw new ArithmeticException("O treino divergiu numericamente. Reduza a taxa de aprendizagem e reinicie.");
            }
            resultado[destino] = ativacao.aplicar(liquido);
        }
        return resultado;
    }

    public double treinarEpoca(List<AmostraCodificada> amostras, double taxaAprendizagem, Random aleatorio) {
        if (amostras.isEmpty()) throw new IllegalArgumentException("O treino está vazio.");
        if (!Double.isFinite(taxaAprendizagem) || taxaAprendizagem <= 0 || taxaAprendizagem > 1) {
            throw new IllegalArgumentException("A taxa de aprendizagem deve satisfazer 0 < η ≤ 1.");
        }

        List<AmostraCodificada> embaralhadas = new ArrayList<>(amostras);
        Collections.shuffle(embaralhadas, Objects.requireNonNull(aleatorio));
        double erroTotal = 0.0;

        for (AmostraCodificada amostra : embaralhadas) {
            double[] entrada = amostra.entrada();
            double[] desejada = amostra.saidaDesejada();
            verificarEntrada(entrada);
            if (desejada.length != quantidadeSaidas) throw new IllegalArgumentException("Quantidade incorreta de saídas desejadas.");

            double[] saidasOcultas = propagar(entrada, pesosEntradaOculta);
            double[] saidas = propagar(saidasOcultas, pesosOcultaSaida);
            double[] erroSaida = new double[quantidadeSaidas];
            double[] erroOculto = new double[quantidadeOcultas];

            for (int indice = 0; indice < quantidadeSaidas; indice++) {
                double erro = desejada[indice] - saidas[indice];
                erroTotal += 0.5 * erro * erro;
                erroSaida[indice] = erro * ativacao.derivada(saidas[indice]);
            }

            for (int oculto = 0; oculto < quantidadeOcultas; oculto++) {
                double propagado = 0.0;
                for (int saida = 0; saida < quantidadeSaidas; saida++) {
                    propagado += erroSaida[saida] * pesosOcultaSaida[saida][oculto];
                }
                erroOculto[oculto] = propagado * ativacao.derivada(saidasOcultas[oculto]);
            }

            atualizar(pesosOcultaSaida, saidasOcultas, erroSaida, taxaAprendizagem);
            atualizar(pesosEntradaOculta, entrada, erroOculto, taxaAprendizagem);
        }

        double erroMedio = erroTotal / amostras.size();
        if (!Double.isFinite(erroMedio)) throw new ArithmeticException("O erro deixou de ser finito. Reduza a taxa de aprendizagem e reinicie.");
        return erroMedio;
    }

    private static void atualizar(double[][] pesos, double[] origem, double[] erro, double taxa) {
        for (int destino = 0; destino < pesos.length; destino++) {
            for (int indiceOrigem = 0; indiceOrigem < origem.length; indiceOrigem++) {
                pesos[destino][indiceOrigem] += taxa * erro[destino] * origem[indiceOrigem];
                if (!Double.isFinite(pesos[destino][indiceOrigem])) throw new ArithmeticException("Pesos divergiram. Reduza η e reinicie.");
            }
            pesos[destino][origem.length] += taxa * erro[destino];
            if (!Double.isFinite(pesos[destino][origem.length])) throw new ArithmeticException("Bias divergiu. Reduza η e reinicie.");
        }
    }

    private void verificarEntrada(double[] entrada) {
        if (entrada.length != quantidadeEntradas) throw new IllegalArgumentException("Esperados " + quantidadeEntradas + " atributos, recebidos " + entrada.length + ".");
        for (double valor : entrada) if (!Double.isFinite(valor)) throw new IllegalArgumentException("As entradas devem ser finitas.");
    }

    public int quantidadeEntradas() { return quantidadeEntradas; }
    public int quantidadeOcultas() { return quantidadeOcultas; }
    public int quantidadeSaidas() { return quantidadeSaidas; }
    public Ativacao ativacao() { return ativacao; }
    public double[][] pesosEntradaOculta() { return copiar(pesosEntradaOculta); }
    public double[][] pesosOcultaSaida() { return copiar(pesosOcultaSaida); }

    private static double[][] copiar(double[][] matriz) {
        double[][] copia = new double[matriz.length][];
        for (int linha = 0; linha < matriz.length; linha++) copia[linha] = matriz[linha].clone();
        return copia;
    }
}
