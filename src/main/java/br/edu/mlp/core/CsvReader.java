package br.edu.mlp.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/** Leitor CSV autoral: vírgulas, aspas e aspas escapadas; UTF-8 com BOM opcional. */
public final class CsvReader {
    private CsvReader() { }

    public static Dataset read(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (first == null) throw new IllegalArgumentException("O arquivo está vazio: " + file.getFileName());
            if (first.startsWith("\uFEFF")) first = first.substring(1);
            List<String> columns = parseLine(first, 1);
            if (columns.size() < 2) throw new IllegalArgumentException("O CSV precisa de ao menos um atributo e da classe na última coluna.");
            if (columns.stream().anyMatch(String::isBlank) || new HashSet<>(columns).size() != columns.size()) {
                throw new IllegalArgumentException("O cabeçalho deve ter nomes preenchidos e distintos.");
            }
            List<String> headers = List.copyOf(columns.subList(0, columns.size() - 1));
            List<Sample> samples = new ArrayList<>();
            TreeSet<String> labels = new TreeSet<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                List<String> cells = parseLine(line, lineNumber);
                if (cells.size() != columns.size()) {
                    throw new IllegalArgumentException("Linha " + lineNumber + ": esperado " + columns.size() + " colunas; encontrado " + cells.size() + ".");
                }
                double[] values = new double[headers.size()];
                for (int i = 0; i < values.length; i++) {
                    try {
                        values[i] = Double.parseDouble(cells.get(i));
                        if (!Double.isFinite(values[i])) throw new NumberFormatException();
                    } catch (NumberFormatException error) {
                        throw new IllegalArgumentException("Linha " + lineNumber + ", coluna " + headers.get(i) + ": informe um número finito usando ponto decimal.");
                    }
                }
                String label = cells.get(cells.size() - 1).strip();
                if (label.isEmpty()) throw new IllegalArgumentException("Linha " + lineNumber + ": a classe está vazia.");
                labels.add(label);
                samples.add(new Sample(values, label));
            }
            if (samples.isEmpty()) throw new IllegalArgumentException("O CSV contém cabeçalho, mas nenhuma amostra.");
            return new Dataset(headers, samples, List.copyOf(labels));
        }
    }

    private static List<String> parseLine(String line, int lineNumber) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (quoted) {
                if (character == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else cell.append(character);
            } else if (character == ',') {
                cells.add(cell.toString().strip()); cell.setLength(0); closed = false;
            } else if (character == '"') {
                if (!cell.toString().isBlank() || closed) throw new IllegalArgumentException("Aspas inválidas na linha " + lineNumber + ".");
                cell.setLength(0); quoted = true;
            } else {
                if (closed && !Character.isWhitespace(character)) throw new IllegalArgumentException("Conteúdo após aspas na linha " + lineNumber + ".");
                cell.append(character);
            }
        }
        if (quoted) throw new IllegalArgumentException("Aspas não fechadas na linha " + lineNumber + ". Campos com quebra de linha não são aceitos.");
        cells.add(cell.toString().strip());
        return cells;
    }
}
