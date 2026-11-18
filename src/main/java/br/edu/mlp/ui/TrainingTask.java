package br.edu.mlp.ui;

import br.edu.mlp.core.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import java.util.*;
import java.util.function.Consumer;

/** A rede é alterada apenas nesta thread; a interface recebe lotes imutáveis. */
public final class TrainingTask extends Task<TrainingTask.Result> {
    public record Epoch(int number, double error, double rate) { }
    public record Update(List<Epoch> epochs, double seconds, double[][] inputHidden, double[][] hiddenOutput) { }
    public record Plateau(int epoch, double deviation) { }
    public record Result(Rede rede, List<Epoch> history, String reason, double seconds) { }

    private final Rede rede;
    private final DadosPreparados dados;
    private final ConfiguracaoTreino configuracao;
    private final Consumer<Update> aoAtualizar;
    private final Consumer<Plateau> aoDetectarPlato;
    private final Object controle = new Object();
    private volatile boolean pausado;
    private volatile boolean interrompido;
    private volatile double taxa;
    private volatile boolean reiniciarPlato;

    public TrainingTask(Rede rede, DadosPreparados dados, ConfiguracaoTreino configuracao,
                        Consumer<Update> aoAtualizar, Consumer<Plateau> aoDetectarPlato) {
        this.rede = rede;
        this.dados = dados;
        this.configuracao = configuracao;
        this.taxa = configuracao.taxaAprendizagem();
        this.aoAtualizar = aoAtualizar;
        this.aoDetectarPlato = aoDetectarPlato;
    }

    public void pauseTraining() { synchronized (controle) { pausado = true; } }
    public boolean isPaused() { return pausado; }

    public void resumeTraining(boolean reduzirTaxa) {
        synchronized (controle) {
            if (reduzirTaxa) taxa = Math.max(Double.MIN_NORMAL, taxa * 0.9);
            reiniciarPlato = true;
            pausado = false;
            controle.notifyAll();
        }
    }

    public void stopTraining() {
        synchronized (controle) { interrompido = true; pausado = false; controle.notifyAll(); }
    }

    public double currentRate() { return taxa; }

    @Override protected Result call() throws Exception {
        long inicio = System.nanoTime(), ultimaPublicacao = inicio;
        var historico = new ArrayList<Epoch>();
        var pendentes = new ArrayList<Epoch>();
        var aleatorio = new Random(configuracao.semente() ^ 0x5DEECE66DL);
        var detector = new DetectorPlato(configuracao.tamanhoJanelaPlatô(), configuracao.limitePlatô());
        String motivo = "Limite de épocas atingido";

        for (int epoca = 1; epoca <= configuracao.maximoEpocas(); epoca++) {
            synchronized (controle) {
                if (pausado && !pendentes.isEmpty()) publicar(pendentes, inicio);
                while (pausado && !interrompido) controle.wait();
            }
            if (interrompido) { motivo = "Interrompido pelo usuário"; break; }
            if (reiniciarPlato) { detector.reiniciar(); reiniciarPlato = false; }

            double taxaEpoca = taxa;
            double erro = rede.treinarEpoca(dados.treino(), taxaEpoca, aleatorio);
            if (!Double.isFinite(erro)) throw new IllegalStateException("O erro deixou de ser finito. Reduza a taxa de aprendizagem e reinicie.");

            Epoch ponto = new Epoch(epoca, erro, taxaEpoca);
            historico.add(ponto);
            pendentes.add(ponto);

            boolean atingiuErro = erro <= configuracao.erroMinimo();
            boolean plato = detector.adicionar(erro) && !atingiuErro && epoca < configuracao.maximoEpocas();
            long agora = System.nanoTime();
            if (agora - ultimaPublicacao > 60_000_000L || atingiuErro || plato || epoca == configuracao.maximoEpocas()) {
                publicar(pendentes, inicio);
                ultimaPublicacao = agora;
            }

            if (atingiuErro) { motivo = "Erro mínimo atingido"; break; }
            if (plato && !interrompido) {
                synchronized (controle) { if (!interrompido) pausado = true; }
                Plateau evento = new Plateau(epoca, detector.desvioPadrao());
                Platform.runLater(() -> aoDetectarPlato.accept(evento));
            }
        }

        if (!pendentes.isEmpty()) publicar(pendentes, inicio);
        if (interrompido) motivo = "Interrompido pelo usuário";
        return new Result(rede, List.copyOf(historico), motivo, segundos(inicio));
    }

    private void publicar(List<Epoch> pendentes, long inicio) {
        Update atualizacao = new Update(List.copyOf(pendentes), segundos(inicio),
                rede.pesosEntradaOculta(), rede.pesosOcultaSaida());
        pendentes.clear();
        Platform.runLater(() -> aoAtualizar.accept(atualizacao));
    }

    private static double segundos(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000_000.0;
    }
}
