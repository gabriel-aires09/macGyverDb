package memory;

import java.nio.ByteBuffer;

/*
    A classe é a representação física de um bloco de memória do nosso banco de dados. 
 */
public class Page {
    /* 
        O alinhamento do controlador SSD será traduzido pela constante PAGE_SIZE, com 4096 bytes. 
        Todo o tráfego deve respeitar este limite
    */
    public static final int PAGE_SIZE = 4096;
    private int pageId; // Para o banco saber exatamente qual página ele está lendo
    private final ByteBuffer data; // Caixa vazia onde nossos bytes irão morar
    // flag da sujeira. Se houver uma mudança de bit, flag ativa e força o salvamento do disco antes de limpar a RAM
    private boolean isDirt;
    
    
    // Nós executamos a "fuga do garbage collector". Não é instanciado um new array de bytes comum.
    public Page(int pageId){
        this.pageId = pageId;
        /* 
            Nós invocamos o ByteBuffer.allocateDirect(), passando o tamanho da PAGE_SIZE.
            Esta linha isolada blinda a nossa perfomance. Allocate direct fura a bolha do heap do java,
            alocando um bloco contido de memória direto no OS.
            Os Gbs passados ficam 100% invisíveis para a lixeira da JVM. 
        */
        this.data = ByteBuffer.allocateDirect(PAGE_SIZE);
        // Quando a página nasce, o isDirt fica como falso. Motivo: acabou de ser gerado e não sofreu nenhuma mudança
        this.isDirt = false;
    }


    // Getters e setters padrão
    public static int getPageSize() {
        return PAGE_SIZE;
    }

    public int getPageId() {
        return pageId;
    }

    public void setPageId(int pageId) {
        this.pageId = pageId;
    }

    public ByteBuffer getData() {
        return data;
    }

    public boolean isDirt() {
        return isDirt;
    }

    public void setDirt(boolean isDirt) {
        this.isDirt = isDirt;
    }
    
}
