# Laboratório MLP — Backpropagation em Java

Aplicação desktop para o trabalho descrito em [TrabalhoIA2BimMLPBackpropagation.pdf](TrabalhoIA2BimMLPBackpropagation.pdf). Implementa uma MLP com uma camada oculta, treinamento online, acompanhamento ao vivo com **JavaFX Charts** e avaliação em dados de teste. Todo o algoritmo de aprendizagem foi escrito em Java, sem bibliotecas de IA.

## Compilar e executar

Requer **JDK 21 ou superior**, com java e javac no PATH. O projeto usa Java 21 como versão de compilação e foi validado neste ambiente com JDK 24.0.1. O Maven Wrapper incluído baixa o Maven 3.9.9 e as dependências na primeira execução; é necessário acesso à internet nessa primeira vez.

Na pasta do projeto, pelo PowerShell:

~~~powershell
.\mvnw.cmd javafx:run
~~~

Também é possível abrir iniciar.cmd com duplo clique. No Linux/macOS:

~~~sh
sh mvnw javafx:run
~~~

Para compilar, empacotar o JAR e executar os testes:

~~~powershell
.\mvnw.cmd package
~~~

O JAR em target não inclui as bibliotecas JavaFX; execute a interface pelo Wrapper ou pela IDE com o pom.xml importado como projeto Maven. Não é necessário instalar o SDK JavaFX separadamente.

## Como usar

1. Na aba **Dados**, escolha dois CSVs ou um único CSV com percentual de treino. Os caminhos iniciais apontam para os arquivos de entradas/. Informe a semente e clique em **Carregar e preparar dados**.
2. Confira a tabela de treino ou teste, com valores brutos ou normalizados e saída desejada one-hot.
3. Na aba **Treinamento**, escolha a regra da camada oculta, a transferência, a taxa, o máximo de épocas, o erro mínimo e os parâmetros do platô. Clique em **Iniciar treinamento**.
4. Acompanhe época, erro médio, taxa atual, tempo decorrido, pesos das conexões e curva de erro. Os botões **Pausar**, **Continuar** e **Interromper** agem ao concluir a época atual.
5. Ao detectar platô, escolha interromper, manter a taxa ou reduzi-la em 10%. A janela é reiniciada após continuar e a decisão será oferecida novamente se a estagnação persistir.
6. Clique em **Avaliar na base de teste**. A matriz mostra classes reais nas linhas e previstas nas colunas, com diagonal destacada, totais, acurácia, precisão e revocação.
7. Exporte o histórico em CSV, a curva em PNG e a matriz em CSV pelos botões da interface.

O tempo decorrido inclui as pausas. O histórico completo é preservado; curvas com mais de 3.000 pontos são amostradas apenas na exibição. As conexões recebem snapshots dos pesos durante o treino e os pesos finais ao encerrar. O log registra ocorrências de platô e reduções de taxa.

A semente usada na divisão fica registrada no resumo dos dados. Alterar a semente depois de preparar os dados muda a inicialização e o embaralhamento do próximo treino; para refazer a divisão, carregue e prepare os dados novamente.

## Formato dos CSVs

Codificação UTF-8, cabeçalho obrigatório e campos separados por vírgula. A última coluna contém a classe; todas as anteriores contêm números finitos. Use ponto como separador decimal nos arquivos.

~~~csv
X1,X2,classe
1,19,CA
4,22,CB
-2,16,CA
~~~

A quantidade de entradas e classes é detectada automaticamente. Espaços ao redor dos campos e rótulos são removidos; BOM UTF-8 e campos entre aspas são aceitos. Campos vazios, números inválidos, linhas com quantidade incorreta de colunas e classes de teste ausentes no treino geram mensagens explicativas. Dois arquivos devem ter os mesmos atributos na mesma ordem. Para dividir uma base, a ferramenta embaralha os exemplos e garante pelo menos um exemplo de cada classe no treino; rejeita percentuais que impossibilitem isso ou deixem o teste vazio.

A base fornecida contém 827 exemplos de treino, 352 de teste, seis atributos e cinco classes: CA, CB, CC, CD e CE.

## Requisitos atendidos

| Requisito do PDF | Implementação |
|---|---|
| RF01 — carregar dados | Leitor CSV com detecção de atributos/classes e tabela de prévia |
| RF02 — definir treino/teste | Dois arquivos ou divisão percentual embaralhada e reprodutível |
| RF03 — normalizar | Min-max ajustado exclusivamente ao treino, com tratamento de coluna constante |
| RF04 — arquitetura | Oculta pela média aritmética, geométrica ou valor manual |
| RF05 — transferência | Linear, logística e tangente hiperbólica, com as derivadas do PDF |
| RF06 — taxa | Validação de 0 < η ≤ 1 e mensagem amigável |
| RF07 — parada | Primeiro critério atingido: erro mínimo ou máximo de épocas; motivo exibido |
| RF08 — backpropagation | Inicialização aleatória pequena, bias e atualização a cada exemplo |
| RF09 — acompanhamento | Indicadores, curva JavaFX em tempo real, pausa, retomada e interrupção |
| RF10 — desenho | Neurônios/conexões, pesos no hover, cor por sinal e espessura por magnitude |
| RF11 — platô | Desvio padrão de janela ≥ 10, decisão do usuário e log |
| RF12 — teste | Argmax, matriz real × prevista e acurácia no conjunto de teste |

Extras implementados: precisão/revocação por classe, exportações CSV/PNG e semente fixa.

## Decisões de implementação

- **Normalização:** (x − mínimo)/(máximo − mínimo), com extremos calculados somente no treino e guardados em MinMaxNormalizer. Colunas constantes viram zero. Valores de teste fora dos extremos são limitados a [0, 1], sem recalcular a escala.
- **Saída:** classes ordenadas lexicograficamente a partir do treino, alvos one-hot 0/1 para todas as ativações e classificação por maior saída. Empates favorecem a primeira classe.
- **Oculta:** Math.round da média escolhida, com mínimo de um neurônio. Para seis entradas e cinco saídas, a aritmética resulta em seis e a geométrica em cinco.
- **Pesos/bias:** distribuição uniforme em [−0,1; 0,1), com java.util.Random e semente configurável. O bias é uma conexão cuja entrada vale um, em ambas as camadas.
- **Transferências:** linear f(net)=net/10 e derivada 1/10; logística e derivada i·(1−i); tanh e derivada 1−i². A mesma função é usada na camada oculta e na saída; a logística evita overflow e a tanh usa Math.tanh.
- **Gradientes:** todos os deltas ocultos são calculados com os pesos da propagação original, antes de atualizar os pesos de saída. A atualização é w ← w + η·delta·saídaOrigem.
- **Erro por época:** média de E=½Σ(desejado−saída)², calculado antes da atualização de cada exemplo. Esse mesmo erro online alimenta a curva, a parada e o detector de platô.
- **Ordem:** embaralhamento a cada época. Um Random separado, com seed XOR 0x5DEECE66D, controla a ordem sem depender do número de pesos sorteados.
- **Platô:** desvio padrão populacional da janela completa de erros. Por padrão, janela 10 e limiar 0,00001. Ao continuar, apenas a janela é reiniciada; pesos e época são preservados. Redução: η ← 0,9η.
- **Interface:** JavaFX Task executa o treino fora da thread gráfica. A UI recebe lotes de épocas e cópias dos pesos, evitando leitura concorrente das matrizes em alteração.

## Organização e explicação do código

| Arquivo/pacote | Responsabilidade |
|---|---|
| src/main/java/br/edu/mlp/core/CsvReader.java | Leitura e validação do CSV |
| core/DataPreparation.java e MinMaxNormalizer.java | Divisão, codificação e normalização sem vazamento |
| core/Activation.java | Funções e derivadas |
| core/Network.java | Forward, deltas, atualização online e pesos |
| core/PlateauDetector.java | Janela e desvio padrão |
| core/Evaluation.java | Matriz de confusão, acurácia, precisão e revocação |
| ui/TrainingTask.java | Laço de treino, parada, pausa e decisão de platô |
| ui/MlpApplication.java e NetworkView.java | Interface, JavaFX Charts e desenho da rede |
| cli/ExperimentRunner.java | Três experimentos reprodutíveis e relatório |
| cli/ExperimentCharts.java | Exportação dos gráficos dos experimentos |

Para a arguição, siga o percurso: leitura → divisão → normalização → one-hot → inicialização → forward → deltas de saída → deltas ocultos → atualização → erro médio → parada/platô → argmax e avaliação.

## Experimentos entregues

O [relatório](docs/RELATORIO_EXPERIMENTOS.md) e sua versão [PDF](docs/RELATORIO_EXPERIMENTOS.pdf) incluem três treinos reais, curvas de erro e matrizes de confusão. Dados completos em experimentos/.

| Configuração | Épocas até erro ≤ 0,005 | Erro final | Acurácia no teste |
|---|---:|---:|---:|
| Logística, seis ocultos, η=0,3 | 15 | 0,00450694 | 100% (352/352) |
| Logística, 12 ocultos, η=0,1 | 36 | 0,00488990 | 100% (352/352) |
| Tanh, seis ocultos, η=0,05 | 63 | 0,00483498 | 100% (352/352) |

Todos usam seed 42 e máximo de 2.000 épocas. O empate nessa base não demonstra superioridade geral de uma configuração; o relatório discute os limites da comparação.

Para recalcular os experimentos:

~~~powershell
.\mvnw.cmd compile exec:java '-Dexec.mainClass=br.edu.mlp.cli.ExperimentRunner'
~~~

Para regenerar os PNGs com JavaFX Charts:

~~~powershell
.\mvnw.cmd compile exec:java '-Dexec.mainClass=br.edu.mlp.cli.ChartsLauncher'
~~~

## Bibliotecas de apoio e validação

- OpenJFX 21.0.10: controles, LineChart/BarChart e interoperabilidade de imagens para exportar PNG.
- JUnit Jupiter 5.11.4: usado somente nos testes.
- Biblioteca padrão do Java: CSV, matrizes, aleatoriedade, operações matemáticas e arquivos.

Nenhuma dependência implementa IA ou aprendizado de máquina. A configuração do JavaFX segue a [documentação oficial do OpenJFX](https://openjfx.io/openjfx-docs/); o [Maven Wrapper](https://maven.apache.org/tools/wrapper/) fixa a ferramenta de build.

Os testes verificam as ativações do PDF, todos os gradientes por diferenças finitas, aprendizado de XOR, repetibilidade, normalização sem vazamento, colunas constantes, CSV, divisão, matriz e platô. Para a verificação gráfica automática em ambiente com tela:

~~~powershell
.\mvnw.cmd javafx:run '-Djavafx.args=--smoke-test'
.\mvnw.cmd javafx:run '-Djavafx.args=--smoke-plateau'
~~~

As execuções geram capturas em target/screenshots/ e registros target/ui-smoke*.txt. O segundo modo força platôs para verificar reapresentação e reduções sucessivas de 10%.

A verificação dos controles (pausa, retomada, platô e interrupção) também pode ser executada separadamente:

~~~powershell
.\mvnw.cmd test-compile exec:java '-Dexec.mainClass=br.edu.mlp.ui.TrainingControlCheck' '-Dexec.classpathScope=test'
~~~

## Entrega

O arquivo entrega/TrabalhoIA-MLP.zip reúne os fontes, testes, Maven Wrapper, README, relatório, experimentos, CSVs de entrada e o enunciado. As pastas target/ e .git/ não fazem parte do pacote. A demonstração e a explicação oral devem ser realizadas pelos integrantes da equipe.

Para recriar o ZIP após alterações: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/empacotar.ps1`.
