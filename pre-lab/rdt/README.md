## Lab1文档

* 学号：517021910653
* 姓名：王祖来
* 邮箱：wzl574402791@sjtu.edu.cn



##### 设计策略

---

使用Go-Back-N协议。

* 计时器设计
  * 维护每一个已发送且未收到ack的包的发送时间，实现类似于virtual timer的效果。
  * 实际timer的超时时间设置为0.01秒。超时时检查sender window中第一个包是否超时。若超时，则重发该包开始的所有未收到ack的包。每一次实际timer超时只需要检查第一个包的发送时间，开销不大。
  * 包的超时时间设置为0.3秒，可以得到相对较高的吞吐量和较低的延迟。并达到loss-free的要求。
* window设计
  * sender：window大小为10。upper layer发送新的message时，将message进行分片，按序复制入window中。若window已满，则停止复制。发包时，从window中第一个未收到ack的包开始顺序发送包给lower layer。收到ack时，将小于ack number的所有包从window中删除。
  * receiver：window大小为10。由于乱序，可能收到的包的seq number大于等待下一个包的seq number，且seq number的差值小于window size，则可以预先将该包存于window中对应的位置。当等待的包到达时，顺序遍历window，将所有预先收到的包都进行解析，组合成message，发送给upper layer。
  * 过于小的window size会导致Go-Back-N退化为stop-and-wait，增加延迟。过于大的window size会导致超时重发的包过多。经过尝试将window size设置为10，可以得到较高的吞吐量和较低的延迟。并达到in-order的要求。
* Data buffer设计
  * 若sender的window被填满，则无法再接收upper layer的message。本lab无法使用block upper layer的方式。因此在sender端维护一个message buffer，当upper layer传入message时，先将message填入buffer中对应的位置，在window有空闲或者window缩小时，将message进行fragment并移入window。
  * window size定为15000，可以基本避免upper layer传入的message过多导致buffer填满的情况。
* error detection设计
  * 在发包时，计算checksum并填入包头中。收包时检查checksum，若和本地计算出的checksum不匹配，则丢弃该包。每一次sender发包、超时发包、receiver发ack包，都需要重新计算checksum，防止checksum被破坏的情况。可以基本达到error-free的要求。
  * 使用internet checksum如下

```c++
static short checksum(packet *pkt)
{
    unsigned short *buf = (unsigned short *)pkt->data;
    unsigned long sum = 0;
    buf++;  /* skip checksum part */
    for (int i = 2; i < RDT_PKTSIZE; i += sizeof(unsigned short)) 
        sum += *buf++;
    while (sum >> 16) 
        sum = (sum >> 16) + (sum & 0xffff);
    return ~sum;
}
```

* packet format设计
  * sender：7 byte的header。包括2 byte的checksum，1 byte的包信息，4 byte的sequence number。1 byte的包信息中，包含1 bit的位用于表示该包是否是一个message的最后一个包（End of Message），7 bit的位用于表示payload的大小。接收receiver的ack number也使用4 byte的sequence number的位置。
  * receiver：同上。发送ack number时也使用4 byte的sequence number的位置。

```c++
               |<-        1 byte        ->|
               |<-      packet info     ->|
|<-  2 byte  ->|<-1 bit->|<-    7 bit   ->|<-  4 byte  ->|<-       the rest      ->|
|<- checksum ->|<- EOM ->|<-payload size->|<-  seq no. ->|<-       payload       ->|
```



##### 实现策略

---

* 以类似于ring buffer的方式实现window和message buffer。维护ring buffer头部的sequence number、尾部的sequence number和已填入ring buffer的大小，即可以实现ring buffer。
* 维护变量`bool sending`，控制计时器的开始和停止。计时器在sender window和message buffer为空时，暂时停止，防止无效的超时检查带来开销。在运行`rdt_sim`时，sender空闲时停止计时器也可以避免超时事件使得主循环无法退出的问题。
* checksum存在局限性，即checksum和包的内容同时被破坏而checksum仍然匹配，或者包内容的破坏无法被checksum匹配所检测到的两种小概率事件会导致checksum失败。因此在sender和receiver处增加一层error detection。receiver检查到收到的seq number大于等待的seq number，且差值大于window buffer时，丢弃该包，从而使得sender重发此包。sender检查到收到的ack number大于本地维护的最大seq number时，丢弃该包，从而可以超时重发此包。
* 由于乱序，sender端收到的ack number大于期待的ack number时，根据协议，可以认为小于该ack number的所有包都已经被receiver接收；receiver端收到的seq number小于期待的seq number时，根据协议，可以认为sender端没有收到该包的ack，而本地已经收到该包并维护在buffer中，因此需要重发该包的ack。



##### 测试

---

结果如下

```bash
$ ./rdt_sim 1000 0.1 100 0.15 0.15 0.15 0                          
## Reliable data transfer simulation with:
        simulation time is 1000.000 seconds
        average message arrival interval is 0.100 seconds
        average message size is 100 bytes
        average out-of-order delivery rate is 15.00%
        average loss rate is 15.00%
        average corrupt rate is 15.00%
        tracing level is 0
Please review these inputs and press <enter> to proceed.

At 0.00s: sender initializing ...
At 0.00s: receiver initializing ...
At 1001.73s: sender finalizing ...
At 1001.73s: receiver finalizing ...

## Simulation completed at time 1001.73s with
        975394 characters sent
        975394 characters delivered
        37069 packets passed between the sender and the receiver
## Congratulations! This session is error-free, loss-free, and in order.
```



```bash
$ ./rdt_sim 1000 0.1 100 0.3 0.3 0.3 0   
## Reliable data transfer simulation with:
        simulation time is 1000.000 seconds
        average message arrival interval is 0.100 seconds
        average message size is 100 bytes
        average out-of-order delivery rate is 30.00%
        average loss rate is 30.00%
        average corrupt rate is 30.00%
        tracing level is 0
Please review these inputs and press <enter> to proceed.

At 0.00s: sender initializing ...
At 0.00s: receiver initializing ...
At 1839.77s: sender finalizing ...
At 1839.77s: receiver finalizing ...

## Simulation completed at time 1839.77s with
        998629 characters sent
        998629 characters delivered
        47481 packets passed between the sender and the receiver
## Congratulations! This session is error-free, loss-free, and in order.
```

