## Lab2文档



##### Question

---

Q1: What’s the purpose of using hugepage?

网络数据包处理中，CPU对内存访问频繁。使用内存大页能有效降低TLB miss，从而降低访存开销。



Q2: Take examples/helloworld as an example, describe the execution flow of DPDK programs?

main函数中，调用`rte_eal_init`，启动基础运行环境。`RTE_LCORE_FOREACH_SLAVE(lcore_id)`遍历所有逻辑核，获取其`lcore_id`。对于每一个从逻辑核，调用`rte_eal_remote_launch`启动指定线程。调用`lcore_hello(NULL)`启动主逻辑核的线程。调用`rte_eal_mp_wait_lcore`等待所有逻辑核运行线程结束。每一个线程调用`rte_lcore_id`获取当前逻辑核id，并打印出指定字符串。



Q3: Read the codes of examples/skeleton, describe DPDK APIs related to sending and

receiving packets.

* `port_init`：初始化端口配置。

* `rte_eth_dev_configure`：对指定端口设置收发队列数目，并可以对端口功能进行配置。

* `rte_eth_rx_queue_setup`/`rte_eth_tx_queue_setup`：初始化队列，指定内存、描述符数量、报

  文缓冲区，并且对队列进行配置。

* `rte_eth_rx_burst`/`rte_eth_tx_burst`：用于收发包，四个参数分别是端口，队列，报文缓冲区以及收发包数。
* `rte_eth_dev_start`：启动端口。
* `rte_eth_promiscuous_enable`：开启混杂模式。



Q4: Describe the data structure of ‘rte_mbuf’.

* `rte_mbuf`的结构报头包含包处理所需的所有数据，大小为2个cache line。对于巨型帧，`rte_mbuf`包含指向下一个`rte_mbuf`结构体的指针来形成链表结构。
* head room用来存储和系统中其他实体交互的信息，如控制信息、帧内容、事件等。的起始地址保存在`rte_mbuf`的buff_addr中，长度由`RTE_PKTMBUF_HEADROO`定义。
* 网络数据帧内容际长度可通过调用`rte_pktmbuf_pktlen`或`rte_pktmbuf_datalen`获得。