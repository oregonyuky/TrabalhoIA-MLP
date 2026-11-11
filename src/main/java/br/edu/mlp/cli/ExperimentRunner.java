package br.edu.mlp.cli;

import br.edu.mlp.core.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Experimentos reproduziveis, sem dependencias de interface grafica. */
public final class ExperimentRunner {
    private static final long SEED = 42;
    private static final int MAX_EPOCHS = 2_000;
    private static final double MIN_ERROR = 0.005;
    private static final int PLATEAU_WINDOW = 10;
    private static final double PLATEAU_THRESHOLD = 0.00001;

    private record Configuration(String id, Activation activation, int hidden, double rate) { }
    private record Point(int epoch, double error, boolean plateau) { }
    private record Result(Configuration config, int epochs, double finalError, double accuracy,
                          int correct, int total, double seconds, List<Integer> plateaus,
                          String reason, Evaluation evaluation) { }

    private ExperimentRunner() { }

    /** Args: diretorio opcional do projeto; substitui os artefatos dos tres experimentos. */
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Uso: ExperimentRunner [diretorio-do-projeto]");
        }
        Path root = args.length == 0 ? Path.of("").toAbsolutePath() : Path.of(args[0]).toAbsolutePath();
        Path training = root.resolve("entradas/base_treinamento.csv");
        Path test = root.resolve("entradas/base_teste.csv");
        var configs = List.of(
                new Configuration("logistica-6-eta03", Activation.LOGISTIC, 6, 0.3),
                new Configuration("logistica-12-eta01", Activation.LOGISTIC, 12, 0.1),
                new Configuration("tanh-6-eta005", Activation.TANH, 6, 0.05));
        var results = new ArrayList<Result>();
        for (Configuration config : configs) {
            results.add(run(config, training, test, root.resolve("experimentos").resolve(config.id())));
        }
        writeReport(root, results);
        System.out.println("Relat\u00f3rio: " + root.resolve("docs/RELATORIO_EXPERIMENTOS.md"));
    }

    private static Result run(Configuration config, Path training, Path test, Path output)
            throws IOException {
        var data = DataPreparation.loadSeparate(training, test);
        var network = new Network(data.inputCount(), config.hidden(), data.outputCount(),
                config.activation(), SEED);
        var random = new Random(SEED ^ 0x5DEECE66DL);
        var detector = new PlateauDetector(PLATEAU_WINDOW, PLATEAU_THRESHOLD);
        var history = new ArrayList<Point>();
        var plateaus = new ArrayList<Integer>();
        long started = System.nanoTime();
        String reason = "Limite de \u00e9pocas atingido";
        for (int epoch = 1; epoch <= MAX_EPOCHS; epoch++) {
            double error = network.trainEpoch(data.training(), config.rate(), random);
            if (!Double.isFinite(error)) {
                throw new IllegalStateException(config.id() + ": erro n\u00e3o finito na \u00e9poca " + epoch);
            }
            boolean reached = error <= MIN_ERROR;
            boolean plateau = detector.add(error) && !reached && epoch < MAX_EPOCHS;
            history.add(new Point(epoch, error, plateau));
            if (reached) {
                reason = "Erro m\u00ednimo atingido";
                break;
            }
            if (plateau) {
                plateaus.add(epoch);
                // Politica automatica documentada: registrar e continuar com a mesma taxa.
                detector.reset();
            }
        }
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        var evaluation = Evaluation.evaluate(network, data.test(), data.classes());
        var result = new Result(config, history.size(), history.getLast().error(),
                evaluation.accuracy(), evaluation.correct(), evaluation.total(), seconds,
                List.copyOf(plateaus), reason, evaluation);
        Files.createDirectories(output);
        writeHistory(output.resolve("historico.csv"), history);
        writeMatrix(output.resolve("matriz.csv"), evaluation.confusionMatrix(), data.classes());
        writeJson(output.resolve("resultados.json"), result, data.inputCount(),
                data.training().size(), data.classes(), evaluation.confusionMatrix(), evaluation);
        System.out.printf(Locale.ROOT,
                "%s: %d epocas, erro=%.8f, acuracia=%.2f%% (%d/%d), platos=%d%n",
                config.id(), result.epochs(), result.finalError(), result.accuracy() * 100,
                result.correct(), result.total(), result.plateaus().size());
        return result;
    }

    private static void writeHistory(Path file, List<Point> history) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("epoca,erro,plato_detectado\n");
            for (Point point : history) {
                writer.write(point.epoch() + "," + number(point.error()) + "," + point.plateau() + "\n");
            }
        }
    }

    private static void writeMatrix(Path file, int[][] matrix, List<String> classes)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("real/predita");
            for (String label : classes) writer.write("," + csv(label));
            writer.write("\n");
            for (int row = 0; row < matrix.length; row++) {
                writer.write(csv(classes.get(row)));
                for (int count : matrix[row]) writer.write("," + count);
                writer.write("\n");
            }
        }
    }

    private static void writeJson(Path file, Result result, int inputs, int trainingSamples,
                                  List<String> classes, int[][] matrix, Evaluation evaluation)
            throws IOException {
        var config = result.config();
        var text = new StringBuilder("{\n");
        text.append("  \"id\": ").append(json(config.id())).append(",\n");
        text.append("  \"activation\": ").append(json(config.activation().name())).append(",\n");
        text.append("  \"inputs\": ").append(inputs).append(",\n");
        text.append("  \"hidden\": ").append(config.hidden()).append(",\n");
        text.append("  \"outputs\": ").append(classes.size()).append(",\n");
        text.append("  \"learningRate\": ").append(number(config.rate())).append(",\n");
        text.append("  \"seed\": ").append(SEED).append(",\n");
        text.append("  \"shuffleSeed\": ").append(SEED ^ 0x5DEECE66DL).append(",\n");
        text.append("  \"maxEpochs\": ").append(MAX_EPOCHS).append(",\n");
        text.append("  \"minError\": ").append(number(MIN_ERROR)).append(",\n");
        text.append("  \"trainingSamples\": ").append(trainingSamples).append(",\n");
        text.append("  \"testSamples\": ").append(result.total()).append(",\n");
        text.append("  \"epochs\": ").append(result.epochs()).append(",\n");
        text.append("  \"finalError\": ").append(number(result.finalError())).append(",\n");
        text.append("  \"accuracy\": ").append(number(result.accuracy())).append(",\n");
        text.append("  \"correct\": ").append(result.correct()).append(",\n");
        text.append("  \"seconds\": ").append(number(result.seconds())).append(",\n");
        text.append("  \"stopReason\": ").append(json(result.reason())).append(",\n");
        text.append("  \"plateau\": {\"window\": ").append(PLATEAU_WINDOW)
                .append(", \"threshold\": ").append(number(PLATEAU_THRESHOLD))
                .append(", \"policy\": \"record_and_continue_unchanged\", \"epochs\": ")
                .append(result.plateaus()).append("},\n");
        text.append("  \"classes\": [");
        for (int index = 0; index < classes.size(); index++) {
            if (index > 0) text.append(", ");
            text.append(json(classes.get(index)));
        }
        text.append("],\n  \"confusionMatrix\": [\n");
        for (int row = 0; row < matrix.length; row++) {
            text.append("    [");
            for (int col = 0; col < matrix[row].length; col++) {
                if (col > 0) text.append(", ");
                text.append(matrix[row][col]);
            }
            text.append("]").append(row + 1 < matrix.length ? ",\n" : "\n");
        }
        text.append("  ],\n  \"perClass\": [\n");
        for (int index = 0; index < classes.size(); index++) {
            text.append("    {\"class\": ").append(json(classes.get(index)))
                    .append(", \"precision\": ").append(number(evaluation.precision(index)))
                    .append(", \"recall\": ").append(number(evaluation.recall(index))).append("}")
                    .append(index + 1 < classes.size() ? ",\n" : "\n");
        }
        text.append("  ]\n}\n");
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void writeReport(Path root, List<Result> results) throws IOException {
        StringBuilder text = new StringBuilder();
        var data = DataPreparation.loadSeparate(root.resolve("entradas/base_treinamento.csv"),
                root.resolve("entradas/base_teste.csv"));
        text.append("# Experimentos com a rede MLP\n\n");
        text.append(String.format(Locale.ROOT,
                "Base: %d atributos num\u00e9ricos, %d classes, %d amostras de treino e %d de teste. ",
                data.inputCount(), data.outputCount(), data.training().size(), data.test().size()));
        text.append("Normaliza\u00e7\u00e3o min-max ajustada apenas no treino e alvos one-hot.\n\n");
        text.append("Resultados medidos pelo programa, com implementa\u00e7\u00e3o pr\u00f3pria de forward e backpropagation. ");
        text.append("Bases: \u0060entradas/base_treinamento.csv\u0060 e \u0060entradas/base_teste.csv\u0060. ");
        text.append("O teste \u00e9 usado apenas para avaliar a rede ao final de cada treinamento; ");
        text.append("n\u00e3o participa da normaliza\u00e7\u00e3o nem da atualiza\u00e7\u00e3o dos pesos.\n\n");
        text.append("Todos os experimentos usam semente 42, embaralhamento determin\u00edstico, ");
        text.append("at\u00e9 2.000 \u00e9pocas e erro m\u00ednimo 0,005. ");
        text.append("O erro da curva \u00e9 a m\u00e9dia do erro quadr\u00e1tico E retornada por cada \u00e9poca de treinamento. ");
        text.append("A acur\u00e1cia \u00e9 a fra\u00e7\u00e3o de previs\u00f5es corretas na base de teste.\n\n");
        text.append("| Experimento | Ativa\u00e7\u00e3o | Ocultos | Taxa | \u00c9pocas | Erro final | Acur\u00e1cia de teste | Plat\u00f4s |\n");
        text.append("|---|---|---:|---:|---:|---:|---:|---:|\n");
        for (Result result : results) {
            text.append(String.format(Locale.ROOT, "| %s | %s | %d | %.2f | %d | %.8f | %.2f%% (%d/%d) | %d |\n",
                    result.config().id(), result.config().activation(), result.config().hidden(),
                    result.config().rate(), result.epochs(), result.finalError(),
                    result.accuracy() * 100, result.correct(), result.total(), result.plateaus().size()));
        }
        text.append("\n## Tratamento de plat\u00f4\n\n");
        text.append("O detector usa uma janela de 10 erros e limiar de desvio-padr\u00e3o 0,00001. ");
        text.append("Neste executor autom\u00e1tico, cada detec\u00e7\u00e3o \u00e9 registrada e o treino continua ");
        text.append("com a mesma taxa de aprendizagem, pesos e dados. Apenas a janela do detector \u00e9 reiniciada. ");
        text.append("N\u00e3o h\u00e1 decis\u00e3o interativa na CLI; na interface gr\u00e1fica o usu\u00e1rio ");
        text.append("pode escolher continuar, reduzir a taxa em 10% ou parar.\n\n");
        text.append("## Curvas e arquivos completos\n\n");
        for (Result result : results) {
            String folder = "../experimentos/" + result.config().id() + "/";
            text.append("### ").append(result.config().id()).append("\n\n");
            text.append("Parada: ").append(result.reason()).append(". ");
            text.append("Detec\u00e7\u00f5es de plat\u00f4: ").append(result.plateaus().isEmpty() ? "nenhuma" : result.plateaus())
                    .append(".\n\n");
            text.append("![Curva de erro por \u00e9poca](").append(folder).append("curva.png)\n\n");
            text.append("| Real / Prevista |");
            for (String label : result.evaluation().classes()) text.append(" ").append(label).append(" |");
            text.append("\n|---|");
            for (String ignored : result.evaluation().classes()) text.append("---:|");
            text.append("\n");
            int[][] matrix = result.evaluation().confusionMatrix();
            for (int row=0; row<matrix.length; row++) {
                text.append("| ").append(result.evaluation().classes().get(row)).append(" |");
                for (int value : matrix[row]) text.append(" ").append(value).append(" |");
                text.append("\n");
            }
            text.append("\n");
            text.append("[Hist\u00f3rico CSV](").append(folder).append("historico.csv), ");
            text.append("[matriz de confus\u00e3o CSV](").append(folder).append("matriz.csv) e ");
            text.append("[configura\u00e7\u00e3o e m\u00e9tricas JSON](").append(folder).append("resultados.json).\n\n");
        }
        double highestAccuracy = results.stream().mapToDouble(Result::accuracy).max().orElseThrow();
        long tied = results.stream().filter(result -> result.accuracy() == highestAccuracy).count();
        Result fewestEpochs = results.stream().min(java.util.Comparator.comparingInt(Result::epochs)).orElseThrow();
        text.append("## Discuss\u00e3o\n\n");
        text.append(String.format(Locale.ROOT,
                "A maior acur\u00e1cia observada nesta base de teste foi %.2f%%, alcan\u00e7ada por %d das %d configura\u00e7\u00f5es. ",
                highestAccuracy * 100, tied, results.size()));
        text.append(String.format(Locale.ROOT,
                "O experimento \u0060%s\u0060 precisou do menor n\u00famero de \u00e9pocas (%d) para atingir o erro m\u00ednimo. ",
                fewestEpochs.config().id(), fewestEpochs.epochs()));
        text.append("A compara\u00e7\u00e3o usa uma \u00fanica inicializa\u00e7\u00e3o por configura\u00e7\u00e3o; ");
        text.append("portanto, os resultados n\u00e3o estimam a variabilidade entre sementes nem provam ");
        text.append("superioridade geral de uma ativa\u00e7\u00e3o ou arquitetura. ");
        text.append("As duas configura\u00e7\u00f5es log\u00edsticas mudam simultaneamente a largura e a taxa, ");
        text.append("de modo que seus efeitos n\u00e3o podem ser atribu\u00eddos a um \u00fanico fator.\n\n");
        text.append("Nas matrizes, as linhas representam as classes reais e as colunas as classes preditas. ");
        text.append("Os JSONs incluem precis\u00e3o e revoca\u00e7\u00e3o por classe para identificar confus\u00f5es ");
        text.append("que a acur\u00e1cia agregada pode ocultar.\n\n");
        text.append("Reprodu\u00e7\u00e3o: \u0060.\\mvnw.cmd compile exec:java '-Dexec.mainClass=br.edu.mlp.cli.ExperimentRunner'\u0060. ");
        text.append("Os PNGs s\u00e3o exporta\u00e7\u00f5es de gr\u00e1ficos JavaFX Charts a partir dos hist\u00f3ricos CSV.\n");
        Path report = root.resolve("docs/RELATORIO_EXPERIMENTOS.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, text, StandardCharsets.UTF_8);
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("M\u00e9trica n\u00e3o finita");
        return Double.toString(value);
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.append("\"").toString();
    }
}
