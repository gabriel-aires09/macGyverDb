package main.java.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import main.java.memory.Page;

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
    
    public DiskManager(string dbFile){
        try {
            // Recebe o nome do nosso arquivo de banco de dados
            Path path = Paths.get(dbFile);
            
            /* 
                Depois, pedimos para o OS abrir esse canal. Passamos três opções estritas de abertura
                Passamos três opções restritas de abertura: CREATE, READ e WRITE.
                Nada de passar restrição de arquivos de leitura. Precisamos que nosso motor db tenha acesso total.
            */
            this.fileChannel = FileChannel.open(
                path,
                StandardOpenOption.CREATE, // Para criar o arquivo do zero, caso seja o primeiro init do db
                StandardOpenOption.READ,
                StandardOpenOption.WRITE 
            );
            this.nextPageId = new AtomicInteger((int) (fileChannel.size() / Page.PAGE_SIZE));
            logger.info("DiskManager initialized. Target file: " + dbFile);
        } catch (IOException e){
            throw new RuntimeException("System halt: Failed to open DB File.", e);
        }

    }

}
