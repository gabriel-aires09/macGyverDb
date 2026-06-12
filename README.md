# macGyverDb

Motor de banco de dados construído do zero em Java, com foco em performance de baixo nível e controle direto sobre memória e disco.

## Arquitetura

O projeto implementa a camada de armazenamento de um banco de dados relacional a partir dos primitivos mais básicos, evitando abstrações do Java que introduziriam overhead desnecessário.

```
App
 └── DiskManager          ← ponte entre memória e sistema de arquivos
      ├── Page            ← bloco físico de memória (4096 bytes)
      └── Tuple           ← registro de tamanho fixo (64 bytes)
```

### `memory/Page`

Representa um bloco de memória de **4096 bytes**, alinhado ao controlador SSD. Usa `ByteBuffer.allocateDirect()` para alocar memória fora do heap da JVM — invisível ao garbage collector, zerando pauses de GC em operações de I/O intensas.

Cada página carrega uma **dirty flag**: quando qualquer bit é alterado, a flag é ativada e o motor sabe que precisa persistir a página em disco antes de liberá-la da RAM.

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

Ponte entre as páginas em RAM e o arquivo `.db` no sistema de arquivos. Dois detalhes críticos de implementação:

- **`FileChannel`** no lugar de `FileInputStream`/`FileOutputStream`: permite mapeamento direto de memória e acesso posicional bruto, eliminando cópias extras no heap.
- **`AtomicInteger`** para o ID da próxima página: usa a instrução CAS (Compare-and-Swap) do hardware para garantir atomicidade na alocação de páginas em cenários multi-thread, sem locks.

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
