package memory;
import java.util.LinkedHashSet;

// 23:43

/*
 *    LruReplacer tem como objetivo principal evitar que o kernel do Linux
 *    matem o nosso banco com OOM Killer
 */

public class LruReplacer {
    /*
     *    Estrutura de dados principal e Coração da classe. 
     *    Sua função principal é guardar números inteiros
     *    Chamamos de unpinnedFrames ou frames destravados.
     *
     *    Não podemos utilizar uma lista comum, porque precisamos manter a ordem
     *    cronológica exata de quem entrou na fila e ter velocidade de busca
     *    instantânea para não gargalar a CPU.
     */ 
    private final LinkedHashSet<Integer> unpinnedFrames;

    /*
     *    No construtor, recebemos a capacidade máxima de busca instântanea para
     *    não gargalar a CPU. Recebemos a capacidade máxima de páginas e já 
     *    passamos isto para a estrutura LinkedHashSet. Isso avisa a JVM para 
     *    reservar os blocos na memórira na hora que o banco sobe, impendido que perca
     *    tempo realizando o redimensionamento da estrutura no meio de um pico de 
     *    tráfico de rede
     */
    public LruReplacer(int capacity) {
        this.unpinnedFrames = new LinkedHashSet<>(capacity);
    }

    /*
     *    O método pin é uma trava de segurança. Quando uma transação começa a ler
     *    ou alterar uma página na memória, chamamos o pin e removemos esse frame
     *    na fila do porteiro. Motivo: blinda a página, garantindo que o algoritmo
     *    não vá ejetar o dados para o disco enquanto o thread estiver mexendo nele
     */ 
    public void pin(int frameId) {
        unpinnedFrames.remove(frameId);
    }

    /*
     *    Terminou a transação, a thread fez o que tinha de fazer, é a hora de chamar
     *    o método unpin. Sem isso, a página fica travada na memória para sempre.
     *
     *    O frame não será apagado. Ele entra novamente no extremo final da fila
     *    do LinkedHashSet. Isso manda um recado claro para o gerente de memória. Ei,
     *    eu acabei de ler e usar essa página agora.
     *
     *    Caso a quantidade de memória estourar, ela está liberada para ser ejetada destravado
     *    volta para o metal, sem corromper o banco. 
     */
    public void unpin(int frameId) {
        unpinnedFrames.add(frameId);
    }


    /*
     *    Método de execução. Quando o método perceber que o 1GB de RAM lotou
     *    ele invoca este método em desespero. Se não liberarmos espaço, o kernel
     *    do Linux mata o processo. Checamos se a fila está vazia, se não estiver,
     *    invocamos o getFirst.
     */
    public Integer evict() {
        if (unpinnedFrames.isEmpty()) {
            return null;
        }
        /*
         *    Least Recently Used, batizado de frame vítima. Arrancamos ele da fila
         *    sem dó e o devolvemos. Ele será escovado para o disco do linux para
         *    abrir espaço imediato para novas transações.
         */
        int victimFrame = unpinnedFrames.getFirst();
        unpinnedFrames.remove(victimFrame);
        return victimFrame;
    }

}
