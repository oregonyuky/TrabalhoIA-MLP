package br.edu.mlp.cli;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exporta os históricos reais com JavaFX Charts, sem treinar novamente. */
public final class ExperimentCharts extends Application {
    static Throwable failure;
    @Override public void start(Stage unused) {
        try {
            Path root=getParameters().getRaw().isEmpty() ? Path.of("."):Path.of(getParameters().getRaw().getFirst());
            for(String id:List.of("logistica-6-eta03","logistica-12-eta01","tanh-6-eta005")) {
                Path directory=root.resolve("experimentos").resolve(id);
                List<String> lines=Files.readAllLines(directory.resolve("historico.csv"),StandardCharsets.UTF_8);
                int last=lines.size()-1;
                NumberAxis x=new NumberAxis(0,last+1,Math.max(1,Math.ceil(last/10.0)));
                NumberAxis y=new NumberAxis(); y.setForceZeroInRange(true);
                x.setLabel("Época"); y.setLabel("Erro médio · ½ Σ(desejado − saída)²");
                LineChart<Number,Number> chart=new LineChart<>(x,y);
                chart.setTitle(id); chart.setAnimated(false); chart.setCreateSymbols(false);
                XYChart.Series<Number,Number> error=new XYChart.Series<>(); error.setName("Erro do treino");
                for(int i=1;i<lines.size();i++) {
                    String[] cells=lines.get(i).split(",");
                    error.getData().add(new XYChart.Data<>(Integer.parseInt(cells[0]),Double.parseDouble(cells[1])));
                }
                XYChart.Series<Number,Number> threshold=new XYChart.Series<>(); threshold.setName("Limiar: 0,005");
                threshold.getData().addAll(List.of(new XYChart.Data<>(1,0.005),new XYChart.Data<>(last,0.005)));
                chart.getData().addAll(List.of(error,threshold));
                Scene scene=new Scene(chart,900,420);
                var css=getClass().getResource("/br/edu/mlp/ui/app.css");
                if(css!=null) scene.getStylesheets().add(css.toExternalForm());
                chart.applyCss(); chart.layout();
                ImageIO.write(SwingFXUtils.fromFXImage(chart.snapshot(null,null),null),"png",directory.resolve("curva.png").toFile());
                System.out.println("Curva JavaFX: "+directory.resolve("curva.png"));
            }
        } catch(Throwable error) { failure=error; error.printStackTrace(); }
        finally { Platform.exit(); }
    }
}

