package network;

/*
 *  Será utilizado protocolo binário cru. Um único byte vai ditar
 *  o destino da arquitetura
 *  
 *  Não será utilizado enum, porque é objeto. E objeto é considerado lixo
 *  na JVM limpar depois. 
 *
 *  O socket gera um byte e o sistema ler um byte. Motivo: não gera sobrecarga
 *  na memória. Aqui, os dados não serão parseados os dados com frameworks. A 
 *  máquina recebe um byte da rede já sabe qual circuito deve acionar
*/
public class OpCode {
    // Constante INSERT como primitivo byte 0x01 hexadecimal e assim sucessivamente
    public static final byte INSERT = 0x01;
    public static final byte SELECT = 0x02;
    public static final  byte DELETE = 0x03;
    public static final byte UPDATE = 0x04;
    public static final byte SELECT_ALL = 0x05;
}
