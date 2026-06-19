<p align="center">
	<img width="200" alt="JavaFx Logo" src="https://i.imgur.com/vSQOa0U.png">
</p>

# macGyverDb

Motor de banco de dados construído do zero em Java, com foco em performance de baixo nível e controle direto sobre memória e disco. Por agora, estou somente acompanhando o tutorial de referência disponível ao final deste README. 

Este repositório tem como objetivo principal a criação de uma tese para meu futuro TCC em especialização de análise dados no Instituto Federal de Minas Gerais. A ideia central é utilizar os dados registrados neste banco criado do zero para inserção e análise de dados. 

Futuramente, será criado um Notion como base de conhecimento da aplicaçãoe e futura tese. Existem diversos conceitos da ciência da computação que deve ser trabalhados em banco de dados, como Write-ahead logging (WAL).
 
A base inicial deste projeto será creditada ao usuário Wesley00s e seu projeto - [nullDb](https://github.com/NullPointer-Labs/nulldb)

## Arquitetura

O projeto implementa as camadas de armazenamento, memória, rede e recuperação de um banco de dados relacional a partir dos primitivos mais básicos, evitando abstrações do Java que introduziriam overhead desnecessário.

```
App
 ├── storage/
 │    ├── DiskManager     ← ponte entre memória e sistema de arquivos
 │    └── Tuple           ← registro de tamanho fixo (64 bytes)
 ├── memory/
 │    ├── Page            ← bloco físico de memória (4096 bytes)
 │    └── LruReplacer     ← política de evicção de páginas (LRU)
 ├── network/
 │    └── OpCode          ← constantes do protocolo binário de rede
 └── recovery/
      ├── WalManager      ← gerenciador de Write-Ahead Log (WAL)
      └── LogRecord       ← estrutura de um registro no WAL
```

### `memory/Page`

Representa um bloco de memória de **4096 bytes**, alinhado ao controlador SSD. Usa `ByteBuffer.allocateDirect()` para alocar memória fora do heap da JVM — invisível ao garbage collector, zerando pauses de GC em operações de I/O intensas.

Cada página carrega uma **dirty flag**: quando qualquer bit é alterado, a flag é ativada e o motor sabe que precisa persistir a página em disco antes de liberá-la da RAM.

### `memory/LruReplacer`

Implementa a política de evicção **Least Recently Used (LRU)** para o buffer pool. Controla quais frames de memória estão disponíveis para substituição, impedindo que o OOM Killer do kernel Linux mate o processo do banco.

Usa `LinkedHashSet<Integer>` como estrutura central: mantém a **ordem cronológica de acesso** e garante busca em O(1), evitando gargalos de CPU durante picos de tráfego.

- **`pin(frameId)`** — trava um frame enquanto uma transação o utiliza, bloqueando sua evicção.
- **`unpin(frameId)`** — libera o frame ao final da transação, recolocando-o no final da fila LRU.
- **`evict()`** — remove e retorna o frame menos recentemente usado (cabeça da fila) para ser escovado ao disco.

### `storage/Tuple`

Registro de tamanho fixo com layout previsível em disco:

| Campo       | Tipo    | Tamanho |
|-------------|---------|---------|
| `id`        | `long`  | 8 bytes |
| `timestamp` | `long`  | 8 bytes |
| `payload`   | `byte[]`| 48 bytes|
| **Total**   |         | **64 bytes** |

O payload usa `byte[]` de tamanho fixo em vez de `String` para garantir alinhamento constante em disco. Textos maiores que 48 bytes são truncados; textos menores são lidos com `trim()` para descartar bytes nulos.

Os métodos `serialize` / `deserialize` manipulam diretamente um `ByteBuffer` via ponteiro de offset — sem JSON, sem reflexão, sem alocações intermediárias.

### `storage/DiskManager`

Ponte entre as páginas em RAM e o arquivo `.db` no sistema de arquivos. Detalhes críticos de implementação:

- **`FileChannel`** no lugar de `FileInputStream`/`FileOutputStream`: permite acesso posicional bruto, eliminando cópias extras no heap.
- **`AtomicInteger`** para o ID da próxima página: usa a instrução CAS (Compare-and-Swap) do hardware para garantir atomicidade na alocação de páginas em cenários multi-thread, sem locks.
- **`readPage(pageId, page)`** — leitura posicional via offset calculado (`pageId × 4096`). Usa `flip()` no `ByteBuffer` após a leitura para rebobinar o cursor ao byte zero, deixando a página pronta para consumo.
- **`writePage(page)`** — escrita posicional com `rewind()` seguido de `fileChannel.force(false)`, que força uma chamada `fsync` ao SO — garantindo que os bytes saiam da RAM do kernel e cheguem fisicamente ao SSD antes de retornar.
- **`allocatePage()`** — aloca uma nova página vazia no final do arquivo via `getAndIncrement()`.
- **`shutDown()`** — fecha o `FileChannel` corretamente, evitando descriptors vazando no OS.

### `network/OpCode`

Define as constantes do **protocolo binário cru** de comunicação. Um único `byte` determina a operação a executar — sem enum (que geraria objetos e lixo para o GC) e sem parsing de texto.

| Constante    | Valor  | Operação       |
|--------------|--------|----------------|
| `INSERT`     | `0x01` | Inserir tupla  |
| `SELECT`     | `0x02` | Buscar por ID  |
| `DELETE`     | `0x03` | Deletar tupla  |
| `UPDATE`     | `0x04` | Atualizar tupla|
| `SELECT_ALL` | `0x05` | Listar tudo    |

### `recovery/WalManager`

Implementa o **Write-Ahead Log (WAL)** — o mecanismo que garante durabilidade e recuperação após falhas (quedas de energia, crashes). A regra é simples: nenhuma alteração pode ir ao disco de dados antes de seu registro de log ser persistido.

Cada operação chama `append()`, que serializa um `LogRecord` em um `ByteBuffer` direto e escreve no arquivo `.wal` via `FileChannel` em modo `APPEND`. O método `flush()` dispara `fileChannel.force(true)` (fsync com metadados) para garantir a durabilidade do log.

### `recovery/LogRecord`

Estrutura de um registro no WAL:

| Campo     | Tipo     | Descrição                          |
|-----------|----------|------------------------------------|
| `lsn`     | `long`   | Log Sequence Number — ID único e monotônico do registro |
| `opCode`  | `byte`   | Operação executada (ver `OpCode`)  |
| `pageId`  | `int`    | Página afetada                     |
| `payload` | `byte[]` | Dados da tupla envolvida           |

## Requisitos

- Java 21
- Gradle 9.2+

## Build e execução

```bash
# Build
./gradlew build

# Executar
./gradlew run

# Testes
./gradlew test
```

## Stack

- **Java 21**
- **Gradle** com Kotlin DSL
- **JUnit Jupiter** para testes
- **Guava** como utilitário

## Referências
- [Construí um Banco de Dados Sem Usar NENHUM Framework](https://www.youtube.com/watch?v=qH5EBKhxMBw)
