package com.company;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;

class Header{
    private  short id;
    private short flags;
    private  short [] counts;
    private static Random random = new Random();

    private void init(){
        counts = new short[4];
        flags = 0;
        id = (short)random.nextInt(32767);
    }
    public Header(){
        init();
    }
    static short setFlag(short flags,int bit,boolean value){
        if(value)
            return flags |= (1 << (15-bit));
        else
            return flags &= ~(1 << (15 - bit));
    }
    public void  setFlag(int bit){
        flags = setFlag(flags,bit,true);
    }
    public  void setCount(short field, short value) {
        counts[field] = value;
    }
    public byte[] toByteArray(){
        byte[] b = new byte[12];
        b[0] = (byte)(id >> 8);
        b[1] = (byte)(id);
        b[2] = (byte)(flags>>8);
        b[3] = (byte)(flags);
        int j = 4;
        for (int i = 0; i< 4;i++){
            b[j] = (byte)(counts[i]>> 8);
            b[j+1] = (byte)(counts[i]);
            j+=2;
        }
        return b;
    }
}
/*QUESTION DATA*/
class QUESTION {

    private short qtype;   /*query type:IN,NS,CNAME,SOA,PTR,MX*/
    private short qclass;  /*query class:IN or CHAOS*/
    public QUESTION(short qtype, short qclass) {
        this.qtype = qtype;
        this.qclass = qclass;
    }
    public byte[] toByteArray(){
        byte[] b = new byte[4];
        b[0] = (byte)(qtype >> 8);
        b[1] = (byte)(qtype);
        b[2] = (byte)(qclass >> 8);
        b[3] = (byte)(qclass);
        return b;
    }
}

class DNSInput{
    private ByteBuffer byteBuffer;
    public
    DNSInput(byte [] input) {
        byteBuffer = ByteBuffer.wrap(input);
    }
    public int
    readU16()  {
        return (byteBuffer.getShort() & 0xFFFF);
    }
    public void
    jump(int index) {
        byteBuffer.position(index);
        byteBuffer.limit(byteBuffer.capacity());
    }
    public int
    readU8()  {
        return (byteBuffer.get() & 0xFF);
    }
    public int
    current() {
        return byteBuffer.position();
    }

}

public  class Lookup {
    private  String dnsName;
    private String Cname;
    public Lookup(String name){
        this.dnsName = name;
        this.Cname = null;
    }
    public static final String dns_ip = "8.8.8.8";
    public static void main(String[] args) {
        Lookup lookup = new Lookup("www.baidu.com");
        lookup.Run();
        String cname = lookup.getCname();
        System.out.println(cname);
    }
    public String getCname(){
        return Cname;
    }

    public void Run(){
        int jump_length = dnsName.length()+2;
        byte[] msg = buildMessage(dnsName,(short)5);
        byte[] recvbyte = null;
        try {
            DatagramSocket client = new DatagramSocket();
            DatagramPacket data = new DatagramPacket(msg,msg.length,new InetSocketAddress(dns_ip,53));
            client.send(data);
            byte[]  recvMessage = new byte[100];
            DatagramPacket datagramPacket = new DatagramPacket(recvMessage,recvMessage.length);
            client.receive(datagramPacket);
            recvbyte = datagramPacket.getData();

        }catch (IOException e){
            e.printStackTrace();
        }
        DNSInput input = new DNSInput(recvbyte);
        input.jump(2);
        int flags = input.readU16();
        input.jump(6);
        int ansnum = input.readU16();
        if(ansnum ==0) {
            Cname = null;
            return;
        }
        int rcode = getRcode(flags);
        if(rcode != 0){
            Cname = null;
            return;
        }
        //id+count+query+answer
        int jump_index = 4+8+jump_length+4+10;
        input.jump(jump_index);
        int length = input.readU16();
        byte[] tmp = new byte[length];
        System.arraycopy(recvbyte,jump_index+2,tmp,0,length);

        int lastpre = tmp[length-2];
        int last = tmp[length-1];
        if (lastpre == -64){
            input.jump(last);
            int len  = input.readU8();
            int clen = tmp.length-2+len;
            byte[] bcname = new  byte[clen+1];
            System.arraycopy(tmp,0,bcname,0,tmp.length-2);
            System.arraycopy(recvbyte,input.current()-1,bcname,tmp.length-2,len+1);
            String str = new String(bcname);
            addDotsFromCname(str);
        }

    }
    public void addDotsFromCname(String str){
        String cname= "";
        char[]  strarray = str.toCharArray();
        for(int i = 0;i < strarray.length;){
            int t = strarray[i++];
            for (int j = i;j < t+i;j++){
                cname += strarray[j];
            }
            i+=t;
            if(i != strarray.length){
                cname +=".";
            }
        }
        Cname = cname;
    }
    public byte[] buildMessage(String name,short query_type){
        Header head = new Header();
        head.setFlag(6);
        head.setFlag(7);
        head.setCount((short) 0, (short)1);
        head.setCount((short)1,(short)0);
        head.setCount((short)2,(short)0);
        head.setCount((short)3,(short)0);
        QUESTION ques = new QUESTION(query_type,(short)1);
        String str = removeDotsFromName(name);
        byte[]  dname = str.getBytes();
        byte[] h = head.toByteArray();
        byte[] qinfo = ques.toByteArray();
        byte[] info =  byteMergerAll(h,dname,qinfo);
        return info;

    }
    private static byte[] byteMergerAll(byte[]... values) {
        int length_byte = 0;
        for (int i = 0; i < values.length; i++) {
            length_byte += values[i].length;
        }
        byte[] all_byte = new byte[length_byte];
        int countLength = 0;
        for (int i = 0; i < values.length; i++) {
            byte[] b = values[i];
            System.arraycopy(b, 0, all_byte, countLength, b.length);
            countLength += b.length;
        }
        return all_byte;
    }
    public String removeDotsFromName(String host){
        int lock = 0,i;
        int length = host.length()+2;
        char[] tmp = new char[length];
        host +=".";
        int j = 0;
        char [] stringArr = host.toCharArray();
        for(i =0 ;i <stringArr.length;i++){
            if(stringArr[i] == '.'){
                int t =  i-lock;
                tmp[j++] = (char)t;
                for(;lock < i;lock++){
                    tmp[j++] =stringArr[lock];
                }
                lock++;
            }
        }
        tmp[j] = (char)0;
        return String.valueOf(tmp);
    }
    public int
    getRcode(int flags) {
        return flags & 0xF;
    }

}
