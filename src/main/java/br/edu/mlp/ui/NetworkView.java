package br.edu.mlp.ui;

import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

/** View of one hidden layer. Matrices use [destination][source], with bias last. */
public final class NetworkView extends BorderPane {
    private static final double ROW = 48;
    private static final double TOP = 82;
    private static final double RADIUS = 17;
    private static final Color POSITIVE = Color.web("#0f978b");
    private static final Color NEGATIVE = Color.web("#d46d7b");
    private final Pane drawing = new Pane();
    private final ScrollPane scroll = new ScrollPane(drawing);
    private int inputs;
    private int hidden;
    private List<String> classes = List.of();
    private double[][] inputHidden;
    private double[][] hiddenOutput;

    public NetworkView() {
        getStyleClass().add("network-view");
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.setMinHeight(280);
        scroll.setPrefViewportHeight(380);
        scroll.getStyleClass().add("network-scroll");
        setCenter(scroll);
        Label guide = new Label("Passe o mouse sobre uma conexão para consultar o peso.");
        guide.getStyleClass().add("subtitle");
        Label positive = new Label("● positivo");
        positive.setStyle("-fx-text-fill: #0f978b;");
        Label negative = new Label("● negativo");
        negative.setStyle("-fx-text-fill: #d46d7b;");
        Label magnitude = new Label("Espessura = magnitude · b = bias (entrada 1)");
        magnitude.getStyleClass().add("subtitle");
        HBox legend = new HBox(16, positive, negative, magnitude);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setPadding(new Insets(8, 12, 8, 12));
        setTop(guide);
        setBottom(legend);
        drawing.widthProperty().addListener((observable, oldValue, newValue) -> redraw());
        showNetwork(0, 0, List.of(), null, null);
    }

    /** Call on the JavaFX thread. Null matrices show the configured architecture. */
    public void showNetwork(int inputs, int hidden, List<String> classes,
                            double[][] inputHidden, double[][] hiddenOutput) {
        if (inputs < 0 || hidden < 0 || classes == null) {
            throw new IllegalArgumentException("Dimensões inválidas para a arquitetura.");
        }
        this.inputs = inputs;
        this.hidden = hidden;
        this.classes = List.copyOf(classes);
        this.inputHidden = copy(inputHidden);
        this.hiddenOutput = copy(hiddenOutput);
        drawing.setMinWidth(680);
        drawing.setPrefWidth(900);
        drawing.setMinHeight(Math.max(300,
            TOP + ROW * Math.max(inputs + 1, Math.max(hidden + 1, classes.size())) + 28));
        drawing.setPrefHeight(drawing.getMinHeight());
        redraw();
    }

    private static double[][] copy(double[][] matrix) {
        if (matrix == null) return null;
        double[][] result = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) result[i] = matrix[i].clone();
        return result;
    }

    private void redraw() {
        drawing.getChildren().clear();
        double width = Math.max(680, drawing.getWidth());
        if (inputs == 0 || hidden == 0 || classes.isEmpty()) {
            Text hint = new Text(30, 100, "Carregue os dados para visualizar a arquitetura da rede.");
            hint.setFill(Color.web("#64748b"));
            hint.setFont(Font.font(14));
            drawing.getChildren().add(hint);
            return;
        }
        double xInput = 88;
        double xHidden = width / 2;
        double xOutput = width - 132;
        double maxWeight = maximumWeight();
        // Draw edges before neurons so endpoints remain visible and accessible.
        for (int h = 0; h < hidden; h++) {
            for (int i = 0; i <= inputs; i++) {
                addEdge(xInput, y(i), xHidden, y(h), weight(inputHidden, h, i), maxWeight,
                        (i == inputs ? "bias da entrada" : "x" + (i + 1)) + " → h" + (h + 1));
            }
        }
        for (int o = 0; o < classes.size(); o++) {
            for (int h = 0; h <= hidden; h++) {
                addEdge(xHidden, y(h), xOutput, y(o), weight(hiddenOutput, o, h), maxWeight,
                        (h == hidden ? "bias oculto" : "h" + (h + 1)) + " → " + classes.get(o));
            }
        }
        heading(xInput, "Entrada · " + inputs);
        heading(xHidden, "Oculta · " + hidden);
        heading(xOutput, "Saída · " + classes.size());
        for (int i = 0; i < inputs; i++) addNode(xInput, y(i), "x" + (i + 1), "Atributo " + (i + 1), false, "#e8f0ff");
        addNode(xInput, y(inputs), "b", "Bias da camada oculta: entrada constante 1", true, "#f1f5f9");
        for (int h = 0; h < hidden; h++) addNode(xHidden, y(h), "h" + (h + 1), "Neurônio oculto " + (h + 1), false, "#dff6f2");
        addNode(xHidden, y(hidden), "b", "Bias da camada de saída: entrada constante 1", true, "#f1f5f9");
        for (int o = 0; o < classes.size(); o++) {
            addNode(xOutput, y(o), Integer.toString(o + 1), "Classe " + classes.get(o), false, "#f2eaff");
            Text label = new Text(xOutput + 25, y(o) + 4, abbreviate(classes.get(o), 16));
            label.setFill(Color.web("#334155"));
            label.setFont(Font.font(12));
            installTooltip(label, "Classe " + classes.get(o));
            drawing.getChildren().add(label);
        }
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static double y(int row) { return TOP + row * ROW; }

    private static Double weight(double[][] matrix, int destination, int source) {
        return matrix != null && destination < matrix.length && source < matrix[destination].length
                ? matrix[destination][source] : null;
    }

    private double maximumWeight() {
        double max = 0;
        for (double[][] matrix : new double[][][]{inputHidden, hiddenOutput}) {
            if (matrix == null) continue;
            for (double[] row : matrix) for (double weight : row) max = Math.max(max, Math.abs(weight));
        }
        return max == 0 ? 1 : max;
    }

    private void addEdge(double x1, double y1, double x2, double y2, Double weight,
                         double maxWeight, String name) {
        Line line = new Line(x1 + RADIUS, y1, x2 - RADIUS, y2);
        Color color = weight == null ? Color.web("#cbd5e1") : weight >= 0 ? POSITIVE : NEGATIVE;
        double thickness = weight == null ? 1 : 0.6 + 2.8 * Math.abs(weight) / maxWeight;
        line.setStroke(color);
        line.setStrokeWidth(thickness);
        line.setOpacity(weight == null ? 0.7 : 0.56);
        line.setPickOnBounds(false);
        line.setAccessibleText(name + (weight == null ? ": peso ainda não inicializado" : ": " + weight));
        installTooltip(line, name + "\n" + (weight == null ? "Peso definido ao iniciar o treinamento."
                : String.format(Locale.forLanguageTag("pt-BR"), "Peso: %.8f", weight)));
        line.setOnMouseEntered(event -> { line.setStrokeWidth(Math.max(3.5, thickness + 1.4)); line.setOpacity(1); });
        line.setOnMouseExited(event -> { line.setStrokeWidth(thickness); line.setOpacity(weight == null ? 0.7 : 0.56); });
        drawing.getChildren().add(line);
    }

    private void addNode(double x, double y, String name, String description, boolean bias, String fill) {
        Circle circle = new Circle(x, y, RADIUS, Color.web(fill));
        circle.setStroke(Color.web(bias ? "#94a3b8" : "#8aa6bf"));
        circle.setStrokeWidth(1.3);
        if (bias) circle.getStrokeDashArray().addAll(3.0, 3.0);
        circle.setAccessibleText(description);
        installTooltip(circle, description);
        Text text = new Text(name);
        text.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
        text.setFill(Color.web("#1e3a5f"));
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(y + 4);
        text.setMouseTransparent(true);
        drawing.getChildren().addAll(circle, text);
    }

    private void heading(double x, String label) {
        Text text = new Text(label);
        text.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        text.setFill(Color.web("#1e3a5f"));
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(30);
        text.setMouseTransparent(true);
        drawing.getChildren().add(text);
    }

    private static void installTooltip(javafx.scene.Node node, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(120));
        tooltip.setShowDuration(Duration.seconds(30));
        Tooltip.install(node, tooltip);
    }
}
