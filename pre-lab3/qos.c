#include "rte_common.h"
#include "rte_mbuf.h"
#include "rte_meter.h"
#include "rte_red.h"

#include "qos.h"

// #define ANOTHER_WAY

#ifndef ANOTHER_WAY
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
#else
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
#endif

struct rte_meter_srtcm app_flows[APP_FLOWS_MAX];

struct red_queue {
    uint32_t size;
    struct rte_red red;
} app_queue[APP_FLOWS_MAX];

/**
 * srTCM
 */
int
qos_meter_init(void)
{
    /* to do */
    uint32_t i, j;
    int ret;

    for (i = 0, j = 0; i < APP_FLOWS_MAX; i++, j = (j + 1) % RTE_DIM(app_srtcm_params)) {
        ret = rte_meter_srtcm_config(&app_flows[i], &app_srtcm_params[j]);
        if (ret)
            return ret;
    }

    return 0;
}

enum qos_color
qos_meter_run(uint32_t flow_id, uint32_t pkt_len, uint64_t time)
{
    /* to do */
    return rte_meter_srtcm_color_blind_check(&app_flows[flow_id], time, pkt_len);
}


/**
 * WRED
 */

int
qos_dropper_init(void)
{
    /* to do */
    uint32_t i;
    int ret;

    for (i = 0; i < APP_FLOWS_MAX; i++) {
        ret = rte_red_rt_data_init(&app_queue[i].red);
        if (ret) 
            return ret;
    }

    return 0;
}

int
qos_dropper_run(uint32_t flow_id, enum qos_color color, uint64_t time)
{
    /* to do */
    int ret;
    struct red_queue *q = &app_queue[flow_id];

    if (time != q->red.q_time) {
        rte_red_mark_queue_empty(&q->red, time);
        q->size = 0;
    }
    

    if ((ret = rte_red_enqueue(&app_red_cfg[flow_id % RTE_DIM(app_red_cfg)][color], &q->red, q->size, time)) == 0)
        q->size++;
    
    return ret;
}