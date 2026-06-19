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
     *    reservar os blocos na memórira na hora que o banco sobe, impendido quem
     *    o redimensionamento da estrutura no meio de um pico de tráfico de rede
     */
    public LruReplacer(int capacity) {
        this.unpinnedFrames = new LinkedHashSet<>(capacity);
    }

    /*
     *    O método pin é uma trava de segurança. Quando uma transação começa a ler
     *    ou alterar uma página na memória, chamamos o pin e removemos esse frame
     *    na fila do porteiro.
     */ 
    public void pin(int frameId) {
        unpinnedFrames.remove(frameId);
    }

    public void unpin(int frameId) {
        unpinnedFrames.add(frameId);
    }

    public Integer evict() {
        if (unpinnedFrames.isEmpty()) {
            return null;
        }
        int victimFrame = unpinnedFrames.getFirst();
        unpinnedFrames.remove(victimFrame);
        return victimFrame;
    }

}
