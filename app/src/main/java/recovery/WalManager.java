package recovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/*
 *    Esta classe serve para proteger a durabilidade do nosso banco. Ela tem uma semelhança
 *    enorme com a classe DiskManager. Ambas conversam diretamente com o sistema de arquivos do Linux
 *
 *    Analogia: DiskManager - bibliotecário que precisa colocar o livro numa estante específica (posições exatas)
 *    WAL Manager - Funciona como uma impressora de cupom fiscal. Sabe somente escrever para frente, adicionando 
 *    dados ao final da fila.
 */

public class WalManager {
    //  Declara o logger para manter o rastreio visual.   
    private static final Logger logger = Logger.getLogger(WalManager.class.getName());

    //  Declara o FileChannel como logChannel. Duto de alta velocidade do Java.
    private final FileChannel logChannel;

    /*  
     *  Este será nosso gerador de senhas do banco. Precisamos que cada transação receba um número de sequência único, 
     *  sem repetir. O AtomicInteger faz isso na camada do hardware, sem travar com processos de sincronizações.  
     */
    private final AtomicInteger currentLsn;
 
    /*
     *  Chamamos um ByteBuffer chamado walBuffer. Nós não vamos criar um buffer novo para cada operação.
     *  Nós vamos reciclar este mesmo espaço.
     */
    private final ByteBuffer walBuffer;

    public WalManager(String logFile) {
        
        try {
            Path path = Paths.get(logFile); // serve para mapear o caminho 
            /*
             *  O FileChannel.open() serve para abrir o canal de arquivo 
             *
             *  Nós passamos as seguintes opções no FileChannel:
             *  
             *  CREATE - serve para o OS criar o arquivo se ele não existir
             *  
             *  WRITE - garantir a permissão de escrita.
             *  
             *  APPEND - mudar completamente o comportamento do SSD e HD.
             *  Com esta opção, nosso banco não perde tempo procurando espaços
             *  vazios no meio do arquivo. O sistema operacional já sabe que todo
             *  byte que chegar deve ser inserido exatamente no final do arquivo. 
             *  Este modo garante que a agulha virtual do sistema não precise saltar
             *  de posição
             */
            this.logChannel = FileChannel.open(path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);

            /*
             *  Inicialiamos nosso currentLsn em zero. Significando que estamos na 
             *  primeiro operação.
             */
            this.currentLsn = new AtomicLong(0);
            
            /*
             *  Instanciamos nosso walBuffer chamando o allocateDirect com 
             *  4096 bytes de tamanho. Alocamos isso fora da lixeira do Java,
             *  para garantir a melhor vazão possível. 
             */
            this.walBuffer = ByteBuffer.allocateDirect(4096);

            /*  
            *   Mensagem de log, confirmando que o gerenciador subiu com sucesso.
            *   Se der erro, derrubamos o sistema
            */
            logger.info("WalManager initialized. Target file: " + logFile);
        } catch (IOException e) {
            throw new RuntimeException("System halt: Failed to open WAL file.", e);
        }
    }

    /*    
     *    O método append() é encarregado do trabalho mais pesado. Uma observação
     *    importante é que este método recebe somente tipos primitivos.
     *
     *    1 byte para opCode;
     *    1 inteiro para o pageId;
     *    1 array de bytes para para o payload
     */
    public long append(byte opCode, int pageId, byte[] payload) {
        
        /* 
         *  Pegamos o número de sequência atual, chamando o get() do nosso AtomicInteger
         *  e guardamos este valor na variável LSN. 
         */
        long lsn = currentLsn.incrementAndGet();

        /*
         *  Pegamos o buffer alocado no início e chamamos o método clear(), limpando
         *  a área de trabalho e fazendo com que o buffer esteja pronto para receber dados. 
         */
        walBuffer.clear();

        /*
         *  Início do empilhamento das informações. Mandamos um putLong() utilizando a variável
         *  lsn, consumindo 8 bytes.
         */
        walBuffer.putLong(lsn);

        // Mandamos um put com opCode, consumindo mais um byte
        walBuffer.put(opCode);

        // Mandamos um putInt com o id da página, usando 4 bytes.
        walBuffer.putInt(pageId);

        /*  Antes de jogar o payload, a gente precisa dizer qual o tamanho dele. Por isso,
         *  payload.length ao final. Isso avisa para quem for ler o long no futuro quanto 
         *  bytes exatos ele precisa puxar para resgatar o texto
        */
        walBuffer.putInt(payload.length);]
        
        // Chamamos o put() para passar o array de payload inteiro
        walBuffer.put(payload);

        /*  
         *  O método flip() diz para buffer parar de funcionar no modo gravação e se
         *  preparar para ser lido pelo disco
         */
        walBuffer.flip();
        
        /*
         *  Criamos um bloco de try-catch com laço de repetição while.
         *  Enquanto o walBuffer estiver com os dados restantes usando o método hasRemaining()
         *  e dentro do laço, nós mandamos o logChannel escrever o conteúdo do buffer no arquivo físico
         *
         *  Motivo de usar esta estrutura: o sistema operacional tem seus próprios limites estruturais e
         *  buffers internos. O laço while vai insistir e continuar enviando os pedaços até que a última gota
         *  do nosso buffer tenha sido absolvida pelo sistema de arquivos. 
         */ 
        try {
            while (walBuffer.hasRemaining()) {
                int ignored = logChannel.write(walBuffer);
            }  
        } catch (IOException e) {
            throw new RuntimeException("WAL Write Error at LSN: " + lsn, e);
        }

        // Depois de tudo gravado, retornamos o número de sequências da transação
        return lsn;

    }


    /*
     *    Coração da durabilidade. Criamos o método flush() (descarregar). Quando o método append() termina de rodar,
     *    os dados não estão totalmente seguros. Linux guarda os dados que acabamos de escrever e passa para um espaço
     *    de memória volátil chamado PageCache. Se o computador desligar agora, o log desaparece e o banco corrompe.
     */
    public void flush() {
        try {
            /*
             *  Para que o banco não seja corrompido, utilizamos logChannel.force() com valor true.
             *  Essa linha faz uma chamada de sistema no Linux conhecida com fsync. Ela ignora os atalhos de memória do 
             *  sistema operacional.
             *
             *  A chamada force obriga os controladores do hardware a moverem os elétrons fisicamente para dentro das células
             *  de memória na volátil do seu SSD. Nós passamos o valor true porque queremos forçar a atualização
             *  dos metadados dos arquivos. Quando essa linha terminar de executar é que a nossa arquitetura tem a garantia
             *  absoluta de que a transação sobreviveu a qualquer energia.
             *
             *  Registramos isso no log como flush executado com sucesso e fechamos o bloco catch de segurança
             */
            logChannel.force(true);
            logger.info("WAL flushed to disk successfully.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to fsync WAL", e);
        }
    }
    
    /*
     *  O método shutDown serve para finalizar a classe. Todo componente
     *  que abre um arquivo no OS tem a obrigação de fechar e devolver os
     *  recursos ao sistema. 
     */
    public void shutDown() {
        try {
            /*
             *  Quando o banco for desligado, chamamos o método flush() por
             *  precaução. Isso garante que qualquer sujeira seja gravada no disco
             */
            flush();
            /*
             *  Método logChannel.close() serve para liberar o arquivo de log e registrar o encerramento
             *  no console
             */
            logChannel.close();
            logger.info("WalManager gracefully shut down.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to close WAL channel.", e);
        }
    }

}
