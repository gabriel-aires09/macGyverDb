package main.java.storage;
import java.nio.ByteBuffer;

public class Tuple {
    public static final int TUPLE_SIZE = 64; // tamanho da linha
    public static final int PAYLOAD_SIZE = 48; // tamanho da coluna de texto

    /*
        Atributos da tupla
        8 bytes para o id e 8 bytes para o timestamp
    */
    private long id; 
    private long timestamp;

    /*  
        Para o texto, um array de bytes de payload. A String em java é um objeto pesado
        e de tamanho variável. Com array de bytes garantimos um tamanho fixo de 48 bytes em disco.
    */ 
    private final byte[] payload; 

    
    /*  
        No construtor, nós recebemos o id, o timestamp e o texto do usuário.
        Instanciamos o array de bytes com tamanho fixo de 48.  
     */
    public Tuple(long id, long timestamp, String payloadStr){
        this.id = id;
        this.timestamp = timestamp;
        this.payload = new byte[PAYLOAD_SIZE];

        byte[] stringBytes = payloadStr.getBytes();
        
        /*  
            Se o usuário mandar mais de 100 caracteres, é necessário o uso do Math.Min()
            Calcula-se o comprimento, pegamos o que for menor entre o tamanho da string 
            passada e o nosso limite de 48. Se passar, a gente simplesmente ignora o resto (trucamento)
         */
        int lengthToCopy = Math.min(stringBytes.length, PAYLOAD_SIZE);

        /* 
            Para fugir de loops, usamos o System.arraycopy. Sua função é pegar o bloco
            bytes de origem e injenta diretamente no array de destino 
        */
        System.arraycopy(stringBytes, 0, this.payload, 0, lenghtToCopy);
    }

    /*  
        O trabalho deste construtor é inicializar o new byte[PAYLOAD_SIZE]
        Motivo: quando o banco de dados ler uma página direta do disco rígido, 
        não teremos id, timestamp e textos prontos. Teremos somente uma corrente 
        de bytes chegando do OS.
        Com isso, passamos para o JVM o seguinte: reserva o espaço exato de 48 bytes na RAM
     */
    public Tuple() {
        this.payload = new byte[PAYLOAD_SIZE];

    }

    /*
        Este método recebe um bytebuffer e offset.
        Não estão transformando objetos em json e sim manipulando a memória física
        Offset é o nosso ponteiro
     */
    public void serialize(ByteBuffer buffer, int offset){
        buffer.position(offset); // Avisamos ao buffer para posicionar a agulha neste exato offset. Depois, descarregue os bytes
        buffer.putLong(id);
        buffer.putLong(timestamp);
        buffer.put(payload); // colocando os 48 bytes diretos no buffer

    }

    /*
        Faz o caminho inverso. Dizemos onde o ponteio começa, mandamos ele ler do buffer e preencher nossas variáveis locais 
        O buffer avança automaticamente a cada leitura (matemática de ponteiros)
    */
    public void deserialize(ByteBuffer buffer, int offset){
        buffer.position(offset);
        this.id = buffer.getLong();
        this.timestamp = buffer.getLong();
        buffer.get(this.payload);
    }

    public long getId() {
        return id;
    }

    /*
        O detalhe mais importante está neste getter. Como o array tem 48 bytes fixos, se a pessoa
        salvou o nome Bob, sobraram 45 bytes nulos. Na hora de retornar, nós recriamos a string e chamamos
        um trim()
     */
    public String getPayloadAsString(){
        return new String(payload).trim(); // trim() limpa o byte e sujeira das pontas, devolvendo o texto perfeitamente para o cliente

    }
}
