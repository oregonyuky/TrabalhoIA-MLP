package br.edu.mlp;

import br.edu.mlp.ui.MlpApplication;
import javafx.application.Application;

/** Ponto de entrada separado de Application para execu??o por Maven/IDE. */
public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) { Application.launch(MlpApplication.class, args); }
}
