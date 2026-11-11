package br.edu.mlp.cli;

import javafx.application.Application;

/** Permite executar o exportador por Maven exec:java. */
public final class ChartsLauncher {
    private ChartsLauncher() { }
    public static void main(String[] args) {
        Application.launch(ExperimentCharts.class,args);
        if(ExperimentCharts.failure!=null) throw new IllegalStateException("Falha na exportação dos gráficos",ExperimentCharts.failure);
    }
}

