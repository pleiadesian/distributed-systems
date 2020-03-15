## Lab2-report



##### Part1: parameter deduction process 

---

* 第1种参数
  * 四个流的WRED使用相同的参数，由srTCM控制带宽比。
  * srTCM参数：flow0的`CIR`设置为0.16GB/s，即lab所要求的1.28Gbps；`CBS`和`EBS`设置为1200000，使得flow0的包全部被标为绿色。四个flow的`CIR`、`CBS`、`EBS`以2为比例递减，使得标为绿色的包的数量比例为8:4:2:1。
  * WRED参数：`maxp_inv`和`wq_log2`采用dpdk文档的默认值。将绿色所对应的阈值设置的尽可能大，使得标为绿色的包全部不被丢弃。将黄色和红色的阈值设置为最小值，使得标为黄色和红色的包被全部丢弃。
  * 数量比例为8:4:2:1的绿色的包全部通过，红色和黄色的包全部丢弃，从而四个flow的带宽比达到8:4:2:1。

```c
struct rte_meter_srtcm_params app_srtcm_params[] = {
    {.cir = 1000000000 * 0.16, .cbs = 1200000, .ebs = 1200000},
    {.cir = 1000000000 * 0.08, .cbs = 600000, .ebs = 600000},
    {.cir = 1000000000 * 0.04, .cbs = 300000, .ebs = 300000},
    {.cir = 1000000000 * 0.02, .cbs = 150000, .ebs = 150000},
};

struct rte_red_config app_red_cfg[][e_RTE_METER_COLORS] = {
    /* Traffic Class 0 Colors Green / Yellow / Red */
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022 << 20, .max_th = 1023 << 20, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
    }
};
```

* 第二种参数
  * 四个流的srTCM使用相同的参数，由WRED控制带宽比。
  * srTCM参数：flow0的`CIR`设置为0.16GB/s，即lab所要求的1.28Gbps。`CBS`设置为40000，`ebs`设置为260000，使得标为绿色、黄色、红色的包的数量比例为1:1:2。
  * WRED参数：`maxp_inv`和`wq_log2`采用dpdk文档的默认值。flow0的三个颜色的阈值设置的尽可能大，使得三种颜色的包都能通过；flow1设置阈值使得只有绿色和黄色被通过，红色全部丢包；flow2设置阈值使得只有绿色被通过，黄色和红色全部丢包；flow3设置阈值使得黄色和红色全部丢包，绿色只有一半通过。从而四个flow的带宽比达到8:4:2:1。

```c
struct rte_meter_srtcm_params app_srtcm_params[] = {
    {.cir = 1000000000 * 0.16, .cbs = 40000, .ebs = 260000},
};

struct rte_red_config app_red_cfg[][e_RTE_METER_COLORS] = {
    /* Traffic Class 0 Colors Green / Yellow / Red */
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022 << 20, .max_th = 1023 << 20, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 1022 << 20, .max_th = 1023 << 20, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 1022 << 20, .max_th = 1023 << 20, .maxp_inv = 10, .wq_log2 = 9},
    },
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022 << 20, .max_th = 1023 << 20, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 1022 << 20, .max_th = 1023 << 20, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
    },
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022 << 20, .max_th = 1023 << 20, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
    },
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022 << 9, .max_th = 1023 << 9, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
    },
};
```



##### Part1: DPDK APIs 

---

* `rte_meter_srtcm_config`：传入预先设置的参数，使其配置`rte_meter_srtcm`结构体用于维护流的信息。
* `rte_meter_srtcm_color_blind_check`：传入`rte_meter_srtcm`、当前时间戳、包的长度，使用srTCM算法完成metering，返回包所指定的颜色。
* `rte_red_rt_data_init`：初始化`rte_red`结构体，用于维护队列信息。
* `rte_red_mark_queue_empty`：当检测到1个时间周期完成后，认为未发生拥堵，清空队列并重置`rte_red`中所维护的burst时间。
* `rte_red_enqueue`：传入队列所对应的配置信息、`rte_red`结构体、队列长度、当前时间戳，使用WRED算法，决定是否应该丢包。
* `RTE_DIM`：返回数组长度。

