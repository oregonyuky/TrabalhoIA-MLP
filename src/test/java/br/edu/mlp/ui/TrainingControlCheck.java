package br.edu.mlp.ui;

import br.edu.mlp.core.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Verificação opcional com toolkit gráfico; não integra os testes headless. */
public final class TrainingControlCheck {
    public static void main(String[] args) throws Exception {
        var started=new CountDownLatch(1); Platform.startup(started::countDown);
        if(!started.await(10,TimeUnit.SECONDS)) throw new AssertionError("Toolkit não iniciou.");
        var done=new CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();
        var ref=new AtomicReference<TrainingTask>();
        var updates=new ArrayList<TrainingTask.Epoch>();
        var occurrences=new AtomicInteger();
        try {
            PreparedData data=DataPreparation.loadSeparate(Path.of("entradas/base_treinamento.csv"),Path.of("entradas/base_teste.csv"));
            var config=new TrainingConfig(6,Activation.LOGISTIC,0.3,2000,0,42,10,1);
            var network=new Network(6,6,5,Activation.LOGISTIC,42);
            Platform.runLater(() -> {
                TrainingTask task=new TrainingTask(network,data,config,u -> updates.addAll(u.epochs()),event -> {
                    try {
                        TrainingTask current=ref.get();
                        require(current.isPaused(),"Platô precisa pausar antes da decisão.");
                        int count=occurrences.incrementAndGet();
                        require(event.epoch()==count*10,"Épocas preservadas após retomada.");
                        if(count==1) {
                            PauseTransition delay=new PauseTransition(Duration.millis(120));
                            delay.setOnFinished(e -> {
                                try {
                                    require(updates.getLast().number()==10,"Treino avançou enquanto estava pausado.");
                                    require(current.currentRate()==0.3,"Retomada alterou taxa sem autorização.");
                                    current.resumeTraining(false);
                                } catch(Throwable error) { failure.set(error); current.stopTraining(); }
                            }); delay.play();
                        } else if(count==2) {
                            current.resumeTraining(true);
                            require(Math.abs(current.currentRate()-0.27)<1e-12,"Redução precisa ser de 10%.");
                        } else current.stopTraining();
                    } catch(Throwable error) { failure.set(error); ref.get().stopTraining(); }
                });
                ref.set(task);
                task.setOnSucceeded(e -> {
                    try {
                        var result=task.getValue();
                        require(result.reason().equals("Interrompido pelo usuário"),"Motivo de parada incorreto.");
                        require(occurrences.get()==3 && result.history().size()==30,"Platô não se repetiu ou parada avançou épocas.");
                        require(updates.size()==30,"Histórico perdido ou duplicado na publicação.");
                        for(var point:result.history()) require(Math.abs(point.rate()-(point.number()<=20 ? 0.3:0.27))<1e-12,"Taxa histórica incorreta.");
                    } catch(Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                });
                task.setOnFailed(e -> { failure.set(task.getException()); done.countDown(); });
                task.pauseTraining();
                Thread worker=new Thread(task,"controle-check"); worker.setDaemon(true); worker.start();
                PauseTransition initialPause=new PauseTransition(Duration.millis(120));
                initialPause.setOnFinished(e -> {
                    try {
                        require(updates.isEmpty(),"A pausa inicial não foi respeitada.");
                        task.resumeTraining(false);
                    } catch(Throwable error) { failure.set(error); task.stopTraining(); }
                }); initialPause.play();
            });
            if(!done.await(20,TimeUnit.SECONDS)) throw new AssertionError("Controle de treinamento excedeu o tempo.");
            if(failure.get()!=null) throw new AssertionError("Falha no controle de treinamento",failure.get());
            System.out.println("OK: pausa, retomada sem alterar taxa, redução de 10%, platô persistente e interrupção.");
        } finally {
            if(ref.get()!=null) ref.get().stopTraining();
            Platform.exit();
        }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}

