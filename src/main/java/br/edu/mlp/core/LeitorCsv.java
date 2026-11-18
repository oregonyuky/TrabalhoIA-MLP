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

/** Lê arquivos CSV com cabeçalho, atributos e coluna de classe. */
public final class LeitorCsv {
    private LeitorCsv() {}

    public static ConjuntoDados ler(Path arquivo) throws IOException {
        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            String cabecalho = leitor.readLine();
            if (cabecalho == null) throw new IllegalArgumentException("O arquivo está vazio: " + arquivo.getFileName());
            if (cabecalho.startsWith("\uFEFF")) cabecalho = cabecalho.substring(1);

            List<String> colunas = analisarLinha(cabecalho, 1);
            if (colunas.size() < 2) throw new IllegalArgumentException("O CSV precisa de ao menos um atributo e da classe na última coluna.");
            if (colunas.stream().anyMatch(String::isBlank) || new HashSet<>(colunas).size() != colunas.size()) {
                throw new IllegalArgumentException("O cabeçalho deve ter nomes preenchidos e distintos.");
            }

            List<String> atributos = List.copyOf(colunas.subList(0, colunas.size() - 1));
            List<Amostra> amostras = new ArrayList<>();
            TreeSet<String> classes = new TreeSet<>();

            String linha;
            int numeroLinha = 1;
            while ((linha = leitor.readLine()) != null) {
                numeroLinha++;
                if (linha.isBlank()) continue;
                List<String> celulas = analisarLinha(linha, numeroLinha);
                if (celulas.size() != colunas.size()) throw new IllegalArgumentException("Linha " + numeroLinha + ": esperado " + colunas.size() + " colunas; encontrado " + celulas.size() + ".");

                double[] valores = new double[atributos.size()];
                for (int indice = 0; indice < valores.length; indice++) {
                    try {
                        valores[indice] = Double.parseDouble(celulas.get(indice));
                        if (!Double.isFinite(valores[indice])) throw new NumberFormatException();
                    } catch (NumberFormatException erro) {
                        throw new IllegalArgumentException("Linha " + numeroLinha + ", coluna " + atributos.get(indice) + ": informe um número finito usando ponto decimal.");
                    }
                }

                String classe = celulas.get(celulas.size() - 1).strip();
                if (classe.isEmpty()) throw new IllegalArgumentException("Linha " + numeroLinha + ": a classe está vazia.");
                classes.add(classe);
                amostras.add(new Amostra(valores, classe));
            }

            if (amostras.isEmpty()) throw new IllegalArgumentException("O CSV contém cabeçalho, mas nenhuma amostra.");
            return new ConjuntoDados(atributos, amostras, List.copyOf(classes));
        }
    }

    private static List<String> analisarLinha(String linha, int numeroLinha) {
        List<String> celulas = new ArrayList<>();
        StringBuilder conteudo = new StringBuilder();
        boolean dentroDeAspas = false;
        boolean aspasFechadas = false;
        for (int indice = 0; indice < linha.length(); indice++) {
            char caractere = linha.charAt(indice);
            if (dentroDeAspas) {
                if (caractere == '"') {
                    if (indice + 1 < linha.length() && linha.charAt(indice + 1) == '"') { conteudo.append('"'); indice++; }
                    else { dentroDeAspas = false; aspasFechadas = true; }
                } else conteudo.append(caractere);
            } else if (caractere == ',') {
                celulas.add(conteudo.toString().strip()); conteudo.setLength(0); aspasFechadas = false;
            } else if (caractere == '"') {
                if (!conteudo.toString().isBlank() || aspasFechadas) throw new IllegalArgumentException("Aspas inválidas na linha " + numeroLinha + ".");
                conteudo.setLength(0); dentroDeAspas = true;
            } else {
                if (aspasFechadas && !Character.isWhitespace(caractere)) throw new IllegalArgumentException("Conteúdo após aspas na linha " + numeroLinha + ".");
                conteudo.append(caractere);
            }
        }
        if (dentroDeAspas) throw new IllegalArgumentException("Aspas não fechadas na linha " + numeroLinha + ". Campos com quebra de linha não são aceitos.");
        celulas.add(conteudo.toString().strip());
        return celulas;
    }
}
