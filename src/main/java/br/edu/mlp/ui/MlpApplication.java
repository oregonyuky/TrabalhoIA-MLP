package br.edu.mlp.ui;

import br.edu.mlp.core.*;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Interface em etapas; toda a matemática permanece no pacote core. */
public final class MlpApplication extends Application {
    private Stage stage;
    private TabPane tabs;
    private Tab dataTab, trainingTab, evaluationTab;
    private VBox dataContent, configuration;
    private final TextField trainingPath = new TextField("entradas/base_treinamento.csv");
    private final TextField testPath = new TextField("entradas/base_teste.csv");
    private final TextField fraction = new TextField("70");
    private final TextField seed = new TextField("42");
    private final RadioButton separate = new RadioButton("Dois arquivos: treino e teste");
    private final RadioButton split = new RadioButton("Um arquivo com divisão aleatória");
    private final Label dataSummary = new Label("Prepare os dados para configurar a rede.");
    private final Label topology = new Label("Entradas: —  •  Saídas: —");
    private final ComboBox<String> hiddenMode = new ComboBox<>();
    private final TextField hidden = new TextField("6");
    private final ComboBox<Activation> activation = new ComboBox<>();
    private final TextField learningRate = new TextField("0.3");
    private final TextField epochs = new TextField("2000");
    private final TextField minError = new TextField("0.005");
    private final TextField plateauWindow = new TextField("10");
    private final TextField plateauThreshold = new TextField("0.00001");
    private final Label currentEpoch = new Label("0");
    private final Label currentError = new Label("—");
    private final Label currentRate = new Label("—");
    private final Label elapsed = new Label("0,0 s");
    private final Label status = new Label("Aguardando dados");
    private final Button train = new Button("Iniciar treinamento");
    private final Button pause = new Button("Pausar");
    private final Button stop = new Button("Interromper");
    private final Button evaluate = new Button("Avaliar na base de teste");
    private final Button exportHistory = new Button("Exportar histórico CSV");
    private final Button exportCurve = new Button("Salvar curva PNG");
    private final Button exportMatrix = new Button("Exportar matriz CSV");
    private final TextArea log = new TextArea();
    private final TableView<List<String>> preview = new TableView<>();
    private final ComboBox<String> previewSet = new ComboBox<>();
    private final CheckBox normalized = new CheckBox("Exibir entradas normalizadas");
    private final NetworkView networkView = new NetworkView();
    private final XYChart.Series<Number,Number> errorSeries = new XYChart.Series<>();
    private final XYChart.Series<Number,Number> thresholdSeries = new XYChart.Series<>();
    private final XYChart.Series<Number,Number> plateauSeries = new XYChart.Series<>();
    private LineChart<Number,Number> chart;
    private final TableView<List<String>> confusion = new TableView<>();
    private final TableView<List<String>> classMetrics = new TableView<>();
    private final Label accuracy = new Label("—");
    private final Label evaluationSummary = new Label("Treine a rede e aplique-a aos exemplos de teste.");
    private BarChart<String,Number> metricsChart;
    private PreparedData data;
    private Network network;
    private TrainingConfig usedConfig;
    private TrainingTask activeTask;
    private Evaluation evaluation;
    private final List<TrainingTask.Epoch> history = new ArrayList<>();
    private int chartStride = 1;
    private long lastArchitectureRefresh;
    private String preparedSource = "Arquivos de treino e teste separados";
    private boolean busy;
    private boolean smoke;
    private boolean smokePlateau;
    private int smokePlateaus;

    @Override public void start(Stage stage) {
        this.stage = stage;
        smoke = getParameters().getRaw().contains("--smoke-test");
        smokePlateau = getParameters().getRaw().contains("--smoke-plateau");
        smoke |= smokePlateau;
        VBox heading = new VBox(5,label("Laboratório MLP","app-title"),
            label("Backpropagation • Dados, aprendizado e avaliação","subtitle"));
        heading.setPadding(new Insets(20,24,14,24));
        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        dataTab = new Tab("1  Dados",buildData());
        trainingTab = new Tab("2  Treinamento",buildTraining());
        evaluationTab = new Tab("3  Avaliação",buildEvaluation());
        tabs.getTabs().addAll(dataTab,trainingTab,evaluationTab);
        status.getStyleClass().add("status-label"); status.setPadding(new Insets(8,24,8,24));
        BorderPane root = new BorderPane(tabs); root.setTop(heading); root.setBottom(status);
        Scene scene = new Scene(root,1280,900);
        var css=getClass().getResource("app.css");
        if(css!=null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene); stage.setTitle("Laboratório MLP · Backpropagation");
        stage.setMinWidth(1000); stage.setMinHeight(720);
        stage.setOnCloseRequest(event -> { if(activeTask!=null) activeTask.stopTraining(); });
        stage.show(); refreshButtons(); updateHidden();
        if(smoke) Platform.runLater(() -> {
            try {
                data=DataPreparation.loadSeparate(Path.of(trainingPath.getText()),Path.of(testPath.getText()));
                displayPreparedData(); epochs.setText(smokePlateau ? "35":"250"); minError.setText("0");
                if(smokePlateau) plateauThreshold.setText("1");
                startTraining();
            } catch(Exception e) { smokeFailure(e); }
        });
    }

    private ScrollPane buildData() {
        ToggleGroup modes=new ToggleGroup(); separate.setToggleGroup(modes); split.setToggleGroup(modes); separate.setSelected(true);
        GridPane grid=formGrid();
        Button chooseTrain=new Button("Selecionar CSV…"), chooseTest=new Button("Selecionar CSV…");
        chooseTrain.setOnAction(e -> chooseCsv(trainingPath)); chooseTest.setOnAction(e -> chooseCsv(testPath));
        grid.addRow(0,new Label("Treino / arquivo único"),trainingPath,chooseTrain);
        grid.addRow(1,new Label("Teste"),testPath,chooseTest);
        grid.addRow(2,new Label("Percentual de treino (%)"),fraction);
        grid.addRow(3,new Label("Semente (divisão e treino)"),seed);
        trainingPath.setPrefColumnCount(42);
        testPath.disableProperty().bind(split.selectedProperty()); chooseTest.disableProperty().bind(split.selectedProperty());
        fraction.disableProperty().bind(separate.selectedProperty());
        Button prepare=new Button("Carregar e preparar dados"); prepare.getStyleClass().add("primary-button");
        prepare.setOnAction(e -> prepareData());
        Button next=new Button("Ir para treinamento →"); next.setOnAction(e -> tabs.getSelectionModel().select(trainingTab));
        Label explanation=new Label("CSV com cabeçalho: atributos numéricos e classe na última coluna. Mínimos e máximos são calculados somente no treino.\nA prévia inclui a saída desejada em one-hot; entradas de teste fora da faixa de treino são limitadas a [0, 1].");
        explanation.setWrapText(true);
        previewSet.getItems().addAll("Treino","Teste"); previewSet.setValue("Treino"); normalized.setSelected(true);
        previewSet.setOnAction(e -> updatePreview()); normalized.setOnAction(e -> updatePreview());
        preview.setPrefHeight(430); preview.setPlaceholder(new Label("Nenhuma base carregada"));
        dataSummary.setWrapText(true); dataSummary.getStyleClass().add("section-title");
        dataContent=new VBox(16,label("Preparação dos dados","section-title"),new HBox(24,separate,split),grid,
            new HBox(12,prepare,next),explanation,dataSummary,new HBox(16,previewSet,normalized),preview);
        dataContent.setPadding(new Insets(22));
        return scroll(dataContent);
    }
    private BorderPane buildTraining() {
        hiddenMode.getItems().addAll("Média aritmética","Média geométrica","Manual"); hiddenMode.setValue("Média aritmética");
        hiddenMode.setOnAction(e -> { updateHidden(); showArchitecture(); });
        hidden.setOnAction(e -> showArchitecture()); hidden.focusedProperty().addListener((o,a,b) -> { if(!b) showArchitecture(); });
        activation.setItems(FXCollections.observableArrayList(Activation.values())); activation.setValue(Activation.LOGISTIC);
        GridPane grid=formGrid();
        grid.addRow(0,new Label("Camada oculta"),hiddenMode);
        grid.addRow(1,new Label("Neurônios ocultos"),hidden);
        grid.addRow(2,new Label("Transferência"),activation);
        grid.addRow(3,new Label("Taxa η (0 < η ≤ 1)"),learningRate);
        grid.addRow(4,new Label("Máximo de épocas"),epochs);
        grid.addRow(5,new Label("Erro mínimo"),minError);
        grid.addRow(6,new Label("Janela do platô (≥ 10)"),plateauWindow);
        grid.addRow(7,new Label("Limiar do desvio padrão"),plateauThreshold);
        Label formula=new Label("Erro = média de ½ Σ(desejado − saída)².\nA transferência escolhida vale para as duas camadas. Pesos incluem bias."); formula.setWrapText(true);
        configuration=new VBox(14,label("Configuração da rede","section-title"),topology,grid,formula);
        train.getStyleClass().add("primary-button"); stop.getStyleClass().add("danger-button");
        train.setMaxWidth(Double.MAX_VALUE); train.setOnAction(e -> startTraining());
        pause.setOnAction(e -> togglePause()); stop.setOnAction(e -> {
            if(activeTask!=null) { activeTask.stopTraining(); status.setText("Interrompendo ao concluir a época atual…"); addLog("Interrupção solicitada."); }
        });
        evaluate.setOnAction(e -> evaluateNetwork());
        VBox left=new VBox(16,configuration,train,new HBox(10,pause,stop),evaluate); left.setPadding(new Insets(18)); left.setPrefWidth(350);
        HBox cards=new HBox(12,metric("ÉPOCA",currentEpoch),metric("ERRO MÉDIO",currentError),metric("TAXA η",currentRate),metric("TEMPO DECORRIDO",elapsed));
        cards.getChildren().forEach(n -> HBox.setHgrow(n,Priority.ALWAYS));
        NumberAxis x=new NumberAxis(); x.setLabel("Época"); x.setForceZeroInRange(true);
        NumberAxis y=new NumberAxis(); y.setLabel("Erro médio · ½ Σ(d − y)²"); y.setForceZeroInRange(true);
        chart=new LineChart<>(x,y); chart.setAnimated(false); chart.setCreateSymbols(true); chart.setMinHeight(290); chart.setPrefHeight(330);
        errorSeries.setName("Erro do treino"); thresholdSeries.setName("Limiar de parada"); plateauSeries.setName("Platôs");
        chart.getData().addAll(List.of(errorSeries,thresholdSeries,plateauSeries));
        exportHistory.setOnAction(e -> saveHistory()); exportCurve.setOnAction(e -> saveCurve());
        networkView.setPrefHeight(300); networkView.setMinHeight(230);
        TitledPane architecture=new TitledPane("Arquitetura · passe o mouse sobre uma conexão para inspecionar o peso",networkView);
        log.setEditable(false); log.setPrefRowCount(5); log.getStyleClass().add("log-area");
        VBox right=new VBox(12,cards,architecture,chart,new HBox(12,exportHistory,exportCurve),label("Eventos do treinamento","section-title"),log);
        right.setPadding(new Insets(18));
        BorderPane pane=new BorderPane(scroll(right)); pane.setLeft(scroll(left)); return pane;
    }
    private ScrollPane buildEvaluation() {
        accuracy.getStyleClass().add("metric-value"); evaluationSummary.setWrapText(true);
        confusion.setPrefHeight(270); confusion.setPlaceholder(new Label("A matriz será preenchida após a avaliação."));
        classMetrics.setPrefHeight(210);
        NumberAxis y=new NumberAxis(0,100,20); y.setLabel("Percentual (%)");
        metricsChart=new BarChart<>(new CategoryAxis(),y); metricsChart.setAnimated(false); metricsChart.setPrefHeight(280);
        exportMatrix.setOnAction(e -> saveMatrix());
        VBox content=new VBox(16,label("Desempenho em dados de teste","section-title"),accuracy,evaluationSummary,
            new Label("Matriz de confusão · linhas = classe real; colunas = classe prevista"),confusion,exportMatrix,
            label("Precisão e revocação por classe","section-title"),classMetrics,metricsChart);
        content.setPadding(new Insets(22)); return scroll(content);
    }

    private void prepareData() {
        if(busy) return;
        try {
            long experimentSeed=parseLong(seed,"Semente");
            Path trainFile=Path.of(trainingPath.getText().trim()), testFile=Path.of(testPath.getText().trim());
            boolean useSplit=split.isSelected(); double percent=useSplit ? parseDouble(fraction,"Percentual de treino"):70;
            if(useSplit && (percent<=0 || percent>=100)) throw new IllegalArgumentException("O percentual de treino deve ser maior que 0 e menor que 100.");
            String sourceDescription=useSplit ? "Divisão: "+percent+"% de treino; seed da divisão: "+experimentSeed+"; arquivo: "+trainFile
                : "Treino: "+trainFile+"; teste: "+testFile;
            setBusy(true); status.setText("Lendo arquivos e normalizando os dados…");
            Task<PreparedData> load=new Task<>() { @Override protected PreparedData call() throws Exception {
                return useSplit ? DataPreparation.split(trainFile,percent/100,experimentSeed):DataPreparation.loadSeparate(trainFile,testFile);
            }};
            load.setOnSucceeded(e -> { data=load.getValue(); preparedSource=sourceDescription; clearTraining(); setBusy(false); displayPreparedData(); });
            load.setOnFailed(e -> { setBusy(false); showError(load.getException()); });
            daemon(load,"leitura-csv");
        } catch(Exception e) { showError(e); }
    }
    private void displayPreparedData() {
        dataSummary.setText("Treino: "+data.training().size()+" exemplos  •  Teste: "+data.test().size()
            +" exemplos  •  "+data.inputCount()+" atributos  •  Classes: "+String.join(", ",data.classes())+"\n"+preparedSource);
        topology.setText("Entradas: "+data.inputCount()+"  •  Saídas: "+data.outputCount());
        updateHidden(); updatePreview(); showArchitecture(); refreshButtons();
        status.setText("Dados preparados. Configure a rede na aba Treinamento.");
    }
    private void updatePreview() {
        if(data==null) return;
        boolean testing="Teste".equals(previewSet.getValue());
        List<EncodedSample> encoded=testing ? data.test():data.training();
        Dataset raw=testing ? data.testRaw():data.trainingRaw();
        List<String> headers=new ArrayList<>(data.headers()); headers.add("Classe"); headers.add("Desejado (one-hot)"); setColumns(preview,headers);
        var rows=new ArrayList<List<String>>();
        for(int r=0;r<encoded.size();r++) {
            EncodedSample sample=encoded.get(r);
            double[] input=normalized.isSelected() ? sample.input():raw.samples().get(r).values();
            var row=new ArrayList<String>(); for(double value:input) row.add(format(value));
            row.add(data.classes().get(sample.classIndex()));
            StringJoiner target=new StringJoiner(" "); for(double value:sample.target()) target.add(value==1 ? "1":"0");
            row.add(target.toString()); rows.add(row);
        }
        preview.setItems(FXCollections.observableArrayList(rows));
    }
    private void updateHidden() {
        boolean manual="Manual".equals(hiddenMode.getValue()); hidden.setEditable(manual);
        if(!manual && data!=null) {
            double n="Média geométrica".equals(hiddenMode.getValue()) ? Math.sqrt((double)data.inputCount()*data.outputCount())
                :(data.inputCount()+data.outputCount())/2.0;
            hidden.setText(Long.toString(Math.max(1,Math.round(n))));
        }
    }
    private void showArchitecture() {
        if(data==null || busy) return;
        try { int count=parseInt(hidden,"Neurônios ocultos"); if(count>0 && count<=2048) networkView.showNetwork(data.inputCount(),count,data.classes(),null,null); }
        catch(Exception ignored) { /* A validação completa ocorre ao iniciar o treino. */ }
    }
    private TrainingConfig readConfig() {
        int h=parseInt(hidden,"Neurônios ocultos"), max=parseInt(epochs,"Épocas"), window=parseInt(plateauWindow,"Janela do platô");
        double eta=parseDouble(learningRate,"Taxa de aprendizagem"), error=parseDouble(minError,"Erro mínimo"), threshold=parseDouble(plateauThreshold,"Limiar do platô");
        if(h<1 || h>2048) throw new IllegalArgumentException("Informe de 1 a 2048 neurônios ocultos.");
        if(max<1 || max>1000000) throw new IllegalArgumentException("Informe de 1 a 1000000 épocas.");
        if(eta<=0 || eta>1) throw new IllegalArgumentException("A taxa de aprendizagem deve atender a 0 < η ≤ 1.");
        if(error<0) throw new IllegalArgumentException("O erro mínimo deve ser maior ou igual a zero.");
        if(window<10 || window>100000) throw new IllegalArgumentException("A janela de platô deve ter entre 10 e 100000 épocas.");
        if(threshold<0) throw new IllegalArgumentException("O limiar do platô deve ser maior ou igual a zero.");
        return new TrainingConfig(h,activation.getValue(),eta,max,error,parseLong(seed,"Semente"),window,threshold);
    }
    private void startTraining() {
        if(data==null || busy) return;
        try {
            TrainingConfig config=readConfig(); clearTraining(); usedConfig=config;
            network=new Network(data.inputCount(),config.hidden(),data.outputCount(),config.activation(),config.seed());
            networkView.showNetwork(data.inputCount(),config.hidden(),data.classes(),network.weightsInputHidden(),network.weightsHiddenOutput());
            currentRate.setText(format(config.learningRate()));
            thresholdSeries.getData().addAll(List.of(new XYChart.Data<>(0,config.minError()),new XYChart.Data<>(1,config.minError())));
            TrainingTask task=new TrainingTask(network,data,config,this::receiveUpdate,this::handlePlateau);
            activeTask=task;
            task.setOnSucceeded(e -> {
                TrainingTask.Result result=task.getValue(); activeTask=null; setBusy(false);
                status.setText(result.reason()); addLog(result.reason()+". Épocas: "+result.history().size()+".");
                elapsed.setText(String.format(Locale.getDefault(),"%.2f s",result.seconds()));
                networkView.showNetwork(data.inputCount(),config.hidden(),data.classes(),network.weightsInputHidden(),network.weightsHiddenOutput());
                if(!history.isEmpty()) ensureFinalChartPoint();
                refreshButtons();
                if(smoke) { evaluateNetwork(); captureSmoke(); }
            });
            task.setOnFailed(e -> {
                Throwable failure=task.getException(); activeTask=null; network=null; setBusy(false);
                status.setText("Treinamento encerrado por erro"); addLog("Falha: "+failure.getMessage()); showError(failure);
            });
            setBusy(true); status.setText("Treinando…");
            addLog("Treino iniciado: "+config.activation()+", η="+config.learningRate()+", oculta="+config.hidden()+", seed="+config.seed()+".");
            tabs.getSelectionModel().select(trainingTab); daemon(task,"mlp-backpropagation");
        } catch(Exception e) { showError(e); }
    }
    private void receiveUpdate(TrainingTask.Update update) {
        if (network != null && usedConfig != null && (System.nanoTime()-lastArchitectureRefresh>400_000_000L
                || (activeTask!=null && activeTask.isPaused()))) {
            networkView.showNetwork(data.inputCount(),usedConfig.hidden(),data.classes(),update.inputHidden(),update.hiddenOutput());
            lastArchitectureRefresh=System.nanoTime();
        }
        for(TrainingTask.Epoch point:update.epochs()) {
            history.add(point);
            if(point.number()%chartStride==0) errorSeries.getData().add(new XYChart.Data<>(point.number(),point.error()));
            if(errorSeries.getData().size()>3000) {
                chartStride*=2; errorSeries.getData().removeIf(p -> p.getXValue().intValue()%chartStride!=0);
            }
        }
        if(!update.epochs().isEmpty()) {
            var last=update.epochs().getLast(); currentEpoch.setText(Integer.toString(last.number()));
            currentError.setText(format(last.error())); currentRate.setText(format(last.rate()));
            thresholdSeries.getData().getLast().setXValue(last.number());
        }
        elapsed.setText(String.format(Locale.getDefault(),"%.2f s",update.seconds()));
    }
    private void ensureFinalChartPoint() {
        var last=history.getLast();
        if(errorSeries.getData().isEmpty() || errorSeries.getData().getLast().getXValue().intValue()!=last.number())
            errorSeries.getData().add(new XYChart.Data<>(last.number(),last.error()));
    }
    private void togglePause() {
        if(activeTask==null) return;
        if(activeTask.isPaused()) {
            activeTask.resumeTraining(false); pause.setText("Pausar"); status.setText("Treinando…"); addLog("Treino retomado sem alterar η.");
        } else {
            activeTask.pauseTraining(); pause.setText("Continuar"); status.setText("Pausa solicitada; a época atual será concluída."); addLog("Pausa manual solicitada.");
        }
    }
    private void handlePlateau(TrainingTask.Plateau event) {
        if(activeTask==null || !activeTask.isPaused()) return;
        addLog("Platô na época "+event.epoch()+"; desvio padrão="+String.format(Locale.ROOT,"%.8g",event.deviation())+".");
        if(!history.isEmpty()) plateauSeries.getData().add(new XYChart.Data<>(event.epoch(),history.getLast().error()));
        status.setText("Treinamento pausado: platô detectado na época "+event.epoch()); pause.setText("Continuar");
        if(smoke) { smokePlateaus++; activeTask.resumeTraining(smokePlateau); pause.setText("Pausar"); return; }
        ButtonType stopChoice=new ButtonType("Interromper",ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType same=new ButtonType("Continuar com η atual");
        ButtonType reduce=new ButtonType("Reduzir η em 10% e continuar",ButtonBar.ButtonData.OK_DONE);
        Alert alert=new Alert(Alert.AlertType.CONFIRMATION,"O erro estabilizou nas últimas "+usedConfig.plateauWindow()+" épocas.\nDesvio padrão: "+format(event.deviation())+"\nTaxa atual η: "+format(activeTask.currentRate()),stopChoice,same,reduce);
        alert.setTitle("Platô detectado"); alert.setHeaderText("Escolha como prosseguir com o treinamento"); alert.initOwner(stage);
        ButtonType choice=alert.showAndWait().orElse(stopChoice);
        if(activeTask==null) return;
        if(choice==stopChoice) { addLog("Treinamento interrompido após platô."); activeTask.stopTraining(); }
        else {
            double before=activeTask.currentRate(); activeTask.resumeTraining(choice==reduce);
            addLog(choice==reduce ? "η reduzido em 10%: "+format(before)+" → "+format(activeTask.currentRate()):"Platô: continuando sem alterar η.");
            currentRate.setText(format(activeTask.currentRate())); pause.setText("Pausar"); status.setText("Treinando…");
        }
    }
    private void evaluateNetwork() {
        if(network==null || data==null || busy || history.isEmpty()) return;
        try {
            evaluation=Evaluation.evaluate(network,data.test(),data.classes());
            accuracy.setText(String.format(Locale.getDefault(),"Acurácia: %.2f%%",100*evaluation.accuracy()));
            evaluationSummary.setText(evaluation.correct()+" acertos em "+evaluation.total()+" exemplos de teste.  "+usedConfig.activation()+" • Rede "+data.inputCount()+" → "+usedConfig.hidden()+" → "+data.outputCount()+" • η inicial "+usedConfig.learningRate()+" • η final "+history.getLast().rate()+" • seed do treino "+usedConfig.seed()+"\n"+preparedSource);
            List<String> headers=new ArrayList<>(); headers.add("Real / Prevista"); headers.addAll(data.classes()); headers.add("Total"); setColumns(confusion,headers);
            int[][] matrix=evaluation.confusionMatrix(); var rows=new ArrayList<List<String>>(); int[] totals=new int[data.outputCount()];
            for(int r=0;r<matrix.length;r++) {
                var row=new ArrayList<String>(); row.add(data.classes().get(r)); int total=0;
                for(int c=0;c<matrix[r].length;c++) { row.add(Integer.toString(matrix[r][c])); total+=matrix[r][c]; totals[c]+=matrix[r][c]; }
                row.add(Integer.toString(total)); rows.add(row);
            }
            var totalRow=new ArrayList<String>(); totalRow.add("Total"); for(int n:totals) totalRow.add(Integer.toString(n)); totalRow.add(Integer.toString(evaluation.total())); rows.add(totalRow);
            confusion.setItems(FXCollections.observableArrayList(rows));
            for(int c=1;c<=data.outputCount();c++) markDiagonal(confusion.getColumns().get(c),c-1);
            setColumns(classMetrics,List.of("Classe","Precisão","Revocação","Exemplos reais"));
            var metricRows=new ArrayList<List<String>>();
            var pSeries=new XYChart.Series<String,Number>(); pSeries.setName("Precisão");
            var rSeries=new XYChart.Series<String,Number>(); rSeries.setName("Revocação");
            for(int i=0;i<data.outputCount();i++) {
                int support=0; for(int n:matrix[i]) support+=n;
                metricRows.add(List.of(data.classes().get(i),percent(evaluation.precision(i)),percent(evaluation.recall(i)),Integer.toString(support)));
                pSeries.getData().add(new XYChart.Data<>(data.classes().get(i),100*evaluation.precision(i)));
                rSeries.getData().add(new XYChart.Data<>(data.classes().get(i),100*evaluation.recall(i)));
            }
            classMetrics.setItems(FXCollections.observableArrayList(metricRows)); metricsChart.getData().setAll(List.of(pSeries,rSeries));
            tabs.getSelectionModel().select(evaluationTab); refreshButtons(); addLog("Avaliação: "+percent(evaluation.accuracy())+" de acurácia.");
        } catch(Exception e) { showError(e); }
    }
    private void clearTraining() {
        network=null; evaluation=null; usedConfig=null; history.clear(); chartStride=1;
        errorSeries.getData().clear(); thresholdSeries.getData().clear(); plateauSeries.getData().clear();
        confusion.getItems().clear(); classMetrics.getItems().clear(); metricsChart.getData().clear();
        accuracy.setText("—"); evaluationSummary.setText("Treine a rede e aplique-a aos exemplos de teste.");
        currentEpoch.setText("0"); currentError.setText("—"); currentRate.setText("—"); elapsed.setText("0,0 s"); log.clear(); refreshButtons();
    }
    private void setBusy(boolean value) { busy=value; dataContent.setDisable(value); configuration.setDisable(value); refreshButtons(); }
    private void refreshButtons() {
        train.setDisable(data==null || busy); pause.setDisable(activeTask==null); stop.setDisable(activeTask==null);
        if(activeTask==null) pause.setText("Pausar");
        evaluate.setDisable(network==null || busy || history.isEmpty());
        exportHistory.setDisable(history.isEmpty() || busy); exportCurve.setDisable(history.isEmpty() || busy); exportMatrix.setDisable(evaluation==null || busy);
    }
    private void saveHistory() {
        File file=chooseOutput("Exportar histórico","historico.csv","CSV","*.csv"); if(file==null) return;
        try {
            StringBuilder csv=new StringBuilder("epoca,erro_medio,taxa_aprendizagem\n");
            for(var p:history) csv.append(p.number()).append(',').append(p.error()).append(',').append(p.rate()).append('\n');
            Files.writeString(file.toPath(),csv,StandardCharsets.UTF_8); status.setText("Histórico salvo em "+file);
        } catch(IOException e) { showError(e); }
    }
    private void saveMatrix() {
        File file=chooseOutput("Exportar matriz","matriz_confusao.csv","CSV","*.csv"); if(file==null) return;
        try {
            StringBuilder text=new StringBuilder("real/prevista"); for(String name:data.classes()) text.append(',').append(csv(name)); text.append('\n');
            int[][] matrix=evaluation.confusionMatrix();
            for(int r=0;r<matrix.length;r++) { text.append(csv(data.classes().get(r))); for(int n:matrix[r]) text.append(',').append(n); text.append('\n'); }
            Files.writeString(file.toPath(),text,StandardCharsets.UTF_8); status.setText("Matriz salva em "+file);
        } catch(IOException e) { showError(e); }
    }
    private void saveCurve() {
        File file=chooseOutput("Salvar curva de erro","curva_erro.png","PNG","*.png"); if(file==null) return;
        try { ImageIO.write(SwingFXUtils.fromFXImage(chart.snapshot(null,null),null),"png",file); status.setText("Curva salva em "+file); }
        catch(IOException e) { showError(e); }
    }
    private void chooseCsv(TextField target) {
        FileChooser chooser=new FileChooser(); chooser.setTitle("Selecionar base CSV"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos CSV","*.csv"));
        File file=chooser.showOpenDialog(stage); if(file!=null) target.setText(file.getAbsolutePath());
    }
    private File chooseOutput(String title,String name,String description,String pattern) {
        FileChooser chooser=new FileChooser(); chooser.setTitle(title); chooser.setInitialFileName(name); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description,pattern)); return chooser.showSaveDialog(stage);
    }
    private void addLog(String text) { log.appendText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"  "+text+"\n"); }
    private void showError(Throwable error) {
        if(smoke) { smokeFailure(error); return; }
        String message=error.getMessage()==null ? error.toString():error.getMessage(); status.setText(message);
        Alert alert=new Alert(Alert.AlertType.ERROR,message,ButtonType.OK); alert.setTitle("Confira os dados informados"); alert.setHeaderText("Não foi possível concluir a operação"); alert.initOwner(stage); alert.showAndWait();
    }
    private static double parseDouble(TextField field,String name) {
        try { double n=Double.parseDouble(field.getText().trim().replace(',','.')); if(!Double.isFinite(n)) throw new NumberFormatException(); return n; }
        catch(NumberFormatException e) { throw new IllegalArgumentException(name+": informe um número finito válido."); }
    }
    private static int parseInt(TextField field,String name) {
        try { return Integer.parseInt(field.getText().trim()); } catch(NumberFormatException e) { throw new IllegalArgumentException(name+": informe um número inteiro válido."); }
    }
    private static long parseLong(TextField field,String name) {
        try { return Long.parseLong(field.getText().trim()); } catch(NumberFormatException e) { throw new IllegalArgumentException(name+": informe um número inteiro válido."); }
    }
    private static String format(double n) { return String.format(Locale.getDefault(),"%.7g",n); }
    private static String percent(double n) { return String.format(Locale.getDefault(),"%.2f%%",n*100); }
    private static String csv(String value) { return "\""+value.replace("\"","\"\"")+"\""; }
    private static Label label(String text,String css) { Label label=new Label(text); label.getStyleClass().add(css); return label; }
    private static VBox metric(String name,Label value) { value.getStyleClass().add("metric-value"); VBox box=new VBox(5,label(name,"metric-label"),value); box.getStyleClass().add("metric-card"); box.setMinWidth(125); return box; }
    private static GridPane formGrid() { GridPane grid=new GridPane(); grid.setHgap(10); grid.setVgap(12); return grid; }
    private static ScrollPane scroll(javafx.scene.Node content) { ScrollPane pane=new ScrollPane(content); pane.setFitToWidth(true); pane.setPannable(true); return pane; }
    private static void daemon(Runnable runnable,String name) { Thread thread=new Thread(runnable,name); thread.setDaemon(true); thread.start(); }
    private static void setColumns(TableView<List<String>> table,List<String> headers) {
        table.getColumns().clear();
        for(int i=0;i<headers.size();i++) { final int c=i; var col=new TableColumn<List<String>,String>(headers.get(i)); col.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().get(c))); col.setPrefWidth(i==0 ? 140:115); table.getColumns().add(col); }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }
    private static <T> void markDiagonal(TableColumn<List<String>,T> column,int row) {
        column.setCellFactory(col -> new TableCell<List<String>,T>() {
            @Override protected void updateItem(T value,boolean empty) {
                super.updateItem(value,empty); setText(empty || value==null ? null:value.toString());
                setStyle(!empty && getIndex()==row ? "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-font-weight: bold;":"");
            }
        });
    }
    private void captureSmoke() {
        try {
            if(evaluation==null || history.isEmpty() || evaluation.total()!=data.test().size()) throw new IllegalStateException("Smoke: avaliação incompleta.");
            if(smokePlateau && smokePlateaus<2) throw new IllegalStateException("Smoke: platô não foi reapresentado.");
            if(smokePlateau && Math.abs(history.getLast().rate()-0.3*Math.pow(0.9,smokePlateaus))>1e-12)
                throw new IllegalStateException("Smoke: redução de taxa inconsistente.");
            Files.createDirectories(Path.of("target/screenshots")); tabs.getSelectionModel().select(trainingTab);
            PauseTransition delay=new PauseTransition(Duration.millis(450)); delay.setOnFinished(e -> {
                try {
                    stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                    ImageIO.write(SwingFXUtils.fromFXImage(stage.getScene().snapshot(null),null),"png",Path.of("target/screenshots/treinamento.png").toFile());
                    tabs.getSelectionModel().select(evaluationTab);
                    PauseTransition next=new PauseTransition(Duration.millis(350)); next.setOnFinished(evt -> {
                        try {
                            ImageIO.write(SwingFXUtils.fromFXImage(stage.getScene().snapshot(null),null),"png",Path.of("target/screenshots/avaliacao.png").toFile());
                            Files.writeString(Path.of("target/ui-smoke"+(smokePlateau ? "-plateau":"")+".txt"),"OK\nepocas="+history.size()+"\nacuracia="+evaluation.accuracy()+"\nplatos="+smokePlateaus+"\n",StandardCharsets.UTF_8);
                            Platform.exit();
                        } catch(Exception failure) { smokeFailure(failure); }
                    }); next.play();
                } catch(Exception failure) { smokeFailure(failure); }
            }); delay.play();
        } catch(Exception error) { smokeFailure(error); }
    }
    private void smokeFailure(Throwable error) { error.printStackTrace(); Platform.exit(); throw new IllegalStateException("Falha no smoke test JavaFX",error); }
}

