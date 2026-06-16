package storage;
import memory.Page;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;


//11:49
//O único trabalho aqui é ser a ponte entre a nossa memória e o sistema de arquivos do linux
public class DiskManager {
    // Abre a classe declarando nosso logger.
    private static final Logger logger = Logger.getLogger(DiskManager.class.getName());
    /*
        Detalhe: nós não estamos usando um file input string classíco - IO bloqueante e lento
        que cria lixo na HEAP do java.
        File channel permite o mapeamento direto de memória e acesso posicional bruto    
    */
    private final FileChannel fileChannel;
    /*
        Não utilizar int comum. Pode gerar uma race condition e corromper o disco do banco. Motivo disso ocorrer:
        um banco de dados pode ter dezenas de threads tentando alocar uma página nova no mesmo milissegundo.
        AtomicInteger usa uma instrução de hardware CPU CAS (Compare and Swap) que garante atomicidade sem gargalo
    */
    private final AtomicInteger nextPageId;  
    
    public DiskManager(String dbFile){
        try {
            // Recebe o nome do nosso arquivo de banco de dados
            Path path = Paths.get(dbFile);
            
            /* 
                Depois, pedimos para o OS abrir esse canal. Passamos três opções estritas de abertura
                Passamos três opções restritas de abertura: CREATE, READ e WRITE.
                Nada de abrir arquivos de leitura. Precisamos que nosso motor db tenha acesso total.
            */
            this.fileChannel = FileChannel.open(
                path,
                StandardOpenOption.CREATE, // Para criar o arquivo do zero, caso seja o primeiro init do db
                StandardOpenOption.READ,
                StandardOpenOption.WRITE 
            );
            /*
                Para saber a próxima página em branco que será gravada, pegamos o tamanho físico em bytes dados
                pelo fileChannel size e dividimos pelo PAGE_SIZE de 4096. Cálculo de uma linha que faz o recobrimento de estado
                do banco.
             */
            this.nextPageId = new AtomicInteger((int) (fileChannel.size() / Page.PAGE_SIZE));
            logger.info("DiskManager initialized. Target file: " + dbFile);
        } catch (IOException e){
            throw new RuntimeException("System halt: Failed to open DB File.", e);
        }
    }

    public void readPage(int pageId, Page page) {
        long offset = (long) pageId * Page.PAGE_SIZE;
        try {
            //    buffer de memória pode ter lixo de requisição antiga. Então, resetamos os seus ponteiros
            page.getData().clear();
            //    fileChannel vai buscar os bytes no disco e despejar no nosso buffer.
            int  bytesRead = fileChannel.read(page.getData(), offset);
            /*    Se o retorno for menos um, significa que tentamos ler além do fim do arquivo.
             *    Então somente será feita a limpeza do buffer novamente
            */ 
            if (bytesRead == -1){
                page.getData().clear();
            /*  Se ele leu com sucesso, nós utilizamos uma função importante: flip().
             *  Quando você escreve num buffer, o ponteiro da posição vai parar lá no final.
             *  
             *  Se você tentar ler a página em seguida, va ler o buffer vazio. O Flip joga
             *  o limite pro final dos dados e rebobina a posição por byte zero, deixando a página
             *  engatilhada e pronta para o nosso motor consumir. 
             *
             */ 
            } else {
                page.getData().flip();
            }
        } catch (IOException e) {
            throw new RuntimeException("I/O Error: Failed to read page " + pageId, e);
        }
    }
    
    /*    É o espelho exato da leitura. Calculamos o mesmo offset cravado, mas no buffer
     *    chamamos o rewind ao invés do flip(). Rewind é o equivalente ao rebobinar a fita cassete.
     *    Ele volta o cursor para o início sem alterar os limites do buffer, garantindo a varredura 
     *    e gravação dos 4096 bytes da página. 
     */
    public void writePage(Page page) {
        long offset = (long) page.getPageId() * Page.PAGE_SIZE;
        try {
            page.getData().rewind();
            int ignore = fileChannel.write(page.getData(), offset);
            /*
             *    Linha que diferencia projeto acadêmico de uma engine comercial. Kernel do linux é mentiroso por 
             *    padrão. Quando ele salva um arquivo, o linux salva na RAM. Só quando acha conveniente, ele salva 
             *    no disco rígido. Caso a luz acabe, o banco será corrompido.
             *    
             *    O método force(false) faz uma chamada de sistema (fsync) que obriga os eletrons a mudarem de estado
             *    dentro do ssd antes da linha de código terminar.
             */ 
            fileChannel.force(false); // Usa-se false para não forçar a atualização imediata dos metadados do arquivo
        } catch (IOException e) {
            throw new RuntimeException("I/O Error: Failed to write page " + page.getPageId(), e);
        }
    }

    /*
     *    Pega o nosso contador atômico e faz um get and increment
     *    O banco irá rodar em uma única página como prova de conceito
     *    Mundo real: quando sua b tree lotar e precisar fazer um page split, este método
     *    vai chamar para alocar blocos virgens no final do arquivo.
     */

    public int allocatePage() {
        int newPageId = nextPageId.getAndIncrement();
        logger.info("Allocated new physical page with ID: " + newPageId);
        return newPageId;
    }

    /*
     *    SGDB não gerencia o encerramento de arquivos, deixa os recursos travadas e descrição de arquivos
     *    vazando no OS.
     */

    public void shutDown() {
        try {
            fileChannel.close(); // garante que o fileChannel seja selado e fechado corretamente antes do processo morrer.
            logger.info("DiskManager gracefully shutdown");
        } catch (IOException e) {
            throw new RuntimeException("Failed to close file channel.", e);
        }
    }

}
