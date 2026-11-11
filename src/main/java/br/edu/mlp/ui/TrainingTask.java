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
    public record Result(Network network, List<Epoch> history, String reason, double seconds) { }
    private final Network network;
    private final PreparedData data;
    private final TrainingConfig config;
    private final Consumer<Update> onUpdate;
    private final Consumer<Plateau> onPlateau;
    private final Object control = new Object();
    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile double rate;
    private volatile boolean resetPlateau;

    public TrainingTask(Network network, PreparedData data, TrainingConfig config,
                        Consumer<Update> onUpdate, Consumer<Plateau> onPlateau) {
        this.network = network;
        this.data = data;
        this.config = config;
        this.rate = config.learningRate();
        this.onUpdate = onUpdate;
        this.onPlateau = onPlateau;
    }
    public void pauseTraining() { synchronized (control) { paused = true; } }
    public boolean isPaused() { return paused; }
    public void resumeTraining(boolean reduceRate) {
        synchronized (control) {
            if (reduceRate) rate = Math.max(Double.MIN_NORMAL, rate * 0.9);
            resetPlateau = true;
            paused = false;
            control.notifyAll();
        }
    }
    public void stopTraining() {
        synchronized (control) { stopped = true; paused = false; control.notifyAll(); }
    }
    public double currentRate() { return rate; }

    @Override protected Result call() throws Exception {
        long start = System.nanoTime(), lastPublish = start;
        var history = new ArrayList<Epoch>();
        var pending = new ArrayList<Epoch>();
        var random = new Random(config.seed() ^ 0x5DEECE66DL);
        var detector = new PlateauDetector(config.plateauWindow(), config.plateauThreshold());
        String reason = "Limite de épocas atingido";
        for (int epoch = 1; epoch <= config.maxEpochs(); epoch++) {
            synchronized (control) {
                if (paused && !pending.isEmpty()) publish(pending, start);
                while (paused && !stopped) control.wait();
            }
            if (stopped) { reason = "Interrompido pelo usuário"; break; }
            if (resetPlateau) { detector.reset(); resetPlateau = false; }
            double epochRate = rate;
            double error = network.trainEpoch(data.training(), epochRate, random);
            if (!Double.isFinite(error)) throw new IllegalStateException("O erro deixou de ser finito. Reduza a taxa de aprendizagem e reinicie.");
            Epoch point = new Epoch(epoch, error, epochRate);
            history.add(point); pending.add(point);
            boolean reached = error <= config.minError();
            boolean plateau = detector.add(error) && !reached && epoch < config.maxEpochs();
            long now = System.nanoTime();
            if (now - lastPublish > 60_000_000L || reached || plateau || epoch == config.maxEpochs()) {
                publish(pending, start); lastPublish = now;
            }
            if (reached) { reason = "Erro mínimo atingido"; break; }
            if (plateau && !stopped) {
                synchronized (control) { if (!stopped) paused = true; }
                Plateau event = new Plateau(epoch, detector.standardDeviation());
                Platform.runLater(() -> onPlateau.accept(event));
            }
        }
        if (!pending.isEmpty()) publish(pending, start);
        if (stopped) reason = "Interrompido pelo usuário";
        return new Result(network, List.copyOf(history), reason, seconds(start));
    }
    private void publish(List<Epoch> pending, long start) {
        Update update = new Update(List.copyOf(pending), seconds(start), network.weightsInputHidden(), network.weightsHiddenOutput());
        pending.clear();
        Platform.runLater(() -> onUpdate.accept(update));
    }
    private static double seconds(long start) { return (System.nanoTime() - start) / 1_000_000_000.0; }
}
